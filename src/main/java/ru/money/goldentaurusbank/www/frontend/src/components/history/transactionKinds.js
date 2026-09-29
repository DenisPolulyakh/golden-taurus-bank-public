// Вид операции выводится бэкендом из того, какие ноги заполнены.
export const KIND_LABELS = {
    DEPOSIT: 'Пополнение',
    WITHDRAWAL: 'Списание',
    TRANSFER: 'Перевод',
    OPENING_BALANCE: 'Начальный остаток',
}

// Перевод и начальный остаток накопления не двигают — красим их нейтрально.
export const KIND_AMOUNT_CLASS = {
    DEPOSIT: 'text-success',
    WITHDRAWAL: 'text-destructive',
    TRANSFER: 'text-primary',
    OPENING_BALANCE: 'text-muted-foreground',
}

export const KIND_BADGE_VARIANT = {
    DEPOSIT: 'success',
    WITHDRAWAL: 'danger',
    TRANSFER: 'info',
    OPENING_BALANCE: 'outline',
}

export const getKindLabel = (kind) => KIND_LABELS[kind] || kind || '—'
