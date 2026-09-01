import { useCallback, useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import {
    Bar,
    CartesianGrid,
    Cell,
    ComposedChart,
    Line,
    ReferenceLine,
    Tooltip as RechartsTooltip,
    XAxis,
    YAxis,
} from 'recharts'
import {
    AlertTriangle,
    ChevronDown,
    ChevronRight,
    PiggyBank,
    Target,
    TrendingDown,
    Undo2,
    Wallet,
} from 'lucide-react'
import api from '@/api/axios'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ChartContainer } from '@/components/ui/chart'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { DatePicker } from '@/components/ui-app/date-picker'
import { FormDialog } from '@/components/ui-app/form-dialog'
import { StatCard, StatGrid } from '@/components/ui-app/stat-card'
import { EmptyState, PageLoading } from '@/components/ui-app/page-state'
import { useConfirm } from '@/components/ui-app/confirm-dialog'
import { formatAmount, formatCurrency, formatDateTime, toCents } from '@/lib/format'
import { cn } from '@/lib/utils'

const MONTHS = [
    'Январь', 'Февраль', 'Март', 'Апрель', 'Май', 'Июнь',
    'Июль', 'Август', 'Сентябрь', 'Октябрь', 'Ноябрь', 'Декабрь',
]

// Radix Select не умеет пустую строку значением пункта
const NONE = 'none'

const CHART_CONFIG = {
    spent: { label: 'Потрачено', color: 'var(--chart-1)' },
    balance: { label: 'Остаток', color: 'var(--chart-2)' },
}

const bullionLabel = (bullion) =>
    [bullion?.bullionName?.title ?? bullion?.title, bullion?.vault?.name ?? bullion?.vaultName]
        .filter(Boolean)
        .join(' | ')

