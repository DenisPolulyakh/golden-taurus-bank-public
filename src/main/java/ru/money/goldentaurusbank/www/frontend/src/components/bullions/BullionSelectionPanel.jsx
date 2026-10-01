import { useCallback, useEffect, useRef, useState } from 'react'
import { GripVertical, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { formatAmount, toCents } from '@/lib/format'
import { cn } from '@/lib/utils'

const ROUNDING_OPTIONS = [
    { value: '1', label: 'до 1 ₽' },
    { value: '10', label: 'до 10 ₽' },
    { value: '100', label: 'до 100 ₽' },
    { value: '1000', label: 'до 1 000 ₽' },
    { value: '10000', label: 'до 10 000 ₽' },
    { value: '100000', label: 'до 100 000 ₽' },
    { value: '1000000', label: 'до 1 млн ₽' },
]

const DEFAULT_ROUNDING = '1000'
const ROUNDING_KEY = 'bullion-selection:rounding'
const POSITION_KEY = 'bullion-selection:position'
const DESKTOP_QUERY = '(min-width: 640px)'
const EDGE_GAP = 8

function readRounding() {
    try {
        const value = localStorage.getItem(ROUNDING_KEY)
        return ROUNDING_OPTIONS.some((option) => option.value === value) ? value : DEFAULT_ROUNDING
    } catch {
        return DEFAULT_ROUNDING
    }
}

function readPosition() {
    try {
        const parsed = JSON.parse(localStorage.getItem(POSITION_KEY))
        return Number.isFinite(parsed?.x) && Number.isFinite(parsed?.y)
            ? { x: parsed.x, y: parsed.y }
            : null
    } catch {
        return null
    }
}

function save(key, value) {
    try {
        localStorage.setItem(key, value)
    } catch (error) {
        console.error('Не удалось сохранить настройку окна выделения:', error)
    }
}

function roundingShortfall(totalCents, rubles) {
    const stepCents = Number(rubles) * 100
    const targetCents = Math.ceil(totalCents / stepCents) * stepCents
    return { targetCents, shortfallCents: targetCents - totalCents }
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

function BullionSelectionPanel({ items, onRemove, onClear }) {
    const panelRef = useRef(null)
    const dragRef = useRef(null)
    const isDesktop = useIsDesktop()
    const [rounding, setRounding] = useState(readRounding)
    const [position, setPosition] = useState(readPosition)

    const hasItems = items.length > 0

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
        if (!isDesktop || !hasItems) return
        const observer = new ResizeObserver(keepInViewport)
        observer.observe(panelRef.current)
        window.addEventListener('resize', keepInViewport)
        return () => {
            observer.disconnect()
            window.removeEventListener('resize', keepInViewport)
        }
    }, [isDesktop, hasItems, keepInViewport])

    const handleRoundingChange = (value) => {
        setRounding(value)
        save(ROUNDING_KEY, value)
    }

    const pointerPosition = (event) => clampToViewport({
        x: event.clientX - dragRef.current.offsetX,
        y: event.clientY - dragRef.current.offsetY,
    }, panelRef.current)

    const handlePointerDown = (event) => {
        if (!isDesktop || event.button !== 0 || event.target.closest('button')) return
        const rect = panelRef.current.getBoundingClientRect()
        dragRef.current = { offsetX: event.clientX - rect.left, offsetY: event.clientY - rect.top }
        event.currentTarget.setPointerCapture(event.pointerId)
        event.preventDefault()
    }

    const handlePointerMove = (event) => {
        if (!dragRef.current) return
        setPosition(pointerPosition(event))
    }

    const handlePointerUp = (event) => {
        if (!dragRef.current) return
        const next = pointerPosition(event)
        dragRef.current = null
        setPosition(next)
        save(POSITION_KEY, JSON.stringify(next))
    }

    const handlePointerCancel = () => {
        dragRef.current = null
    }

    if (!hasItems) return null

    const totalCents = items.reduce((sum, item) => sum + toCents(item.amount), 0)
    const { targetCents, shortfallCents } = roundingShortfall(totalCents, rounding)
    const desktopStyle = isDesktop && position
        ? { left: position.x, top: position.y, right: 'auto', bottom: 'auto' }
        : undefined

    return (
        <aside
            ref={panelRef}
            style={desktopStyle}
            aria-label="Выделенные слитки"
            className="sticky bottom-2 z-40 flex max-h-[50vh] flex-col rounded-xl border bg-card text-card-foreground shadow-lg sm:fixed sm:right-6 sm:bottom-6 sm:max-h-[80vh] sm:w-80"
        >
            <div
                className="flex items-center gap-2 border-b px-4 py-2 select-none sm:cursor-grab sm:touch-none sm:active:cursor-grabbing"
                onPointerDown={handlePointerDown}
                onPointerMove={handlePointerMove}
                onPointerUp={handlePointerUp}
                onPointerCancel={handlePointerCancel}
            >
                <GripVertical className="hidden size-4 text-muted-foreground sm:block" />
                <span className="flex-1 text-sm font-medium">Выделено: {items.length}</span>
                <Button
                    variant="ghost"
                    size="icon-sm"
                    onClick={onClear}
                    aria-label="Снять выделение"
                    title="Снять выделение (Esc)"
                >
                    <X />
                </Button>
            </div>

            <div className="flex flex-col gap-3 px-4 py-3">
                <div className="flex items-center justify-between gap-3">
                    <span className="text-sm text-muted-foreground">Округлять</span>
                    <Select value={rounding} onValueChange={handleRoundingChange}>
                        <SelectTrigger size="sm" className="w-36">
                            <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                            {ROUNDING_OPTIONS.map((option) => (
                                <SelectItem key={option.value} value={option.value}>
                                    {option.label}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>

                <div className="flex flex-col gap-0.5">
                    <span className="text-sm text-muted-foreground">
                        Не хватает до {formatAmount(targetCents / 100)} ₽
                    </span>
                    <span className={cn('text-lg font-semibold tabular-nums', shortfallCents === 0 && 'text-success')}>
                        {formatAmount(shortfallCents / 100)} ₽
                    </span>
                </div>

                <div className="flex flex-col gap-0.5">
                    <span className="text-sm text-muted-foreground">Сумма выделенных</span>
                    <span className="brand-text text-3xl leading-tight font-semibold tabular-nums">
                        {formatAmount(totalCents / 100)} ₽
                    </span>
                </div>
            </div>

            <ul className="flex min-h-0 flex-col overflow-y-auto border-t py-1">
                {items.map((item) => (
                    <li key={item.id} className="flex items-center gap-2 px-4 py-1.5 text-sm">
                        <span className="min-w-0 flex-1 truncate" title={item.title}>
                            {item.title}
                        </span>
                        <span className="shrink-0 tabular-nums">{formatAmount(item.amount)} ₽</span>
                        <Button
                            variant="ghost"
                            size="icon-xs"
                            onClick={() => onRemove(item.id)}
                            aria-label={`Убрать «${item.title}» из выделения`}
                        >
                            <X />
                        </Button>
                    </li>
                ))}
            </ul>
        </aside>
    )
}

export default BullionSelectionPanel
