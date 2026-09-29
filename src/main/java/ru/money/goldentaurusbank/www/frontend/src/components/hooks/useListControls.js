import { useCallback, useMemo, useState } from 'react'

function compareValues(a, b) {
    if (typeof a === 'string' || typeof b === 'string') {
        return String(a ?? '').localeCompare(String(b ?? ''), 'ru', { sensitivity: 'base' })
    }
    return Number(a ?? 0) - Number(b ?? 0)
}

export function useListControls(items, { matches, sortValues, initialSort, firstOrder = {} }) {
    const [searchTerm, setSearchTerm] = useState('')
    const [sort, setSort] = useState(initialSort)

    const toggleSort = useCallback((field) => {
        setSort((prev) => {
            if (prev.field === field) {
                return { field, order: prev.order === 'asc' ? 'desc' : 'asc' }
            }
            return { field, order: firstOrder[field] ?? 'asc' }
        })
    }, [firstOrder])

    const visibleItems = useMemo(() => {
        const query = searchTerm.trim().toLowerCase()
        const filtered = query ? items.filter((item) => matches(item, query)) : [...items]
        const getValue = sortValues[sort.field]
        const direction = sort.order === 'asc' ? 1 : -1

        return filtered.sort((a, b) => direction * compareValues(getValue(a), getValue(b)))
    }, [items, searchTerm, sort, matches, sortValues])

    return {
        searchTerm,
        setSearchTerm,
        sortField: sort.field,
        sortOrder: sort.order,
        toggleSort,
        visibleItems,
    }
}
