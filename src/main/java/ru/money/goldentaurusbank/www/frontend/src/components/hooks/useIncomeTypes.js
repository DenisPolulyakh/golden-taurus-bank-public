import { useEffect, useState } from 'react'
import api from '@/api/axios'

/**
 * Справочник типов дохода для выпадающего списка при пополнении слитка.
 * Ручка недоступна или ещё не ответила — форма просто не показывает список,
 * поле пополнения при этом работает как раньше (без классификации).
 */
export function useIncomeTypes() {
    const [incomeTypes, setIncomeTypes] = useState([])

    useEffect(() => {
        api.get('/transactions/income-types', { _skipErrorToast: true })
            .then(({ data }) => setIncomeTypes(data ?? []))
            .catch(() => setIncomeTypes([]))
    }, [])

    return incomeTypes
}
