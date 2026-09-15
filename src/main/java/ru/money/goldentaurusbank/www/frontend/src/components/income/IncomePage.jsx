import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Award, Calendar, TrendingUp, Wallet } from 'lucide-react'
import {
    Area,
    Bar,
    BarChart,
    CartesianGrid,
    ComposedChart,
    Legend,
    Line,
    Tooltip,
    XAxis,
    YAxis,
} from 'recharts'
import api from '@/api/axios'
import { Button } from '@/components/ui/button'
import { ButtonGroup } from '@/components/ui/button-group'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ChartContainer } from '@/components/ui/chart'
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { PageContainer, PageHeader } from '@/components/ui-app/page-header'
import { EmptyState } from '@/components/ui-app/page-state'
import { StatCard, StatGrid } from '@/components/ui-app/stat-card'
import { formatAmount } from '@/lib/format'
import { cn } from '@/lib/utils'
import { incomeTypeColor, INCOME_TYPE_ORDER } from './incomeTypeColors'
import IncomeDonut from './IncomeDonut'

const MONTH_NAMES = ['Янв', 'Фев', 'Мар', 'Апр', 'Май', 'Июн', 'Июл', 'Авг', 'Сен', 'Окт', 'Ноя', 'Дек']

// Вкладки соответствуют шагу группировки на бэке: «Месяц» — дни месяца,
// «Год» — месяцы года, «Всё время» — года целиком.
const VIEWS = [
    { value: 'DAY', label: 'Месяц' },
    { value: 'MONTH', label: 'Год' },
    { value: 'YEAR', label: 'Всё время' },
]

const formatRub = (value) => `${formatAmount(value)} ₽`

const CustomTooltip = ({ active, payload, label, activeTypes }) => {
    if (!active || !payload || !payload.length) return null
    const point = payload[0]?.payload
    if (!point) return null

    const rows = activeTypes
        .map((code) => ({ code, amount: point[code] }))
        .filter((row) => row.amount)

    return (
        <div className="min-w-48 rounded-lg border bg-popover px-4 py-3 text-popover-foreground shadow-md">
            <p className="font-semibold">{label}</p>
            {rows.length > 0 && (
                <div className="mt-2 flex flex-col gap-1">
                    {rows.map((row) => (
                        <div key={row.code} className="flex items-center justify-between gap-4 text-sm">
                            <span className="flex items-center gap-1.5 text-muted-foreground">
                                <span
                                    className="size-2.5 shrink-0 rounded-full"
                                    style={{ backgroundColor: incomeTypeColor(row.code) }}
                                />
                                {row.label}
                            </span>
                            <span className="font-medium tabular-nums">{formatRub(row.amount)}</span>
                        </div>
                    ))}
                </div>
            )}
            <p className="mt-2 border-t pt-1.5 text-sm font-semibold text-primary">
                Итого: {formatRub(point.total)}
            </p>
            {point.cumulative !== undefined && (
                <p className="text-xs text-muted-foreground">
                    Накоплено: {formatRub(point.cumulative)}
                </p>
            )}
        </div>
    )
}

