import { useCallback, useEffect, useMemo, useState } from 'react'

const IGNORED_TARGETS =
    'a, button, input, textarea, select, label, [role="button"], [role="combobox"], [data-slot="button-group"]'

function isSelectionClick(event) {
    if (event.target.closest(IGNORED_TARGETS)) return false
    if (window.getSelection()?.toString()) return false
    return true
}

function isTypingTarget(target) {
    return target instanceof HTMLElement &&
        (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))
}

export function useBullionSelection(items, getId) {
    const [selectedIds, setSelectedIds] = useState([])

    const toggle = useCallback((id) => {
        setSelectedIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
    }, [])

    const remove = useCallback((id) => {
        setSelectedIds((prev) => prev.filter((x) => x !== id))
    }, [])

    const clear = useCallback(() => setSelectedIds([]), [])

    const onCardClick = useCallback((event, id) => {
        if (isSelectionClick(event)) toggle(id)
    }, [toggle])

    const selectedSet = useMemo(() => new Set(selectedIds), [selectedIds])

    const isSelected = useCallback((id) => selectedSet.has(id), [selectedSet])

    const selectedItems = useMemo(() => {
        const byId = new Map(items.map((item) => [getId(item), item]))
        return selectedIds.map((id) => byId.get(id)).filter(Boolean)
    }, [items, selectedIds, getId])

    const hasSelection = selectedItems.length > 0

    useEffect(() => {
        if (!hasSelection) return
        const onKeyDown = (event) => {
            if (event.key !== 'Escape' || event.defaultPrevented || isTypingTarget(event.target)) return
            clear()
        }
        document.addEventListener('keydown', onKeyDown)
        return () => document.removeEventListener('keydown', onKeyDown)
    }, [hasSelection, clear])

    return { selectedItems, isSelected, onCardClick, remove, clear }
}
