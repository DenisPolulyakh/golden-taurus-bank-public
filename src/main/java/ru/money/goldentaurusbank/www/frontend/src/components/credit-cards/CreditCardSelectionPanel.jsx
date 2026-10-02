import { Copy, GripVertical, X } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useFloatingPanel, useStoredOption } from '@/components/hooks/useFloatingPanel'
import { formatAmount, toCents } from '@/lib/format'
import { cn } from '@/lib/utils'
import { graceClass, graceShortText } from './creditCardFormat'

const ROUNDING_OPTIONS = [
    { value: '1', label: 'до 1 ₽' },
    { value: '10', label: 'до 10 ₽' },
    { value: '100', label: 'до 100 ₽' },
]

const ROUNDING_VALUES = ROUNDING_OPTIONS.map((option) => option.value)
const DEFAULT_ROUNDING = '100'
const ROUNDING_KEY = 'card-selection:rounding'
const POSITION_KEY = 'card-selection:position'

const rubles = (cents) => `${formatAmount(cents / 100)} ₽`

const centsToInput = (cents) => String(cents / 100)

async function copyAmount(cents) {
    try {
        await navigator.clipboard.writeText(centsToInput(cents))
        toast.success(`Скопировано: ${rubles(cents)}`)
    } catch {
        toast.error('Браузер не дал доступ к буферу обмена')
    }
}

function CreditCardSelectionPanel({ cards, onRemove, onClear, onRepay, onLocate }) {
    const hasCards = cards.length > 0
    const [rounding, setRounding] = useStoredOption(ROUNDING_KEY, ROUNDING_VALUES, DEFAULT_ROUNDING)
    const { panelRef, style, dragHandleProps } = useFloatingPanel(POSITION_KEY, hasCards)

    if (!hasCards) return null

    const stepCents = Number(rounding) * 100
    const rows = cards.map((card) => {
        const debtCents = toCents(card.debt)
        return { card, debtCents, payCents: debtCents % stepCents }
    })
    const totalDebtCents = rows.reduce((sum, row) => sum + row.debtCents, 0)
    const totalPayCents = rows.reduce((sum, row) => sum + row.payCents, 0)
    const cardsWithPayment = cards.filter((card) => card.paymentAmount != null)
    const totalPaymentCents = cardsWithPayment.reduce((sum, card) => sum + toCents(card.paymentAmount), 0)

    return (
        <aside
            ref={panelRef}
            style={style}
            aria-label="Выделенные карты"
            className="sticky bottom-2 z-40 flex max-h-[50vh] flex-col rounded-xl border bg-card text-card-foreground shadow-lg sm:fixed sm:right-6 sm:bottom-6 sm:max-h-[80vh] sm:w-96"
        >
            <div
                className="flex items-center gap-2 border-b px-4 py-2 select-none sm:cursor-grab sm:touch-none sm:active:cursor-grabbing"
                {...dragHandleProps}
            >
                <GripVertical className="hidden size-4 text-muted-foreground sm:block" />
                <span className="flex-1 text-sm font-medium">Выделено: {cards.length}</span>
                <Button
                    variant="ghost"
                    size="icon-sm"
                    onClick={onClear}
                    aria-label="Снять выделение"
                    title="Снять выделение (Esc)"
                >
                    <X />
                </Button>
            </div>

            <div className="flex flex-col gap-3 px-4 py-3">
                <div className="flex items-center justify-between gap-3">
                    <span className="text-sm text-muted-foreground">Округлять долг</span>
                    <Select value={rounding} onValueChange={setRounding}>
                        <SelectTrigger size="sm" className="w-32">
                            <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                            {ROUNDING_OPTIONS.map((option) => (
                                <SelectItem key={option.value} value={option.value}>
                                    {option.label}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </div>

                <div className="flex flex-col gap-0.5">
                    <span className="text-sm text-muted-foreground">Общая задолженность</span>
                    <span className="text-3xl leading-tight font-semibold text-destructive tabular-nums">
                        {rubles(totalDebtCents)}
                    </span>
                </div>

                <div className="flex flex-col gap-1 text-sm">
                    <SummaryRow
                        label="Итого внести"
                        value={rubles(totalPayCents)}
                        valueClassName={totalPayCents > 0 ? 'font-semibold text-success' : 'text-muted-foreground'}
                    />
                    <SummaryRow label="Долг после внесения" value={rubles(totalDebtCents - totalPayCents)} />
                    {cardsWithPayment.length > 0 && (
                        <SummaryRow label="К внесению по картам" value={rubles(totalPaymentCents)} />
                    )}
                </div>
            </div>

            <ul className="flex min-h-0 flex-col overflow-y-auto border-t py-1">
                {rows.map(({ card, debtCents, payCents }) => (
                    <li key={card.id} className="flex flex-col gap-0.5 px-4 py-2 text-sm">
                        <div className="flex items-center gap-2">
                            <button
                                type="button"
                                onClick={() => onLocate(card.id)}
                                title="Показать карту на странице"
                                className="min-w-0 flex-1 truncate text-left font-medium hover:underline"
                            >
                                {card.name}
                            </button>
                            <span className={cn('shrink-0 text-xs', graceClass(card.graceDaysLeft))}>
                                {graceShortText(card.graceDaysLeft)}
                            </span>
                            <Button
                                variant="ghost"
                                size="icon-xs"
                                onClick={() => onRemove(card.id)}
                                aria-label={`Убрать «${card.name}» из выделения`}
                            >
                                <X />
                            </Button>
                        </div>
                        <div className="flex items-center gap-2">
                            <span className="min-w-0 flex-1 truncate tabular-nums">
                                {rubles(debtCents)}
                                {payCents > 0 && (
                                    <span className="text-muted-foreground"> → {rubles(debtCents - payCents)}</span>
                                )}
                            </span>
                            {payCents > 0 ? (
                                <>
                                    <Button
                                        variant="ghost"
                                        size="xs"
                                        onClick={() => onRepay(card, centsToInput(payCents))}
                                        title="Погасить эту сумму"
                                        className="text-sm font-semibold text-success tabular-nums hover:text-success"
                                    >
                                        {rubles(payCents)}
                                    </Button>
                                    <Button
                                        variant="ghost"
                                        size="icon-xs"
                                        onClick={() => copyAmount(payCents)}
                                        aria-label="Скопировать сумму"
                                        title="Скопировать сумму"
                                    >
                                        <Copy />
                                    </Button>
                                </>
                            ) : (
                                <span className="text-muted-foreground tabular-nums">{rubles(0)}</span>
                            )}
                        </div>
                    </li>
                ))}
            </ul>
        </aside>
    )
}

const SummaryRow = ({ label, value, valueClassName }) => (
    <div className="flex justify-between gap-3">
        <span className="text-muted-foreground">{label}</span>
        <span className={cn('font-medium tabular-nums', valueClassName)}>{value}</span>
    </div>
)

export default CreditCardSelectionPanel
