import { GripVertical, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useFloatingPanel, useStoredOption } from '@/components/hooks/useFloatingPanel'
import { formatAmount, toCents } from '@/lib/format'
import { cn } from '@/lib/utils'

const ROUNDING_OPTIONS = [
    { value: '1', label: 'до 1 ₽' },
    { value: '10', label: 'до 10 ₽' },
    { value: '100', label: 'до 100 ₽' },
    { value: '1000', label: 'до 1 000 ₽' },
    { value: '10000', label: 'до 10 000 ₽' },
    { value: '100000', label: 'до 100 000 ₽' },
    { value: '1000000', label: 'до 1 млн ₽' },
]

const ROUNDING_VALUES = ROUNDING_OPTIONS.map((option) => option.value)

const DEFAULT_ROUNDING = '1000'
const ROUNDING_KEY = 'bullion-selection:rounding'
const POSITION_KEY = 'bullion-selection:position'

function roundingShortfall(totalCents, rubles) {
    const stepCents = Number(rubles) * 100
    const targetCents = Math.ceil(totalCents / stepCents) * stepCents
    return { targetCents, shortfallCents: targetCents - totalCents }
}

function roundingExcess(totalCents, rubles) {
    const stepCents = Number(rubles) * 100
    const floorCents = Math.floor(totalCents / stepCents) * stepCents
    return { floorCents, excessCents: totalCents - floorCents }
}

function nowLocalInput() {
    return new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}

function BullionSelectionPanel({ items, onRemove, onClear, move }) {
    const hasItems = items.length > 0
    const [rounding, setRounding] = useStoredOption(ROUNDING_KEY, ROUNDING_VALUES, DEFAULT_ROUNDING)
    const { panelRef, style, dragHandleProps } = useFloatingPanel(POSITION_KEY, hasItems)

    if (!hasItems) return null

    const totalCents = items.reduce((sum, item) => sum + toCents(item.amount), 0)
    const { targetCents, shortfallCents } = roundingShortfall(totalCents, rounding)
    const { floorCents, excessCents } = roundingExcess(totalCents, rounding)
    const showExcess = Boolean(move) && floorCents > 0 && excessCents > 0
    const canMove = Boolean(move) &&
        !move.loading &&
        !move.disabledReason &&
        move.targetVaultId !== '' &&
        items.some((item) => toCents(item.amount) > 0)

    return (
        <aside
            ref={panelRef}
            style={style}
            aria-label="Выделенные слитки"
            className={cn(
                'sticky bottom-2 z-40 flex max-h-[50vh] flex-col rounded-xl border bg-card text-card-foreground shadow-lg sm:fixed sm:right-6 sm:bottom-6 sm:max-h-[80vh] sm:w-80',
                move && 'sm:w-96',
            )}
        >
            <div
                className="flex items-center gap-2 border-b px-4 py-2 select-none sm:cursor-grab sm:touch-none sm:active:cursor-grabbing"
                {...dragHandleProps}
            >
                <GripVertical className="hidden size-4 text-muted-foreground sm:block" />
                <span className="flex-1 text-sm font-medium">Выделено: {items.length}</span>
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
                    <span className="text-sm text-muted-foreground">Округлять</span>
                    <Select value={rounding} onValueChange={setRounding}>
                        <SelectTrigger size="sm" className="w-36">
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
                    <span className="text-sm text-muted-foreground">
                        Не хватает до {formatAmount(targetCents / 100)} ₽
                    </span>
                    <span className={cn('text-lg font-semibold tabular-nums', shortfallCents === 0 && 'text-success')}>
                        {formatAmount(shortfallCents / 100)} ₽
                    </span>
                </div>

                {showExcess && (
                    <div className="flex flex-col gap-0.5">
                        <span className="text-sm text-muted-foreground">
                            Лишнее сверх {formatAmount(floorCents / 100)} ₽
                        </span>
                        <span className="text-lg font-semibold tabular-nums">
                            {formatAmount(excessCents / 100)} ₽
                        </span>
                    </div>
                )}

                <div className="flex flex-col gap-0.5">
                    <span className="text-sm text-muted-foreground">Сумма выделенных</span>
                    <span className="brand-text text-3xl leading-tight font-semibold tabular-nums">
                        {formatAmount(totalCents / 100)} ₽
                    </span>
                </div>
            </div>

            <ul className="flex min-h-0 flex-col overflow-y-auto border-t py-1">
                {items.map((item) => (
                    <li key={item.id} className="flex items-center gap-2 px-4 py-1.5 text-sm">
                        <span className="min-w-0 flex-1 truncate" title={item.title}>
                            {item.title}
                        </span>
                        {move ? (
                            <div className="flex shrink-0 flex-col items-end">
                                <Input
                                    type="text"
                                    inputMode="decimal"
                                    value={item.inputValue}
                                    onChange={(event) => move.onAmountChange(item.id, event.target.value)}
                                    onBlur={() => move.onAmountBlur(item.id)}
                                    aria-label={`Сумма переноса «${item.title}»`}
                                    className="h-7 w-28 text-right tabular-nums"
                                />
                                <span className="text-xs text-muted-foreground tabular-nums">
                                    из {formatAmount(item.maxAmount)} ₽
                                </span>
                            </div>
                        ) : (
                            <span className="shrink-0 tabular-nums">{formatAmount(item.amount)} ₽</span>
                        )}
                        <Button
                            variant="ghost"
                            size="icon-xs"
                            onClick={() => onRemove(item.id)}
                            aria-label={`Убрать «${item.title}» из выделения`}
                        >
                            <X />
                        </Button>
                    </li>
                ))}
            </ul>

            {move && (
                <div className="flex shrink-0 flex-col gap-3 border-t px-4 py-3">
                    <div className="flex items-center justify-between gap-3">
                        <span className="text-sm text-muted-foreground">Куда</span>
                        <Select value={move.targetVaultId} onValueChange={move.onTargetChange}>
                            <SelectTrigger size="sm" className="w-52">
                                <SelectValue placeholder="Выберите хранилище" />
                            </SelectTrigger>
                            <SelectContent>
                                {move.vaults.map((vault) => (
                                    <SelectItem key={vault.id} value={String(vault.id)}>
                                        {vault.label}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>
                    <div className="flex items-center justify-between gap-3">
                        <span className="text-sm text-muted-foreground">Дата</span>
                        <Input
                            type="datetime-local"
                            value={move.dateOperation}
                            onChange={(event) => move.onDateChange(event.target.value)}
                            max={nowLocalInput()}
                            className="h-8 w-52 text-sm"
                        />
                    </div>
                    <Button
                        onClick={move.onSubmit}
                        disabled={!canMove}
                        title={move.disabledReason ?? ''}
                    >
                        Перенести
                    </Button>
                </div>
            )}
        </aside>
    )
}

export default BullionSelectionPanel
