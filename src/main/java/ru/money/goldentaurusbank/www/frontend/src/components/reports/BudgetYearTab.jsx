import { useEffect, useMemo, useState } from 'react'
import {
    Bar,
    CartesianGrid,
    Cell,
    ComposedChart,
    Line,
    Tooltip as RechartsTooltip,
    XAxis,
    YAxis,
} from 'recharts'
import { AlertTriangle, Lock, Wallet } from 'lucide-react'
import api from '@/api/axios'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ChartContainer } from '@/components/ui/chart'
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { EmptyState, PageLoading } from '@/components/ui-app/page-state'
import { formatAmount, toCents } from '@/lib/format'
import { cn } from '@/lib/utils'

const CHART_CONFIG = {
    spent: { label: 'Потрачено', color: 'var(--chart-1)' },
    planned: { label: 'План', color: 'var(--chart-2)' },
}

const BudgetYearTab = () => {
    const currentYear = new Date().getFullYear()
    const [year, setYear] = useState(currentYear)
    const [report, setReport] = useState(null)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        setLoading(true)
        api.get(`/budget/${year}`)
            .then(({ data }) => setReport(data))
            .catch((err) => console.error('Error fetching yearly budget:', err))
            .finally(() => setLoading(false))
    }, [year])

    // Мемоизация обязательна: новый объект в пропсе перезапускает анимацию Recharts
    const chartData = useMemo(
        () =>
            (report?.months || []).map((row) => ({
                ...row,
                spentValue: Number(row.spent),
                plannedValue: row.plannedAmount == null ? null : Number(row.plannedAmount),
            })),
        [report]
    )

    const years = Array.from({ length: 6 }, (_, i) => currentYear - 4 + i)

    if (loading && !report) {
        return <PageLoading />
    }

    return (
        <TooltipProvider>
        <div className="flex flex-col gap-6">
            <div className="flex items-center gap-3">
                <Select value={String(year)} onValueChange={(v) => setYear(Number(v))}>
                    <SelectTrigger className="w-28"><SelectValue /></SelectTrigger>
                    <SelectContent>
                        {years.map((value) => (
                            <SelectItem key={value} value={String(value)}>{value}</SelectItem>
                        ))}
                    </SelectContent>
                </Select>
                {report?.budgetBullion && (
                    <p className="text-sm text-muted-foreground">
                        по слитку «{report.budgetBullion.title}»
                    </p>
                )}
            </div>

            {!report?.budgetBullion ? (
                <EmptyState
                    icon={Wallet}
                    title="Бюджетный слиток не выбран"
                    description="Выберите его на вкладке «Бюджет на месяц» — годовая сводка строится по тому же слитку."
                />
            ) : (
                <>
                    <Card>
                        <CardHeader>
                            <CardTitle className="text-base">Потрачено по месяцам</CardTitle>
                        </CardHeader>
                        <CardContent>
                            <ChartContainer config={CHART_CONFIG} className="h-[300px] w-full">
                                <ComposedChart data={chartData} margin={{ top: 10, right: 16, left: 8, bottom: 4 }}>
                                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                                    <XAxis dataKey="monthLabel" stroke="var(--muted-foreground)" interval={0} />
                                    <YAxis
                                        stroke="var(--muted-foreground)"
                                        tickFormatter={(value) =>
                                            Math.abs(value) >= 1000 ? `${(value / 1000).toFixed(0)}K` : value
                                        }
                                    />
                                    <RechartsTooltip content={<YearTooltip />} />
                                    <Bar dataKey="spentValue" name="Потрачено" maxBarSize={44} radius={[6, 6, 0, 0]}>
                                        {chartData.map((row) => (
                                            <Cell
                                                key={row.month}
                                                fill={
                                                    row.overspend != null && toCents(row.overspend) > 0
                                                        ? 'var(--destructive)'
                                                        : 'var(--chart-1)'
                                                }
                                            />
                                        ))}
                                    </Bar>
                                    {/* Линия плана: сразу видно, какие месяцы вылезли за ориентир */}
                                    <Line
                                        type="monotone"
                                        dataKey="plannedValue"
                                        name="План"
                                        stroke="var(--chart-2)"
                                        strokeWidth={2.5}
                                        strokeDasharray="5 4"
                                        connectNulls
                                        dot={false}
                                    />
                                </ComposedChart>
                            </ChartContainer>
                        </CardContent>
                    </Card>

                    <Card className="overflow-hidden py-0">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>Месяц</TableHead>
                                    <TableHead className="text-right">План</TableHead>
                                    <TableHead className="text-right">Профинансировано</TableHead>
                                    <TableHead className="text-right">Потрачено</TableHead>
                                    <TableHead className="text-right">Отклонение</TableHead>
                                    <TableHead className="text-right">Остаток на конец</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {report.months.map((row) => (
                                    <TableRow key={row.month} className={cn(row.transactionCount === 0 && 'opacity-50')}>
                                        <TableCell className="capitalize">
                                            <span className="flex items-center gap-1.5">
                                                {row.monthLabel}
                                                {row.closed && (
                                                    <Tooltip>
                                                        <TooltipTrigger asChild>
                                                            <Lock className="size-3.5 shrink-0 text-muted-foreground" />
                                                        </TooltipTrigger>
                                                        <TooltipContent>Месяц закрыт</TooltipContent>
                                                    </Tooltip>
                                                )}
                                                {row.snapshotMismatch && (
                                                    <Tooltip>
                                                        <TooltipTrigger asChild>
                                                            <AlertTriangle className="size-3.5 shrink-0 text-warning" />
                                                        </TooltipTrigger>
                                                        <TooltipContent>
                                                            Живые цифры разошлись со снимком закрытия
                                                        </TooltipContent>
                                                    </Tooltip>
                                                )}
                                            </span>
                                        </TableCell>
                                        <TableCell className="text-right tabular-nums">
                                            {row.plannedAmount == null ? '—' : formatAmount(row.plannedAmount)}
                                        </TableCell>
                                        <TableCell className="text-right tabular-nums">
                                            {formatAmount(row.funding)}
                                        </TableCell>
                                        <TableCell className="text-right font-medium tabular-nums">
                                            {formatAmount(row.spent)}
                                        </TableCell>
                                        <TableCell
                                            className={cn(
                                                'text-right tabular-nums',
                                                row.overspend != null &&
                                                    (toCents(row.overspend) > 0 ? 'text-destructive' : 'text-success')
                                            )}
                                        >
                                            {row.overspend == null ? '—' : formatAmount(row.overspend)}
                                        </TableCell>
                                        <TableCell className="text-right tabular-nums text-muted-foreground">
                                            {formatAmount(row.closingBalance)}
                                        </TableCell>
                                    </TableRow>
                                ))}
                                <TableRow className="border-t-2 bg-muted/40 font-medium">
                                    <TableCell>Итого за год</TableCell>
                                    <TableCell className="text-right tabular-nums">
                                        {formatAmount(report.totalPlanned)}
                                    </TableCell>
                                    <TableCell className="text-right tabular-nums">
                                        {formatAmount(report.totalFunding)}
                                    </TableCell>
                                    <TableCell className="text-right tabular-nums">
                                        {formatAmount(report.totalSpent)}
                                    </TableCell>
                                    <TableCell className="text-right tabular-nums">
                                        {formatAmount(Number(report.totalSpent) - Number(report.totalPlanned))}
                                    </TableCell>
                                    <TableCell />
                                </TableRow>
                            </TableBody>
                        </Table>
                    </Card>
                </>
            )}
        </div>
        </TooltipProvider>
    )
}

const YearTooltip = ({ active, payload }) => {
    if (!active || !payload?.length) return null
    const row = payload[0].payload
    return (
        <div className="rounded-lg border bg-background p-3 text-sm shadow-md">
            <p className="mb-1 font-medium capitalize">{row.monthLabel}</p>
            <p className="text-muted-foreground">Потрачено: {formatAmount(row.spent)}</p>
            {row.plannedAmount != null && (
                <p className="text-muted-foreground">План: {formatAmount(row.plannedAmount)}</p>
            )}
            <p className="text-muted-foreground">Остаток: {formatAmount(row.closingBalance)}</p>
        </div>
    )
}

export default BudgetYearTab
