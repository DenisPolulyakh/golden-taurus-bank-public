import { useEffect, useState } from 'react'
import api from '@/api/axios'

/**
 * Id бюджетного слитка. По нему формы операций решают, показывать ли галочку
 * «Трата бюджета»: у остальных слитков корзина ни на что не влияет, и галочка
 * только мозолила бы глаза.
 *
 * Слиток не выбран или ручка недоступна — возвращаем null, и формы работают
 * как раньше: бэкенд считает операцию тратой по умолчанию.
 */
export function useBudgetBullionId() {
    const [budgetBullionId, setBudgetBullionId] = useState(null)

    useEffect(() => {
        api.get('/budget/settings', { _skipErrorToast: true })
            .then(({ data }) => setBudgetBullionId(data?.budgetBullion?.id ?? null))
            .catch(() => setBudgetBullionId(null))
    }, [])

    return budgetBullionId
}
