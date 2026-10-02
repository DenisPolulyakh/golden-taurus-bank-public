// Форматирование и цветовые правила, общие для страницы карт и её модалок.
// Суммы и даты переехали в @/lib/format — здесь остаётся только то,
// что относится именно к картам.

import { pluralDays } from '@/lib/format'
export { formatAmount, formatSigned, formatDate, formatDateTime } from '@/lib/format'

/**
 * Цвет счётчика до ближайшего платежа: больше 10 дней — зелёный, 6–10 — оранжевый,
 * 5 и меньше — красный. Ровно 10 в ТЗ попадало в зазор между «больше 10» и
 * «больше 5», отнесено к оранжевому.
 *
 * Возвращает классы Tailwind: прежние grace-* жили в удалённом CreditCards.css.
 */
export function graceClass(daysLeft) {
    if (daysLeft === null || daysLeft === undefined) return ''
    if (daysLeft < 0) return 'text-destructive font-semibold'
    if (daysLeft <= 5) return 'text-destructive'
    if (daysLeft <= 10) return 'text-warning'
    return 'text-success'
}

/**
 * Доля использованного лимита в процентах. Лимит 0 (или карт нет) — делить не на
 * что, возвращаем null: это «неизвестно», а не «0 % использовано».
 */
export function limitUsage(totalDebt, totalLimit) {
    const limit = Number(totalLimit || 0)
    if (!limit) return null
    return (Number(totalDebt || 0) / limit) * 100
}

/**
 * Цвет процента: до 30 — зелёный, до 50 — жёлтый, до 80 — оранжевый, от 80 —
 * красный. Границы уходят в более тревожный цвет — так же, как у graceClass.
 * Жёлтой ступени своего токена нет, поэтому она берёт amber из палитры Tailwind.
 */
export function usageClass(percent) {
    if (percent === null || percent === undefined) return ''
    if (percent >= 80) return 'text-destructive'
    if (percent >= 50) return 'text-warning'
    if (percent >= 30) return 'text-amber-500'
    return 'text-success'
}

export function usageText(percent) {
    if (percent === null || percent === undefined) return '—'
    // Дробные доли процента здесь ничего не решают, читается хуже
    return `${Math.round(percent)} %`
}

export function graceText(daysLeft) {
    if (daysLeft === null || daysLeft === undefined) return '—'
    if (daysLeft < 0) return `Просрочено на ${pluralDays(daysLeft)}`
    if (daysLeft === 0) return 'Сегодня последний день'
    return pluralDays(daysLeft)
}

export function graceShortText(daysLeft) {
    if (daysLeft === null || daysLeft === undefined) return ''
    if (daysLeft < 0) return 'просрочено'
    if (daysLeft === 0) return 'сегодня'
    return pluralDays(daysLeft)
}
