import { useCallback, useState } from 'react'
import { toCents } from '@/lib/format'

function parseCents(value) {
    const num = parseFloat(String(value).replace(/\s/g, '').replace(',', '.'))
    return Number.isFinite(num) && num > 0 ? Math.round(num * 100) : 0
}

export function useBullionMove(selectedItems) {
    const [amounts, setAmounts] = useState({})
    const [targetVaultId, setTargetVaultId] = useState('')
    const [dateOperation, setDateOperation] = useState('')
    const [loading, setLoading] = useState(false)
    const [seenItems, setSeenItems] = useState(selectedItems)

    if (seenItems !== selectedItems) {
        setSeenItems(selectedItems)
        const ids = new Set(selectedItems.map((item) => String(item.id)))
        const keys = Object.keys(amounts)
        if (keys.some((key) => !ids.has(key))) {
            setAmounts(Object.fromEntries(keys.filter((key) => ids.has(key)).map((key) => [key, amounts[key]])))
        }
    }

    const amountFor = useCallback((item) => (
        item.id in amounts ? amounts[item.id] : String(item.amount)
    ), [amounts])

    const centsFor = useCallback((item) => {
        const maxCents = toCents(item.amount)
        if (!(item.id in amounts)) return maxCents
        return Math.min(parseCents(amounts[item.id]), maxCents)
    }, [amounts])

    const setAmount = useCallback((id, value) => {
        setAmounts((prev) => ({ ...prev, [id]: value }))
    }, [])

    const normalizeAmount = useCallback((id) => {
        const item = selectedItems.find((candidate) => candidate.id === id)
        if (!item) return
        const cents = centsFor(item)
        setAmounts((prev) => ({ ...prev, [id]: (cents / 100).toFixed(2) }))
    }, [selectedItems, centsFor])

    const reset = useCallback(() => {
        setAmounts({})
        setTargetVaultId('')
        setDateOperation('')
    }, [])

    return {
        amountFor,
        centsFor,
        setAmount,
        normalizeAmount,
        targetVaultId,
        setTargetVaultId,
        dateOperation,
        setDateOperation,
        loading,
        setLoading,
        reset,
    }
}
