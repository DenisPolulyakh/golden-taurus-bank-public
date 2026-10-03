import { GripVertical, TriangleAlert, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useFloatingPanel } from '@/components/hooks/useFloatingPanel'
import { formatAmount, formatShortDate, toCents } from '@/lib/format'
import { cn } from '@/lib/utils'

const POSITION_KEY = 'vault-selection:position'
const DEPOSIT_INSURANCE_RUB = 1_400_000
const DEPOSIT_INSURANCE_CENTS = DEPOSIT_INSURANCE_RUB * 100
const NO_BANK = 'none'

const rubles = (cents) => `${formatAmount(cents / 100)} ₽`

function summarize(vaults) {
    let totalCents = 0
    let weightedCents = 0
    const banks = new Map()
    for (const vault of vaults) {
        const cents = toCents(vault.totalAmount)
        totalCents += cents
        weightedCents += cents * Number(vault.interestRate || 0)
        const key = vault.bankId ?? NO_BANK
        const bank = banks.get(key) ?? {
            key,
            name: vault.bankName ?? 'Без банка',
            insured: vault.bankId != null,
            cents: 0,
        }
        bank.cents += cents
        banks.set(key, bank)
    }
    return {
        totalCents,
        averageRate: totalCents > 0 ? weightedCents / totalCents : null,
        banks: [...banks.values()].sort((a, b) => b.cents - a.cents),
    }
}

const overLimitCents = (bank) =>
    bank.insured && bank.cents > DEPOSIT_INSURANCE_CENTS ? bank.cents - DEPOSIT_INSURANCE_CENTS : 0

function vaultDetails(vault) {
    const type =
        vault.accountType === 'SAVINGS'
            ? 'Накопительный'
            : vault.closeDate
                ? `Срочный до ${formatShortDate(vault.closeDate)}`
                : 'Срочный'
    return `${vault.bankName ?? 'Без банка'} · ${vault.interestRate}% · ${type}`
}

function VaultSelectionPanel({ vaults, grandTotal, onRemove, onClear, onLocate }) {
    const hasVaults = vaults.length > 0
    const { panelRef, style, dragHandleProps } = useFloatingPanel(POSITION_KEY, hasVaults)

    if (!hasVaults) return null

    const { totalCents, averageRate, banks } = summarize(vaults)
    const grandCents = toCents(grandTotal)
    const showShare = grandCents > 0
    const showBanks = banks.length >= 2 || banks.some((bank) => overLimitCents(bank) > 0)

    return (
        <aside
            ref={panelRef}
            style={style}
            aria-label="Выделенные хранилища"
            className="sticky bottom-2 z-40 flex max-h-[50vh] flex-col rounded-xl border bg-card text-card-foreground shadow-lg sm:fixed sm:right-6 sm:bottom-6 sm:max-h-[80vh] sm:w-96"
        >
            <div
                className="flex items-center gap-2 border-b px-4 py-2 select-none sm:cursor-grab sm:touch-none sm:active:cursor-grabbing"
                {...dragHandleProps}
            >
                <GripVertical className="hidden size-4 text-muted-foreground sm:block" />
                <span className="flex-1 text-sm font-medium">Выделено: {vaults.length}</span>
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

            <div className="flex flex-col gap-0.5 px-4 py-3">
                <span className="text-sm text-muted-foreground">Сумма выделенных</span>
                <span className="brand-text text-3xl leading-tight font-semibold tabular-nums">
                    {rubles(totalCents)}
                </span>
                <div className="mt-2 flex flex-col gap-1 text-sm">
                    {showShare && (
                        <SummaryRow
                            label="Доля от всех сбережений"
                            value={`${((totalCents / grandCents) * 100).toFixed(1)} %`}
                        />
                    )}
                    <SummaryRow
                        label="Средняя ставка"
                        value={averageRate == null ? '—' : `${averageRate.toFixed(2)} %`}
                    />
                </div>
            </div>

            {showBanks && (
                <div className="flex shrink-0 flex-col gap-1 border-t px-4 py-3 text-sm">
                    <span className="text-xs text-muted-foreground">По банкам</span>
                    {banks.map((bank) => {
                        const overCents = overLimitCents(bank)
                        return (
                            <div key={bank.key} className="flex flex-col gap-0.5">
                                <SummaryRow
                                    label={bank.name}
                                    value={rubles(bank.cents)}
                                    valueClassName={overCents > 0 ? 'text-warning' : undefined}
                                />
                                {overCents > 0 && (
                                    <div className="flex items-center justify-between gap-3 pl-3 text-xs text-warning">
                                        <span className="flex items-center gap-1">
                                            <TriangleAlert className="size-3" />
                                            сверх страховки АСВ
                                        </span>
                                        <span className="tabular-nums">{rubles(overCents)}</span>
                                    </div>
                                )}
                            </div>
                        )
                    })}
                </div>
            )}

            <ul className="flex min-h-0 flex-col overflow-y-auto border-t py-1">
                {vaults.map((vault) => (
                    <li key={vault.id} className="flex flex-col gap-0.5 px-4 py-2 text-sm">
                        <div className="flex items-center gap-2">
                            <button
                                type="button"
                                onClick={() => onLocate(vault.id)}
                                title="Показать хранилище в таблице"
                                className="min-w-0 flex-1 truncate text-left font-medium hover:underline"
                            >
                                {vault.name}
                            </button>
                            <span className="shrink-0 tabular-nums">{rubles(toCents(vault.totalAmount))}</span>
                            <Button
                                variant="ghost"
                                size="icon-xs"
                                onClick={() => onRemove(vault.id)}
                                aria-label={`Убрать «${vault.name}» из выделения`}
                            >
                                <X />
                            </Button>
                        </div>
                        <span className="truncate text-xs text-muted-foreground">{vaultDetails(vault)}</span>
                    </li>
                ))}
            </ul>
        </aside>
    )
}

const SummaryRow = ({ label, value, valueClassName }) => (
    <div className="flex justify-between gap-3">
        <span className="min-w-0 truncate text-muted-foreground">{label}</span>
        <span className={cn('shrink-0 font-medium tabular-nums', valueClassName)}>{value}</span>
    </div>
)

export default VaultSelectionPanel
