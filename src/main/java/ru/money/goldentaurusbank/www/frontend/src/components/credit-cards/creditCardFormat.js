// Форматирование, общее для страницы карт и её модалок.

export function formatAmount(amount) {
    if (!amount && amount !== 0) return '0';
    const num = typeof amount === 'string' ? parseFloat(amount) : amount;
    if (isNaN(num)) return '0';
    const parts = Math.abs(num).toFixed(2).split('.');
    const formattedInteger = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
    const decimalPart = parts[1];
    const sign = num < 0 ? '-' : '';
    if (decimalPart && decimalPart !== '00') {
        const trimmedDecimal = decimalPart.replace(/0+$/, '');
        if (trimmedDecimal) {
            return `${sign}${formattedInteger}.${trimmedDecimal}`;
        }
    }
    return `${sign}${formattedInteger}`;
}

/** Дисбаланс и прочие суммы со знаком: плюс рисуем явно, минус даёт formatAmount. */
export function formatSigned(amount) {
    const num = typeof amount === 'string' ? parseFloat(amount) : (amount || 0);
    return num > 0 ? `+${formatAmount(num)}` : formatAmount(num);
}

export function formatDate(value) {
    if (!value) return '';
    return new Date(value).toLocaleDateString('ru-RU', {
        year: 'numeric',
        month: 'long',
        day: 'numeric'
    });
}

export function formatDateTime(value) {
    if (!value) return '';
    return new Date(value).toLocaleString('ru-RU', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    });
}

/** 1 день / 2 дня / 5 дней — иначе счётчик читается как машинный. */
export function pluralDays(days) {
    const abs = Math.abs(days);
    const lastTwo = abs % 100;
    const last = abs % 10;
    if (lastTwo >= 11 && lastTwo <= 14) return `${abs} дней`;
    if (last === 1) return `${abs} день`;
    if (last >= 2 && last <= 4) return `${abs} дня`;
    return `${abs} дней`;
}

/**
 * Цвет счётчика льготного периода: больше 10 дней — зелёный, 6–10 — оранжевый,
 * 5 и меньше — красный. Ровно 10 в ТЗ попадало в зазор между «больше 10» и
 * «больше 5», отнесено к оранжевому.
 */
export function graceClass(daysLeft) {
    if (daysLeft === null || daysLeft === undefined) return '';
    if (daysLeft < 0) return 'grace-overdue';
    if (daysLeft <= 5) return 'grace-danger';
    if (daysLeft <= 10) return 'grace-warning';
    return 'grace-ok';
}

/**
 * Доля использованного лимита в процентах. Лимит 0 (или карт нет) — делить не на
 * что, возвращаем null: это «неизвестно», а не «0 % использовано».
 */
export function limitUsage(totalDebt, totalLimit) {
    const limit = Number(totalLimit || 0);
    if (!limit) return null;
    return (Number(totalDebt || 0) / limit) * 100;
}

/**
 * Цвет процента: до 30 — зелёный, до 50 — жёлтый, до 80 — оранжевый, от 80 —
 * красный. Границы уходят в более тревожный цвет — так же, как у graceClass.
 */
export function usageClass(percent) {
    if (percent === null || percent === undefined) return '';
    if (percent >= 80) return 'usage-danger';
    if (percent >= 50) return 'usage-warning';
    if (percent >= 30) return 'usage-notice';
    return 'usage-ok';
}

export function usageText(percent) {
    if (percent === null || percent === undefined) return '—';
    // Дробные доли процента здесь ничего не решают, читается хуже
    return `${Math.round(percent)} %`;
}

export function graceText(daysLeft) {
    if (daysLeft === null || daysLeft === undefined) return '—';
    if (daysLeft < 0) return `Просрочено на ${pluralDays(daysLeft)}`;
    if (daysLeft === 0) return 'Сегодня последний день';
    return pluralDays(daysLeft);
}
