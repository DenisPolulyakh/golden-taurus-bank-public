const STYLES = `
* { box-sizing: border-box; }
body { margin: 0; padding: 24px; font: 13px/1.45 -apple-system, Segoe UI, Roboto, Arial, sans-serif; color: #111; }
h1 { margin: 0 0 4px; font-size: 20px; }
.meta { margin-bottom: 16px; color: #555; font-size: 12px; }
.toolbar { margin-bottom: 16px; }
button { padding: 8px 16px; font: inherit; cursor: pointer; }
.grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.card { border: 1px solid #bbb; border-radius: 6px; padding: 10px 12px; page-break-inside: avoid; }
.card h2 { margin: 0 0 6px; font-size: 14px; display: flex; justify-content: space-between; gap: 8px; }
.row { display: flex; justify-content: space-between; gap: 8px; padding: 1px 0; }
.label { color: #666; }
.value { font-weight: 600; font-variant-numeric: tabular-nums; text-align: right; word-break: break-all; }
.warn { margin-top: 6px; padding: 4px 6px; border-radius: 4px; background: #fff3cd; font-size: 12px; }
.total { margin-top: 16px; padding-top: 8px; border-top: 2px solid #111; display: flex; justify-content: space-between; font-size: 14px; font-weight: 700; }
.orphans { margin-top: 16px; }
.orphans h3 { font-size: 13px; margin: 0 0 6px; }
@media print { .toolbar { display: none; } body { padding: 0; } }
`

function escapeHtml(value) {
    return String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
}

function formatAmount(value) {
    const number = Number(value || 0)
    return number.toLocaleString('ru-RU', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatDate(value) {
    if (!value) return '—'
    const [year, month, day] = String(value).split('-')
    return day && month && year ? `${day}.${month}.${year}` : String(value)
}

function formatPan(value) {
    return value ? String(value).replace(/(.{4})/g, '$1 ').trim() : '—'
}

function row(label, value) {
    return `<div class="row"><span class="label">${escapeHtml(label)}</span><span class="value">${escapeHtml(value)}</span></div>`
}

function cardBlock(card, entry) {
    const rows = [
        row('Номер карты', entry ? formatPan(entry.pan) : '—'),
        row('Счёт', entry?.account || '—'),
        row('БИК', entry?.bic || '—'),
        row('Получатель', entry?.receiver || '—'),
        row('Задолженность', `${formatAmount(card.debt)} ₽`),
        row('Лимит', `${formatAmount(card.limit)} ₽`),
        row('Ближайший платёж', formatDate(card.gracePeriodDate)),
    ]

    if (entry?.note) {
        rows.push(row('Заметка', entry.note))
    }

    const warnings = []
    if (!entry) {
        warnings.push('Реквизиты не заведены')
    } else if (entry.last4 && card.last4 && entry.last4 !== card.last4) {
        warnings.push('Последние 4 цифры не совпадают — проверьте вручную')
    }

    return `<div class="card">
        <h2><span>${escapeHtml(card.name)}</span><span>${escapeHtml(card.maskedNumber || '')}</span></h2>
        ${rows.join('')}
        ${warnings.map((text) => `<div class="warn">${escapeHtml(text)}</div>`).join('')}
    </div>`
}

function orphanBlock(entry) {
    return `<div class="card">
        <h2><span>Карта удалена или недоступна</span><span>•••• ${escapeHtml(entry.last4 || '')}</span></h2>
        ${row('Номер карты', formatPan(entry.pan))}
        ${row('Счёт', entry.account || '—')}
        ${row('БИК', entry.bic || '—')}
        ${row('Получатель', entry.receiver || '—')}
    </div>`
}

export function buildSheetHtml({ cards = [], entries = [], generatedAt }) {
    const byCardId = new Map(entries.map((entry) => [entry.cardId, entry]))
    const knownIds = new Set(cards.map((card) => card.id))

    const blocks = cards.map((card) => cardBlock(card, byCardId.get(card.id) || null))
    const orphans = entries.filter((entry) => !knownIds.has(entry.cardId))

    const totalDebt = cards.reduce((sum, card) => sum + Number(card.debt || 0), 0)
    const stamp = generatedAt ? new Date(generatedAt) : new Date()

    return `<!doctype html>
<html lang="ru">
<head>
<meta charset="utf-8">
<title>Реквизиты карт</title>
<style>${STYLES}</style>
</head>
<body>
<div class="toolbar"><button onclick="window.print()">Печать</button></div>
<h1>Реквизиты карт</h1>
<div class="meta">Данные на ${stamp.toLocaleString('ru-RU')} · карт: ${cards.length}</div>
<div class="grid">${blocks.join('')}</div>
${orphans.length ? `<div class="orphans"><h3>Реквизиты без карты в бухгалтерии</h3><div class="grid">${orphans.map(orphanBlock).join('')}</div></div>` : ''}
<div class="total"><span>Общая задолженность</span><span>${formatAmount(totalDebt)} ₽</span></div>
</body>
</html>`
}