const IncomePage = () => {
    const navigate = useNavigate()

    const [view, setView] = useState('MONTH')
    const [years, setYears] = useState([])
    const [selectedYear, setSelectedYear] = useState(new Date().getFullYear())
    const [selectedMonth, setSelectedMonth] = useState(new Date().getMonth() + 1)
    const [incomeTypes, setIncomeTypes] = useState([])
    const [statistics, setStatistics] = useState(null)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        api.get('/transactions/available-years', { _skipErrorToast: true })
            .then(({ data }) => setYears(data || []))
            .catch(() => setYears([]))
        api.get('/transactions/income-types', { _skipErrorToast: true })
            .then(({ data }) => setIncomeTypes(data || []))
            .catch(() => setIncomeTypes([]))
    }, [])

    useEffect(() => {
        const params = { granularity: view }
        if (view === 'DAY') {
            params.year = selectedYear
            params.month = selectedMonth
        } else if (view === 'MONTH') {
            params.year = selectedYear
        }

        setLoading(true)
        api.get('/transactions/income-statistics', { params, _skipErrorToast: true })
            .then(({ data }) => setStatistics(data))
            .catch(() => setStatistics(null))
            .finally(() => setLoading(false))
    }, [view, selectedYear, selectedMonth])

    // Точки графика: карта по типам разворачивается в плоские поля (SALARY, CASHBACK, ...),
    // recharts умеет стек только по плоским dataKey. Плюс нарастающий итог для линии накоплений.
    const chartData = useMemo(() => {
        if (!statistics) return []
        let running = 0
        return statistics.points.map((point) => {
            running += Number(point.total) || 0
            return {
                period: point.period,
                periodLabel: point.periodLabel,
                total: Number(point.total) || 0,
                cumulative: running,
                ...Object.fromEntries(
                    Object.entries(point.byType || {}).map(([code, amount]) => [code, Number(amount)])
                ),
            }
        })
    }, [statistics])

    // Только типы, у которых за период вообще что-то было — девять пустых
    // легенд на графике никто не читает
    const activeTypes = useMemo(() => {
        if (!statistics) return []
        const present = new Set((statistics.byType || []).map((row) => row.type))
        return INCOME_TYPE_ORDER.filter((code) => present.has(code))
    }, [statistics])

    const typeLabel = (code) =>
        incomeTypes.find((option) => option.code === code)?.displayName
        ?? statistics?.byType?.find((row) => row.type === code)?.displayName
        ?? code

    const total = Number(statistics?.total) || 0

    const averageLabel = view === 'DAY' ? 'Средний доход в день'
        : view === 'MONTH' ? 'Средний доход в месяц'
            : 'Средний доход в год'
    const average = statistics?.points?.length ? total / statistics.points.length : 0

    const topSource = useMemo(() => {
        if (!statistics?.byType?.length) return null
        return [...statistics.byType].sort((a, b) => Number(b.amount) - Number(a.amount))[0]
    }, [statistics])

    const donutData = useMemo(() => {
        if (!statistics?.byType) return []
        return INCOME_TYPE_ORDER
            .map((code) => statistics.byType.find((row) => row.type === code))
            .filter((row) => row && Number(row.amount) > 0)
    }, [statistics])

    const hasData = total > 0

    const handleViewChange = (nextView) => {
        setView(nextView)
        if (nextView === 'DAY' && !selectedMonth) {
            setSelectedMonth(new Date().getMonth() + 1)
        }
    }

    return (
        <PageContainer>
            <PageHeader title="Доходы" description="Классифицированные пополнения и их динамика" onBack={() => navigate('/dashboard')}>
                <ButtonGroup>
                    {VIEWS.map((option) => (
                        <Button
                            key={option.value}
                            size="sm"
                            variant={view === option.value ? 'default' : 'outline'}
                            onClick={() => handleViewChange(option.value)}
                        >
                            {option.label}
                        </Button>
                    ))}
                </ButtonGroup>

                {view !== 'YEAR' && (
                    <Select value={String(selectedYear)} onValueChange={(v) => setSelectedYear(Number(v))}>
                        <SelectTrigger size="sm" className="w-28">
                            <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                            {(years.length ? years : [selectedYear]).map((year) => (
                                <SelectItem key={year} value={String(year)}>{year}</SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                )}

                {view === 'DAY' && (
                    <Select value={String(selectedMonth)} onValueChange={(v) => setSelectedMonth(Number(v))}>
                        <SelectTrigger size="sm" className="w-28">
                            <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                            {MONTH_NAMES.map((name, index) => (
                                <SelectItem key={index + 1} value={String(index + 1)}>{name}</SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                )}
            </PageHeader>

            {!statistics && loading ? (
                <div className="flex flex-col gap-6">
                    <Skeleton className="h-24 w-full" />
                    <Skeleton className="h-[360px] w-full" />
                </div>
            ) : (
            <>
            <StatGrid>
                <StatCard icon={Wallet} label="Итог периода" value={formatRub(total)} tone="primary" />
                <StatCard icon={Calendar} label={averageLabel} value={formatRub(average)} />
                <StatCard
                    icon={Award}
                    label="Главный источник"
                    value={topSource ? topSource.displayName : '—'}
                    hint={topSource ? formatRub(topSource.amount) : undefined}
                    tone="success"
                />
                <StatCard icon={TrendingUp} label="Классифицированных операций" value={
                    (statistics?.byType || []).reduce((sum, row) => sum + Number(row.count || 0), 0)
                } />
            </StatGrid>

            {/* Не держим скелет на каждой смене периода — старый график остаётся
                видимым и лишь гаснет, чтобы не было прыжка макета */}
            <div className={cn('flex flex-col gap-6 transition-opacity', loading && 'pointer-events-none opacity-50')}>
                {!hasData ? (
                    <Card>
                        <CardContent className="py-10">
                            <EmptyState
                                className="border-0"
                                title="Нет классифицированных доходов за период"
                                description="Выберите тип дохода при пополнении слитка — тогда он попадёт сюда"
                            />
                        </CardContent>
                    </Card>
                ) : (
                    <>
                        <Card>
                            <CardHeader>
                                <CardTitle>Доходы по типам</CardTitle>
                            </CardHeader>
                            <CardContent>
                                <ChartContainer config={{}} className="h-[360px] w-full">
                                    <BarChart data={chartData} margin={{ top: 10, right: 20, left: 10, bottom: 10 }}>
                                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                                        <XAxis
                                            dataKey="periodLabel"
                                            stroke="var(--muted-foreground)"
                                            interval={chartData.length > 15 ? Math.floor(chartData.length / 15) : 0}
                                        />
                                        <YAxis
                                            stroke="var(--muted-foreground)"
                                            tickFormatter={(value) => {
                                                if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`
                                                if (value >= 1000) return `${(value / 1000).toFixed(0)}K`
                                                return value
                                            }}
                                        />
                                        <Tooltip content={<CustomTooltip activeTypes={activeTypes.map((code) => ({ code, label: typeLabel(code) }))} />} />
                                        {activeTypes.length > 1 && (
                                            <Legend
                                                formatter={(value) => <span className="text-xs text-foreground">{typeLabel(value)}</span>}
                                            />
                                        )}
                                        {activeTypes.map((code, index) => (
                                            <Bar
                                                key={code}
                                                dataKey={code}
                                                name={code}
                                                stackId="income"
                                                fill={incomeTypeColor(code)}
                                                radius={index === activeTypes.length - 1 ? [4, 4, 0, 0] : 0}
                                                maxBarSize={56}
                                            />
                                        ))}
                                    </BarChart>
                                </ChartContainer>
                            </CardContent>
                        </Card>

                        <Card>
                            <CardHeader>
                                <CardTitle>Накопленный доход</CardTitle>
                            </CardHeader>
                            <CardContent>
                                <ChartContainer config={{}} className="h-[280px] w-full">
                                    <ComposedChart data={chartData} margin={{ top: 10, right: 20, left: 10, bottom: 10 }}>
                                        <defs>
                                            <linearGradient id="cumulativeFill" x1="0" y1="0" x2="0" y2="1">
                                                <stop offset="0%" stopColor="var(--chart-2)" stopOpacity={0.45} />
                                                <stop offset="100%" stopColor="var(--chart-2)" stopOpacity={0.03} />
                                            </linearGradient>
                                        </defs>
                                        <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" vertical={false} />
                                        <XAxis
                                            dataKey="periodLabel"
                                            stroke="var(--muted-foreground)"
                                            interval={chartData.length > 15 ? Math.floor(chartData.length / 15) : 0}
                                        />
                                        <YAxis
                                            stroke="var(--muted-foreground)"
                                            tickFormatter={(value) => {
                                                if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`
                                                if (value >= 1000) return `${(value / 1000).toFixed(0)}K`
                                                return value
                                            }}
                                        />
                                        <Tooltip content={<CustomTooltip activeTypes={[]} />} />
                                        <Area
                                            type="monotone"
                                            dataKey="cumulative"
                                            stroke="none"
                                            fill="url(#cumulativeFill)"
                                            isAnimationActive={false}
                                        />
                                        <Line
                                            type="monotone"
                                            dataKey="cumulative"
                                            name="Накоплено"
                                            stroke="var(--chart-2)"
                                            strokeWidth={3}
                                            dot={false}
                                            activeDot={{ r: 6, fill: 'var(--chart-2)', stroke: 'var(--background)', strokeWidth: 2 }}
                                        />
                                    </ComposedChart>
                                </ChartContainer>
                            </CardContent>
                        </Card>

                        <Card>
                            <CardHeader>
                                <CardTitle>Доли источников за период</CardTitle>
                            </CardHeader>
                            <CardContent>
                                <IncomeDonut data={donutData} total={total} typeLabel={typeLabel} />
                            </CardContent>
                        </Card>
                    </>
                )}
            </div>
            </>
            )}
        </PageContainer>
    )
}

export default IncomePage
