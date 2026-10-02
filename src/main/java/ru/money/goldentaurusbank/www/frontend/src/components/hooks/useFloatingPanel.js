import { useCallback, useEffect, useRef, useState } from 'react'

const DESKTOP_QUERY = '(min-width: 640px)'
const EDGE_GAP = 8

function storeValue(key, value) {
    try {
        localStorage.setItem(key, value)
    } catch (error) {
        console.error('Не удалось сохранить настройку окна выделения:', error)
    }
}

function readOption(key, allowed, fallback) {
    try {
        const value = localStorage.getItem(key)
        return allowed.includes(value) ? value : fallback
    } catch {
        return fallback
    }
}

function readPosition(key) {
    try {
        const parsed = JSON.parse(localStorage.getItem(key))
        return Number.isFinite(parsed?.x) && Number.isFinite(parsed?.y)
            ? { x: parsed.x, y: parsed.y }
            : null
    } catch {
        return null
    }
}

function clampToViewport(position, element) {
    const maxX = Math.max(window.innerWidth - element.offsetWidth - EDGE_GAP, EDGE_GAP)
    const maxY = Math.max(window.innerHeight - element.offsetHeight - EDGE_GAP, EDGE_GAP)
    return {
        x: Math.min(Math.max(position.x, EDGE_GAP), maxX),
        y: Math.min(Math.max(position.y, EDGE_GAP), maxY),
    }
}

function useIsDesktop() {
    const [isDesktop, setIsDesktop] = useState(() => window.matchMedia(DESKTOP_QUERY).matches)

    useEffect(() => {
        const media = window.matchMedia(DESKTOP_QUERY)
        const onChange = (event) => setIsDesktop(event.matches)
        media.addEventListener('change', onChange)
        return () => media.removeEventListener('change', onChange)
    }, [])

    return isDesktop
}

export function useStoredOption(key, allowed, fallback) {
    const [value, setValue] = useState(() => readOption(key, allowed, fallback))

    const change = useCallback((next) => {
        setValue(next)
        storeValue(key, next)
    }, [key])

    return [value, change]
}

export function useFloatingPanel(positionKey, active) {
    const panelRef = useRef(null)
    const dragRef = useRef(null)
    const isDesktop = useIsDesktop()
    const [position, setPosition] = useState(() => readPosition(positionKey))

    const keepInViewport = useCallback(() => {
        const panel = panelRef.current
        if (!panel) return
        setPosition((prev) => {
            if (!prev) return prev
            const next = clampToViewport(prev, panel)
            return next.x === prev.x && next.y === prev.y ? prev : next
        })
    }, [])

    useEffect(() => {
        if (!isDesktop || !active) return
        const observer = new ResizeObserver(keepInViewport)
        observer.observe(panelRef.current)
        window.addEventListener('resize', keepInViewport)
        return () => {
            observer.disconnect()
            window.removeEventListener('resize', keepInViewport)
        }
    }, [isDesktop, active, keepInViewport])

    const pointerPosition = (event) => clampToViewport({
        x: event.clientX - dragRef.current.offsetX,
        y: event.clientY - dragRef.current.offsetY,
    }, panelRef.current)

    const onPointerDown = (event) => {
        if (!isDesktop || event.button !== 0 || event.target.closest('button')) return
        const rect = panelRef.current.getBoundingClientRect()
        dragRef.current = { offsetX: event.clientX - rect.left, offsetY: event.clientY - rect.top }
        event.currentTarget.setPointerCapture(event.pointerId)
        event.preventDefault()
    }

    const onPointerMove = (event) => {
        if (!dragRef.current) return
        setPosition(pointerPosition(event))
    }

    const onPointerUp = (event) => {
        if (!dragRef.current) return
        const next = pointerPosition(event)
        dragRef.current = null
        setPosition(next)
        storeValue(positionKey, JSON.stringify(next))
    }

    const onPointerCancel = () => {
        dragRef.current = null
    }

    const style = isDesktop && position
        ? { left: position.x, top: position.y, right: 'auto', bottom: 'auto' }
        : undefined

    return {
        panelRef,
        style,
        dragHandleProps: { onPointerDown, onPointerMove, onPointerUp, onPointerCancel },
    }
}
