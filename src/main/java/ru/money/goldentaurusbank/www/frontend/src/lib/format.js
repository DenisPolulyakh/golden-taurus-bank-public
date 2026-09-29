// Форматирование сумм и дат, общее для всех экранов. Раньше одна и та же
// функция была скопирована в семь файлов с мелкими расхождениями.

/** Разряды пробелами, нулевые копейки не показываем: 1 234, 1 234.5, -700. */
export function formatAmount(amount) {
    if (!amount && amount !== 0) return '0'
    const num = typeof amount === 'string' ? parseFloat(amount) : amount
    if (isNaN(num)) return '0'

    const parts = Math.abs(num).toFixed(2).split('.')
    const integerPart = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ')
    const trimmedDecimal = parts[1].replace(/0+$/, '')
    const sign = num < 0 ? '-' : ''

    return trimmedDecimal ? `${sign}${integerPart}.${trimmedDecimal}` : `${sign}${integerPart}`
}

/** Суммы со знаком: плюс рисуем явно, минус даёт formatAmount. */
export function formatSigned(amount) {
    const num = typeof amount === 'string' ? parseFloat(amount) : (amount || 0)
    return num > 0 ? `+${formatAmount(num)}` : formatAmount(num)
}

/** Формат истории операций: 1 234,56 ₽ — копейки всегда видны. */
export function formatCurrency(amount) {
    if (!amount) return '0 ₽'
    return new Intl.NumberFormat('ru-RU', {
        style: 'currency',
        currency: 'RUB',
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
    }).format(amount)
}

/** Копейки целым числом: сравнивать суммы во float нельзя, 100.10 - 100.05 даст хвост. */
export function toCents(value) {
    const num = typeof value === 'string' ? parseFloat(value) : value
    return isNaN(num) ? 0 : Math.round(num * 100)
}

/** 5 августа 2026 */
export function formatDate(value) {
    if (!value) return ''
    return new Date(value).toLocaleDateString('ru-RU', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
    })
}

/** 05.08.2026 */
export function formatShortDate(value) {
    if (!value) return ''
    return new Date(value).toLocaleDateString('ru-RU')
}

/** 05.08.2026 14:30 */
export function formatDateTime(value) {
    if (!value) return ''
    return new Date(value).toLocaleString('ru-RU', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    })
}

/** 1 день / 2 дня / 5 дней — иначе счётчик читается как машинный. */
export function pluralDays(days) {
    const abs = Math.abs(days)
    const lastTwo = abs % 100
    const last = abs % 10
    if (lastTwo >= 11 && lastTwo <= 14) return `${abs} дней`
    if (last === 1) return `${abs} день`
    if (last >= 2 && last <= 4) return `${abs} дня`
    return `${abs} дней`
}