const BudgetMonthTab = () => {
    const today = useMemo(() => new Date(), [])
    const [year, setYear] = useState(today.getFullYear())
    const [month, setMonth] = useState(today.getMonth() + 1)

    const [report, setReport] = useState(null)
    const [bullions, setBullions] = useState([])
    const [loading, setLoading] = useState(true)
    const [saving, setSaving] = useState(false)

    const [expandedDay, setExpandedDay] = useState(null)
    const [dayTransactions, setDayTransactions] = useState([])
    const [dayLoading, setDayLoading] = useState(false)

    const [planDialog, setPlanDialog] = useState(false)
    const [planForm, setPlanForm] = useState({ plannedAmount: '', comment: '' })
    const [fundDialog, setFundDialog] = useState(false)
    const [fundForm, setFundForm] = useState({ amount: '', sourceBullionId: '', dateOperation: '', comment: '' })
    const [closeDialog, setCloseDialog] = useState(false)
    const [closeForm, setCloseForm] = useState({ targetBullionId: '', comment: '' })

    const { confirm, confirmDialog } = useConfirm()

    const fetchReport = useCallback(async () => {
        setLoading(true)
        try {
            const { data } = await api.get(`/budget/${year}/${month}`)
            setReport(data)
        } catch (err) {
            console.error('Error fetching budget report:', err)
        } finally {
            setLoading(false)
        }
    }, [year, month])

    useEffect(() => {
        fetchReport()
        setExpandedDay(null)
    }, [fetchReport])

    useEffect(() => {
        api.get('/bullions')
            .then(({ data }) => setBullions(data?.data || []))
            .catch((err) => console.error('Error fetching bullions:', err))
    }, [])

    const budgetBullion = report?.budgetBullion
    const planned = report?.plannedAmount

    // Данные графика мемоизируем: без этого перерисовка вкладки перезапускает
    // анимацию Recharts, потому что пропс приезжает новым объектом каждый раз
    const chartData = useMemo(
        () =>
            (report?.days || []).map((day) => ({
                ...day,
                spentValue: Number(day.spent),
                balanceValue: Number(day.balance),
            })),
        [report]
    )

    const dailyPace = useMemo(() => {
        if (!planned || !report?.days?.length) return null
        return Number(planned) / report.days.length
    }, [planned, report])

    const discrepancy = toCents(report?.discrepancy)

    const applyReport = (data) => {
        setReport(data)
        setExpandedDay(null)
    }

    const saveSettings = async (budgetBullionId, sourceBullionId) => {
        try {
            const { data } = await api.put('/budget/settings', { budgetBullionId, sourceBullionId })
            // Настройки отдают текущий месяц; если смотрим другой — перечитываем
            if (data.year === year && data.month === month) applyReport(data)
            else fetchReport()
            toast.success('Настройки бюджета сохранены')
        } catch (err) {
            console.error('Error saving budget settings:', err)
        }
    }

    const handleBudgetBullionChange = (value) => {
        const nextId = value === NONE ? null : Number(value)
        const sourceId = report?.sourceBullion?.id ?? null

        confirm({
            title: 'Сменить бюджетный слиток',
            description:
                'Отчёт будет пересчитан по новому слитку. Корзины у уже записанных операций ' +
                'останутся как есть — при необходимости переразметьте их в таблице.',
            confirmText: 'Сменить',
            onConfirm: () => saveSettings(nextId, sourceId === nextId ? null : sourceId),
        })
    }

    const handleSourceBullionChange = (value) => {
        const nextId = value === NONE ? null : Number(value)
        saveSettings(budgetBullion?.id ?? null, nextId)
    }

    const toggleDay = async (day) => {
        if (expandedDay === day.day) {
            setExpandedDay(null)
            return
        }
        setExpandedDay(day.day)
        setDayLoading(true)
        try {
            const { data } = await api.get(`/budget/${year}/${month}/days/${day.day}/transactions`)
            setDayTransactions(data || [])
        } catch (err) {
            console.error('Error fetching day transactions:', err)
            setDayTransactions([])
        } finally {
            setDayLoading(false)
        }
    }

    const handleRollback = (transaction) => {
        confirm({
            title: 'Откат операции',
            description:
                `Откатить операцию на ${formatCurrency(transaction.amount)}? ` +
                'Обратная операция запишется сегодняшним числом, а не датой оригинала.',
            confirmText: 'Откатить',
            onConfirm: async () => {
                const { data } = await api.post(
                    `/budget/${year}/${month}/transactions/${transaction.id}/rollback`
                )
                applyReport(data)
                toast.success('Операция откачена')
            },
        })
    }

    const handleBucket = async (transaction, budgetOperation) => {
        try {
            const { data } = await api.patch(`/budget/transactions/${transaction.id}/bucket`, {
                budgetOperation,
            })
            setReport(data)
            setDayTransactions((prev) =>
                prev.map((tx) => (tx.id === transaction.id ? { ...tx, budgetOperation } : tx))
            )
        } catch (err) {
            console.error('Error changing bucket:', err)
        }
    }

    const submitPlan = async (event) => {
        event.preventDefault()
        setSaving(true)
        try {
            const { data } = await api.put(`/budget/${year}/${month}/plan`, {
                plannedAmount: planForm.plannedAmount,
                comment: planForm.comment || null,
            })
            applyReport(data)
            setPlanDialog(false)
            toast.success('План месяца сохранён')
        } catch (err) {
            console.error('Error saving plan:', err)
        } finally {
            setSaving(false)
        }
    }

    const submitFund = async (event) => {
        event.preventDefault()
        setSaving(true)
        try {
            const { data } = await api.post(`/budget/${year}/${month}/fund`, {
                amount: fundForm.amount || null,
                sourceBullionId: fundForm.sourceBullionId ? Number(fundForm.sourceBullionId) : null,
                dateOperation: fundForm.dateOperation || null,
                comment: fundForm.comment || null,
            })
            applyReport(data)
            setFundDialog(false)
            toast.success('Бюджет пополнен')
        } catch (err) {
            console.error('Error funding budget:', err)
        } finally {
            setSaving(false)
        }
    }

    const submitClose = async (event) => {
        event.preventDefault()
        setSaving(true)
        try {
            const { data } = await api.post(`/budget/${year}/${month}/close`, {
                targetBullionId: Number(closeForm.targetBullionId),
                comment: closeForm.comment || null,
            })
            applyReport(data)
            setCloseDialog(false)
            toast.success('Месяц закрыт, остаток уведён')
        } catch (err) {
            console.error('Error closing month:', err)
        } finally {
            setSaving(false)
        }
    }

    const openFundDialog = () => {
        setFundForm({
            amount: '',
            sourceBullionId: report?.sourceBullion?.id ? String(report.sourceBullion.id) : '',
            dateOperation: '',
            comment: '',
        })
        setFundDialog(true)
    }

    if (loading && !report) {
        return <PageLoading />
    }

    const fundedToPlan = planned != null && toCents(report?.funding) >= toCents(planned)
    const years = Array.from({ length: 6 }, (_, i) => today.getFullYear() - 4 + i)

    return (
        <TooltipProvider>
            <div className="flex flex-col gap-6">
                {/* Шапка: период, слитки, кнопки */}
                <div className="flex flex-wrap items-end gap-3">
                    <div className="flex gap-2">
                        <Select value={String(month)} onValueChange={(v) => setMonth(Number(v))}>
                            <SelectTrigger className="w-36"><SelectValue /></SelectTrigger>
                            <SelectContent>
                                {MONTHS.map((name, index) => (
                                    <SelectItem key={index + 1} value={String(index + 1)}>{name}</SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                        <Select value={String(year)} onValueChange={(v) => setYear(Number(v))}>
                            <SelectTrigger className="w-28"><SelectValue /></SelectTrigger>
                            <SelectContent>
                                {years.map((value) => (
                                    <SelectItem key={value} value={String(value)}>{value}</SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>

                    <div className="flex flex-col gap-1">
                        <Label className="text-xs text-muted-foreground">Бюджетный слиток</Label>
                        <Select
                            value={budgetBullion?.id ? String(budgetBullion.id) : NONE}
                            onValueChange={handleBudgetBullionChange}
                        >
                            <SelectTrigger className="w-64"><SelectValue placeholder="Не выбран" /></SelectTrigger>
                            <SelectContent>
                                <SelectItem value={NONE}>Не выбран</SelectItem>
                                {bullions.map((bullion) => (
                                    <SelectItem key={bullion.id} value={String(bullion.id)}>
                                        {bullionLabel(bullion)}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </div>

                    <div className="flex flex-col gap-1">
                        <Label className="text-xs text-muted-foreground">Финансировать с</Label>
                        <Select
                            value={report?.sourceBullion?.id ? String(report.sourceBullion.id) : NONE}
                            onValueChange={handleSourceBullionChange}
                        >
                            <SelectTrigger className="w-64"><SelectValue placeholder="Не выбран" /></SelectTrigger>
                            <SelectContent>
                                <SelectItem value={NONE}>Не выбран</SelectItem>
                                {bullions
                                    .filter((bullion) => bullion.id !== budgetBullion?.id)
                                    .map((bullion) => (
                                        <SelectItem key={bullion.id} value={String(bullion.id)}>
                                            {bullionLabel(bullion)}
                                        </SelectItem>
                                    ))}
                            </SelectContent>
                        </Select>
                    </div>

                    {budgetBullion && (
                        <div className="ml-auto flex flex-wrap gap-2">
                            <Button variant="outline" onClick={() => {
                                setPlanForm({
                                    plannedAmount: planned != null ? String(planned) : '',
                                    comment: report?.planComment || '',
                                })
                                setPlanDialog(true)
                            }}>
                                <Target />
                                {planned != null ? 'План месяца' : 'Задать план'}
                            </Button>
                            <Button onClick={openFundDialog}>
                                <PiggyBank />
                                {fundedToPlan
                                    ? 'Доложить'
                                    : planned != null
                                        ? `Профинансировать ${formatAmount(Number(planned) - Number(report.funding))}`
                                        : 'Профинансировать'}
                            </Button>
                            <Button
                                variant="outline"
                                disabled={toCents(report?.closingBalance) <= 0}
                                onClick={() => {
                                    setCloseForm({ targetBullionId: '', comment: '' })
                                    setCloseDialog(true)
                                }}
                            >
                                <TrendingDown />
                                Закрыть месяц
                            </Button>
                        </div>
                    )}
                </div>

                {!budgetBullion ? (
                    <EmptyState
                        icon={Wallet}
                        title="Бюджетный слиток не выбран"
                        description="Выберите слиток, по которому считать месячный бюджет — подойдёт любой, обычно это кошелёк текущих расходов."
                    />
                ) : (
                    <>
                        {discrepancy !== 0 && (
                            <Card className="border-destructive/40 bg-destructive/5">
                                <CardContent className="flex items-start gap-3">
                                    <AlertTriangle className="mt-0.5 size-5 shrink-0 text-destructive" />
                                    <div className="text-sm">
                                        <p className="font-medium text-destructive">
                                            Остаток бюджета не сходится с движением слитка на{' '}
                                            {formatCurrency(report.discrepancy)}
                                        </p>
                                        <p className="text-muted-foreground">
                                            Это ошибка подсчёта, а не ваших данных: каждая операция должна
                                            попадать ровно в одну корзину. Покажите это разработчику.
                                        </p>
                                    </div>
                                </CardContent>
                            </Card>
                        )}

                        <StatGrid>
                            <StatCard
                                icon={Target}
                                label="План месяца"
                                value={planned != null ? formatAmount(planned) : '—'}
                                hint={planned == null ? 'не задан' : report.planComment || undefined}
                                tone="info"
                            />
                            <StatCard
                                icon={TrendingDown}
                                label="Потрачено"
                                value={formatAmount(report.spent)}
                                hint={
                                    report.overspend != null && toCents(report.overspend) > 0
                                        ? `перерасход ${formatAmount(report.overspend)}`
                                        : undefined
                                }
                                tone={report.overspend != null && toCents(report.overspend) > 0 ? 'destructive' : 'default'}
                            />
                            <StatCard
                                icon={Wallet}
                                label="Остаток бюджета"
                                value={formatAmount(report.closingBalance)}
                                hint={`сумма слитка ${formatAmount(report.bullionAmount)}`}
                                accent
                            />
                            <StatCard
                                icon={PiggyBank}
                                label="Профинансировано"
                                value={formatAmount(report.funding)}
                                hint={
                                    toCents(report.extraFunding) > 0
                                        ? `сверх плана ${formatAmount(report.extraFunding)}`
                                        : undefined
                                }
                                tone={toCents(report.extraFunding) > 0 ? 'warning' : 'default'}
                            />
                        </StatGrid>

                        <div className="grid gap-6 lg:grid-cols-[20rem_1fr]">
                            <Card>
                                <CardHeader>
                                    <CardTitle className="text-base">Сверка</CardTitle>
                                </CardHeader>
                                <CardContent>
                                    <dl className="flex flex-col gap-1.5 text-sm tabular-nums">
                                        <ReconRow label="Остаток на 1-е" value={report.openingBalance} />
                                        <ReconRow label="+ профинансировано" value={report.funding} />
                                        <ReconRow label="= доступно" value={report.available} strong />
                                        <ReconRow label="− потрачено" value={report.spent} />
                                        <ReconRow label="= остаток" value={report.closingBalance} strong accent />
                                        <ReconRow
                                            label="сумма слитка"
                                            value={report.bullionAmount}
                                            muted
                                        />
                                    </dl>
                                </CardContent>
                            </Card>

                            <Card>
                                <CardHeader>
                                    <CardTitle className="text-base">Траты по дням</CardTitle>
                                </CardHeader>
                                <CardContent>
                                    <ChartContainer config={CHART_CONFIG} className="h-[260px] w-full">
                                        <ComposedChart data={chartData} margin={{ top: 10, right: 16, left: 8, bottom: 4 }}>
                                            <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                                            <XAxis dataKey="day" stroke="var(--muted-foreground)" interval={2} />
                                            <YAxis
                                                stroke="var(--muted-foreground)"
                                                tickFormatter={(value) => {
                                                    const abs = Math.abs(value)
                                                    if (abs >= 1000) return `${value < 0 ? '-' : ''}${(abs / 1000).toFixed(0)}K`
                                                    return value
                                                }}
                                            />
                                            <RechartsTooltip content={<ChartTooltip />} />
                                            {/* Сколько можно тратить в день, чтобы уложиться в план */}
                                            {dailyPace != null && (
                                                <ReferenceLine
                                                    y={dailyPace}
                                                    stroke="var(--muted-foreground)"
                                                    strokeDasharray="4 4"
                                                />
                                            )}
                                            <Bar dataKey="spentValue" name="Потрачено" maxBarSize={22} radius={[4, 4, 0, 0]}>
                                                {chartData.map((entry) => (
                                                    <Cell
                                                        key={entry.day}
                                                        fill={entry.spentValue < 0 ? 'var(--success)' : 'var(--chart-1)'}
                                                    />
                                                ))}
                                            </Bar>
                                            <Line
                                                type="monotone"
                                                dataKey="balanceValue"
                                                name="Остаток"
                                                stroke="var(--chart-2)"
                                                strokeWidth={2.5}
                                                dot={false}
                                            />
                                        </ComposedChart>
                                    </ChartContainer>
                                </CardContent>
                            </Card>
                        </div>

                        <Card className="overflow-hidden py-0">
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead className="w-28">Дата</TableHead>
                                        <TableHead className="text-right">Снято</TableHead>
                                        <TableHead className="text-right">Вернул</TableHead>
                                        <TableHead className="text-right">Возмещено</TableHead>
                                        <TableHead className="text-right">Потрачено</TableHead>
                                        <TableHead className="text-right">Остаток</TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {report.days.map((day) => (
                                        <DayRows
                                            key={day.day}
                                            day={day}
                                            year={year}
                                            month={month}
                                            expanded={expandedDay === day.day}
                                            loading={dayLoading}
                                            transactions={dayTransactions}
                                            budgetBullionId={budgetBullion.id}
                                            onToggle={toggleDay}
                                            onRollback={handleRollback}
                                            onBucket={handleBucket}
                                        />
                                    ))}
                                    <TableRow className="border-t-2 bg-muted/40 font-medium">
                                        <TableCell>Итого</TableCell>
                                        <TableCell className="text-right tabular-nums">
                                            {formatAmount(sumOf(report.days, 'taken'))}
                                        </TableCell>
                                        <TableCell className="text-right tabular-nums">
                                            {formatAmount(sumOf(report.days, 'returned'))}
                                        </TableCell>
                                        <TableCell className="text-right tabular-nums">
                                            {formatAmount(sumOf(report.days, 'compensated'))}
                                        </TableCell>
                                        <TableCell className="text-right tabular-nums">
                                            {formatAmount(report.spent)}
                                        </TableCell>
                                        <TableCell className="text-right tabular-nums">
                                            {formatAmount(report.closingBalance)}
                                        </TableCell>
                                    </TableRow>
                                </TableBody>
                            </Table>
                        </Card>
                    </>
                )}

                <FormDialog
                    open={planDialog}
                    onOpenChange={setPlanDialog}
                    title={`План на ${MONTHS[month - 1].toLowerCase()} ${year}`}
                    description="Ориентир, а не лимит: потратить и профинансировать сверх него можно."
                    onSubmit={submitPlan}
                    saving={saving}
                >
                    <div className="flex flex-col gap-2">
                        <Label htmlFor="planned-amount">Сумма плана</Label>
                        <Input
                            id="planned-amount"
                            type="number"
                            step="0.01"
                            min="0"
                            required
                            value={planForm.plannedAmount}
                            onChange={(e) => setPlanForm({ ...planForm, plannedAmount: e.target.value })}
                        />
                    </div>
                    <div className="flex flex-col gap-2">
                        <Label htmlFor="plan-comment">Комментарий</Label>
                        <Input
                            id="plan-comment"
                            value={planForm.comment}
                            onChange={(e) => setPlanForm({ ...planForm, comment: e.target.value })}
                        />
                    </div>
                </FormDialog>

                <FormDialog
                    open={fundDialog}
                    onOpenChange={setFundDialog}
                    title="Пополнить бюджет"
                    description="Пустая сумма — добрать до плана первым числом. Явная сумма ложится сегодняшней датой, в том числе сверх плана."
                    onSubmit={submitFund}
                    saving={saving}
                    submitText="Перевести"
                >
                    <div className="flex flex-col gap-2">
                        <Label htmlFor="fund-amount">Сумма</Label>
                        <Input
                            id="fund-amount"
                            type="number"
                            step="0.01"
                            min="0"
                            placeholder={
                                planned != null && !fundedToPlan
                                    ? `до плана: ${formatAmount(Number(planned) - Number(report?.funding ?? 0))}`
                                    : 'сколько докинуть'
                            }
                            value={fundForm.amount}
                            onChange={(e) => setFundForm({ ...fundForm, amount: e.target.value })}
                        />
                    </div>
                    <div className="flex flex-col gap-2">
                        <Label>Слиток-источник</Label>
                        <Select
                            value={fundForm.sourceBullionId || NONE}
                            onValueChange={(v) =>
                                setFundForm({ ...fundForm, sourceBullionId: v === NONE ? '' : v })
                            }
                        >
                            <SelectTrigger><SelectValue placeholder="Из настроек" /></SelectTrigger>
                            <SelectContent>
                                <SelectItem value={NONE}>Из настроек</SelectItem>
                                {bullions
                                    .filter((bullion) => bullion.id !== budgetBullion?.id)
                                    .map((bullion) => (
                                        <SelectItem key={bullion.id} value={String(bullion.id)}>
                                            {bullionLabel(bullion)}
                                        </SelectItem>
                                    ))}
                            </SelectContent>
                        </Select>
                    </div>
                    <div className="flex flex-col gap-2">
                        <Label>Дата</Label>
                        <DatePicker
                            value={fundForm.dateOperation}
                            onChange={(value) => setFundForm({ ...fundForm, dateOperation: value })}
                            placeholder="По умолчанию"
                            clearable
                        />
                    </div>
                    <div className="flex flex-col gap-2">
                        <Label htmlFor="fund-comment">Комментарий</Label>
                        <Input
                            id="fund-comment"
                            value={fundForm.comment}
                            onChange={(e) => setFundForm({ ...fundForm, comment: e.target.value })}
                        />
                    </div>
                </FormDialog>

                <FormDialog
                    open={closeDialog}
                    onOpenChange={setCloseDialog}
                    title="Закрыть месяц"
                    description={`Остаток ${formatCurrency(report?.closingBalance)} уйдёт переводом на выбранный слиток. Если не закрывать, он станет началом следующего месяца.`}
                    onSubmit={submitClose}
                    saving={saving}
                    submitDisabled={!closeForm.targetBullionId}
                    submitText="Увести остаток"
                >
                    <div className="flex flex-col gap-2">
                        <Label>Куда увести</Label>
                        <Select
                            value={closeForm.targetBullionId}
                            onValueChange={(v) => setCloseForm({ ...closeForm, targetBullionId: v })}
                        >
                            <SelectTrigger><SelectValue placeholder="Выберите слиток" /></SelectTrigger>
                            <SelectContent>
                                {bullions
                                    .filter((bullion) => bullion.id !== budgetBullion?.id)
                                    .map((bullion) => (
                                        <SelectItem key={bullion.id} value={String(bullion.id)}>
                                            {bullionLabel(bullion)}
                                        </SelectItem>
                                    ))}
                            </SelectContent>
                        </Select>
                    </div>
                    <div className="flex flex-col gap-2">
                        <Label htmlFor="close-comment">Комментарий</Label>
                        <Input
                            id="close-comment"
                            value={closeForm.comment}
                            onChange={(e) => setCloseForm({ ...closeForm, comment: e.target.value })}
                        />
                    </div>
                </FormDialog>

                {confirmDialog}
            </div>
        </TooltipProvider>
    )
}

const sumOf = (days, field) => (days || []).reduce((total, day) => total + Number(day[field]), 0)

const ReconRow = ({ label, value, strong, accent, muted }) => (
    <div className={cn('flex items-baseline justify-between gap-4', strong && 'font-medium')}>
        <dt className={cn('text-muted-foreground', muted && 'text-xs')}>{label}</dt>
        <dd className={cn(accent && 'brand-text text-base font-semibold', muted && 'text-xs text-muted-foreground')}>
            {formatAmount(value)}
        </dd>
    </div>
)

const ChartTooltip = ({ active, payload, label }) => {
    if (!active || !payload?.length) return null
    const day = payload[0].payload
    return (
        <div className="rounded-lg border bg-background p-3 text-sm shadow-md">
            <p className="mb-1 font-medium">{day.date}</p>
            <p className="text-muted-foreground">Потрачено: {formatAmount(day.spent)}</p>
            <p className="text-muted-foreground">Остаток: {formatAmount(day.balance)}</p>
            {toCents(day.funding) !== 0 && (
                <p className="text-muted-foreground">Движение бюджета: {formatAmount(day.funding)}</p>
            )}
        </div>
    )
}

/**
 * Строка дня плюс раскрытый список операций. Дни без операций показываем
 * прочерками — иначе номера дней идут с пропусками, как «транши» в Excel.
 */
const DayRows = ({
    day, year, month, expanded, loading, transactions,
    budgetBullionId, onToggle, onRollback, onBucket,
}) => {
    const empty = day.transactionCount === 0
    const date = new Date(year, month - 1, day.day)
    const weekend = date.getDay() === 0 || date.getDay() === 6
    const future = date > new Date()
    const hasFunding = toCents(day.funding) !== 0

    return (
        <>
            <TableRow
                className={cn(
                    'cursor-pointer',
                    weekend && 'bg-muted/30',
                    future && 'opacity-50',
                    expanded && 'bg-accent/40'
                )}
                onClick={() => !empty && onToggle(day)}
            >
                <TableCell className="whitespace-nowrap">
                    <span className="flex items-center gap-1.5">
                        {empty ? (
                            <span className="inline-block size-4" />
                        ) : expanded ? (
                            <ChevronDown className="size-4 text-muted-foreground" />
                        ) : (
                            <ChevronRight className="size-4 text-muted-foreground" />
                        )}
                        {day.date}
                        {hasFunding && (
                            <Tooltip>
                                <TooltipTrigger asChild>
                                    <Badge variant="outline" className="ml-1">
                                        <PiggyBank className="size-3" />
                                    </Badge>
                                </TooltipTrigger>
                                <TooltipContent>
                                    Движение бюджета: {formatAmount(day.funding)}. В траты не входит.
                                </TooltipContent>
                            </Tooltip>
                        )}
                    </span>
                </TableCell>
                <Amount value={day.taken} empty={empty} />
                <Amount value={day.returned} empty={empty} />
                <Amount value={day.compensated} empty={empty} negativeIsExpense />
                <TableCell
                    className={cn(
                        'text-right font-medium tabular-nums',
                        toCents(day.spent) < 0 && 'text-success'
                    )}
                >
                    {empty && toCents(day.spent) === 0 ? '—' : formatAmount(day.spent)}
                </TableCell>
                <TableCell className="text-right tabular-nums text-muted-foreground">
                    {formatAmount(day.balance)}
                </TableCell>
            </TableRow>

            {expanded && (
                <TableRow className="hover:bg-transparent">
                    <TableCell colSpan={6} className="bg-muted/20 p-0">
                        {loading ? (
                            <div className="p-4 text-sm text-muted-foreground">Загрузка операций...</div>
                        ) : (
                            <div className="flex flex-col divide-y">
                                {transactions.map((tx) => (
                                    <div key={tx.id} className="flex flex-wrap items-center gap-3 px-4 py-2.5 text-sm">
                                        <span className="w-32 shrink-0 text-muted-foreground">
                                            {formatDateTime(tx.dateOperation)}
                                        </span>
                                        <span
                                            className={cn(
                                                'w-28 shrink-0 text-right font-medium tabular-nums',
                                                tx.sourceBullionId === budgetBullionId
                                                    ? 'text-destructive'
                                                    : 'text-success'
                                            )}
                                        >
                                            {tx.sourceBullionId === budgetBullionId ? '−' : '+'}
                                            {formatAmount(tx.amount)}
                                        </span>
                                        <span className="min-w-0 flex-1 break-words">
                                            {tx.description || tx.comment || '—'}
                                        </span>
                                        <label className="flex shrink-0 items-center gap-2 text-xs text-muted-foreground">
                                            <Checkbox
                                                checked={tx.budgetOperation}
                                                onCheckedChange={(checked) => onBucket(tx, checked === true)}
                                            />
                                            трата
                                        </label>
                                        {tx.canRollback && (
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={() => onRollback(tx)}
                                            >
                                                <Undo2 />
                                            </Button>
                                        )}
                                    </div>
                                ))}
                                {transactions.length === 0 && (
                                    <div className="p-4 text-sm text-muted-foreground">Операций нет</div>
                                )}
                            </div>
                        )}
                    </TableCell>
                </TableRow>
            )}
        </>
    )
}

const Amount = ({ value, empty, negativeIsExpense }) => (
    <TableCell
        className={cn(
            'text-right tabular-nums',
            negativeIsExpense && toCents(value) < 0 && 'text-destructive'
        )}
    >
        {empty && toCents(value) === 0 ? '—' : formatAmount(value)}
    </TableCell>
)

export default BudgetMonthTab
