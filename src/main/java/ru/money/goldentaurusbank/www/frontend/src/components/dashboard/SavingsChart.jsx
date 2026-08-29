// components/Dashboard/SavingsChart.jsx
import { useEffect, useState } from 'react'
import {
    Bar, XAxis, YAxis, CartesianGrid,
    Tooltip, Line, ComposedChart, Cell
} from 'recharts'
import api from '@/api/axios'
import { Button } from '@/components/ui/button'
import { ButtonGroup } from '@/components/ui/button-group'
import { ChartContainer } from '@/components/ui/chart'
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { EmptyState } from '@/components/ui-app/page-state'

// Цвета серий берём из токенов темы, а не хексами по месту
const CHART_CONFIG = {
    savings: { label: 'Накопления', color: 'var(--chart-1)' },
    debtBar: { label: 'Задолженность', color: 'var(--destructive)' },
    trend: { label: 'Тренд', color: 'var(--chart-2)' },
}

const SavingsChart = ({ refreshKey }) => {
    const [data, setData] = useState([]);
    const [years, setYears] = useState([]);
    const [selectedYear, setSelectedYear] = useState(null);
    const [selectedMonth, setSelectedMonth] = useState(null);
    const [viewMode, setViewMode] = useState('month');
    const [loading, setLoading] = useState(true);
    const [totalSavings, setTotalSavings] = useState(0);
    const [totalDebt, setTotalDebt] = useState(0);

    const monthNames = ['Янв', 'Фев', 'Мар', 'Апр', 'Май', 'Июн', 'Июл', 'Авг', 'Сен', 'Окт', 'Ноя', 'Дек'];
    const currentDate = new Date();
    const currentYear = currentDate.getFullYear();
    const currentMonth = currentDate.getMonth() + 1;
    const currentDay = currentDate.getDate();

    useEffect(() => {
        fetchAvailableYears();
    }, []);

    useEffect(() => {
        if (selectedYear !== undefined) {
            if (viewMode === 'year') {
                fetchYearlyStatistics();
            } else {
                fetchDailyStatistics();
            }
        }
    }, [selectedYear, selectedMonth, viewMode, refreshKey]);

    const fetchAvailableYears = async () => {
        try {
            const response = await api.get('/transactions/available-years');
            setYears(response.data);

            if (response.data.includes(currentYear)) {
                setSelectedYear(currentYear);
                setSelectedMonth(currentMonth);
            } else if (response.data.length > 0) {
                setSelectedYear(response.data[response.data.length - 1]);
                setSelectedMonth(currentMonth);
            } else {
                setSelectedYear(currentYear);
                setSelectedMonth(currentMonth);
            }
        } catch (err) {
            console.error('Error fetching years:', err);
            setSelectedYear(currentYear);
            setSelectedMonth(currentMonth);
        }
    };

    const fetchYearlyStatistics = async () => {
        setLoading(true);
        try {
            const response = await api.get('/transactions/dashboard/statistics', {
                params: { year: selectedYear }
            });

            // Бэкенд отдаёт готовые 12 месяцев с накоплениями и изменением за месяц:
            // накопления считаются обратным ходом от фактической суммы слитков,
            // поэтому правый край графика совпадает с «Общей суммой всех слитков».
            const monthlyData = response.data.monthlyData || [];

            setTotalSavings(monthlyData[monthlyData.length - 1]?.savings || 0);
            setTotalDebt(response.data.totalDebt || 0);

            const transformedData = monthlyData.map((item) => {
                const monthNum = parseInt(item.month.split('-')[1]);
                const isFuture = selectedYear === currentYear && monthNum > currentMonth;
                const isCurrentMonth = selectedYear === currentYear && monthNum === currentMonth;
                const savings = item.savings || 0;
                const debt = Number(item.debt || 0);

                return {
                    ...item,
                    monthLabel: `${monthNames[monthNum - 1]} ${selectedYear}`,
                    monthNumber: monthNum,
                    savings,
                    debt,
                    // Задолженность рисуется вниз, поэтому в график уходит с минусом
                    debtBar: -debt,
                    change: item.netChange || 0,
                    barColor: savings >= 0 ? 'url(#savingsFill)' : 'var(--destructive)',
                    barOpacity: isFuture ? 0.35 : (isCurrentMonth ? 0.7 : 1)
                };
            });

            setData(transformedData);
        } catch (err) {
            console.error('Error fetching yearly statistics:', err);
            setData([]);
        } finally {
            setLoading(false);
        }
    };

    const fetchDailyStatistics = async () => {
        if (!selectedYear || !selectedMonth) return;

        setLoading(true);
        try {
            const response = await api.get('/transactions/dashboard/daily-statistics', {
                params: {
                    year: selectedYear,
                    month: selectedMonth
                }
            });

            // Бэкенд отдаёт все дни месяца, включая пустые, — достраивать нечего.
            const dailyData = response.data.dailyData || [];
            setTotalSavings(dailyData[dailyData.length - 1]?.savings || 0);
            setTotalDebt(response.data.totalDebt || 0);

            const isCurrentMonth = selectedYear === currentYear && selectedMonth === currentMonth;

            setData(dailyData.map((item) => {
                const isFuture = isCurrentMonth && item.day > currentDay;
                const isToday = isCurrentMonth && item.day === currentDay;
                const savings = item.savings || 0;
                const debt = Number(item.debt || 0);

                return {
                    ...item,
                    date: `${String(item.day).padStart(2, '0')}.${String(selectedMonth).padStart(2, '0')}`,
                    debt,
                    // Задолженность рисуется вниз, поэтому в график уходит с минусом
                    debtBar: -debt,
                    change: item.dailyChange || 0,
                    barColor: savings >= 0 ? 'url(#savingsFill)' : 'var(--destructive)',
                    barOpacity: isFuture ? 0.35 : (isToday ? 0.7 : 1)
                };
            }));
        } catch (err) {
            console.error('Error fetching daily statistics:', err);
            setData([]);
        } finally {
            setLoading(false);
        }
    };

    const handleYearChange = (year) => {
        setSelectedYear(year);
        if (viewMode === 'month') {
            setSelectedMonth(1);
        }
    };

    const handleMonthChange = (month) => {
        setSelectedMonth(month);
    };

    const handleViewModeChange = (mode) => {
        setViewMode(mode);
        setData([]);

        if (mode === 'year') {
            setSelectedMonth(null);
        } else {
            setSelectedMonth(currentMonth);
            if (!selectedYear || !years.includes(selectedYear)) {
                setSelectedYear(currentYear);
            }
        }
    };

    const formatCurrency = (value) => {
        if (!value && value !== 0) return '0 ₽';
        return new Intl.NumberFormat('ru-RU', {
            style: 'currency',
            currency: 'RUB',
            minimumFractionDigits: 0,
            maximumFractionDigits: 0
        }).format(value);
    };

    const CustomTooltip = ({ active, payload, label }) => {
        if (active && payload && payload.length) {
            const point = payload[0]?.payload || {};
            const savings = point.savings || 0;
            const debt = point.debt || 0;
            const change = point.change || 0;
            const changeLabel = viewMode === 'year'
                ? 'к прошлому месяцу'
                : 'к прошлому дню';
            const changeClass = change > 0
                ? 'text-success'
                : (change < 0 ? 'text-destructive' : 'text-muted-foreground');
            const changeSign = change > 0 ? '+' : '';
            return (
                <div className="min-w-45 rounded-lg border bg-popover px-4 py-3 text-popover-foreground shadow-md">
                    <p className="font-semibold">{label}</p>
                    <p className="mt-2 text-lg font-semibold text-primary">
                        {formatCurrency(savings)}
                    </p>
                    <p className={`mt-1 text-sm font-medium ${changeClass}`}>
                        {changeSign}{formatCurrency(change)}{' '}
                        <span className="text-muted-foreground">{changeLabel}</span>
                    </p>
                    {debt > 0 && (
                        <p className="mt-2 text-sm font-semibold text-destructive">
                            Долг по картам: {formatCurrency(debt)}
                        </p>
                    )}
                </div>
            );
        }
        return null;
    };

    if (loading) {
        return <Skeleton className="h-[380px] w-full" />;
    }

    if (!data || data.length === 0) {
        return <EmptyState className="border-0" title="Нет данных для отображения" />;
    }

    const isYearView = viewMode === 'year';
    // Красные столбцы вниз показываем только тем, у кого есть кредитные карты
    const hasDebt = data.some(item => Number(item.debt || 0) > 0);

    return (
        <div className="flex flex-col gap-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                    <h3 className="font-semibold">Накопления</h3>
                    <p className="text-sm text-muted-foreground">
                        Общая сумма:{' '}
                        <span className="font-semibold text-primary">
                            {formatCurrency(totalSavings)}
                        </span>
                        {totalDebt > 0 && (
                            <>
                                {' · '}Долг по картам:{' '}
                                <span className="font-semibold text-destructive">
                                    {formatCurrency(totalDebt)}
                                </span>
                            </>
                        )}
                    </p>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                    <ButtonGroup>
                        <Button
                            size="sm"
                            variant={isYearView ? 'default' : 'outline'}
                            onClick={() => handleViewModeChange('year')}
                        >
                            Год
                        </Button>
                        <Button
                            size="sm"
                            variant={!isYearView ? 'default' : 'outline'}
                            onClick={() => handleViewModeChange('month')}
                        >
                            Месяц
                        </Button>
                    </ButtonGroup>

                    <Select
                        value={selectedYear ? String(selectedYear) : ''}
                        onValueChange={(value) => handleYearChange(Number(value))}
                    >
                        <SelectTrigger size="sm" className="w-28">
                            <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                            {years.map(year => (
                                <SelectItem key={year} value={String(year)}>{year}</SelectItem>
                            ))}
                        </SelectContent>
                    </Select>

                    {!isYearView && (
                        <Select
                            value={selectedMonth ? String(selectedMonth) : ''}
                            onValueChange={(value) => handleMonthChange(Number(value))}
                        >
                            <SelectTrigger size="sm" className="w-28">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                {monthNames.map((name, index) => (
                                    <SelectItem key={index + 1} value={String(index + 1)}>
                                        {name}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    )}
                </div>
            </div>

            <ChartContainer config={CHART_CONFIG} className="h-[380px] w-full">
                {/* stackOffset="sign" — иначе стек копит сумму и долг откладывается
                    вниз от вершины синего столбца, а не от нулевой линии */}
                <ComposedChart data={data} stackOffset="sign"
                               margin={{ top: 20, right: 30, left: 20, bottom: 20 }}>
                    <defs>
                        {/* Градиент по высоте столбца: плоская заливка выглядит
                            плакатно, особенно на широком графике за год */}
                        <linearGradient id="savingsFill" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stopColor="var(--chart-1)" stopOpacity={1} />
                            <stop offset="100%" stopColor="var(--chart-1)" stopOpacity={0.55} />
                        </linearGradient>
                        <linearGradient id="debtFill" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stopColor="var(--destructive)" stopOpacity={0.55} />
                            <stop offset="100%" stopColor="var(--destructive)" stopOpacity={1} />
                        </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                    <XAxis
                        dataKey={isYearView ? 'monthLabel' : 'date'}
                        stroke="var(--muted-foreground)"
                        interval={isYearView ? 0 : Math.floor(data.length / 15)}
                    />
                    <YAxis
                        tickFormatter={(value) => {
                            // Ось уходит в минус под столбцы задолженности — сокращаем по модулю
                            const sign = value < 0 ? '-' : '';
                            const abs = Math.abs(value);
                            if (abs >= 1000000) return `${sign}${(abs / 1000000).toFixed(1)}M`;
                            if (abs >= 1000) return `${sign}${(abs / 1000).toFixed(0)}K`;
                            return value;
                        }}
                    />
                    <Tooltip content={<CustomTooltip />} />
                    <Bar
                        dataKey="savings"
                        name="Накопления"
                        stackId="savingsDebt"
                        maxBarSize={56}
                        radius={[6, 6, 0, 0]}
                    >
                        {data.map((entry, index) => (
                            <Cell
                                key={`cell-${index}`}
                                fill={entry.barColor || 'var(--chart-1)'}
                                opacity={entry.barOpacity !== undefined ? entry.barOpacity : 1}
                            />
                        ))}
                    </Bar>
                    {/* Задолженность по картам: столбцы вниз ровно под накоплениями
                        (общий stackId), по их вершинам — жёлтая линия */}
                    {hasDebt && (
                        <Bar
                            dataKey="debtBar"
                            name="Задолженность"
                            fill="url(#debtFill)"
                            stackId="savingsDebt"
                            maxBarSize={56}
                            radius={[0, 0, 6, 6]}
                        />
                    )}
                    {hasDebt && (
                        <Line
                            type="monotone"
                            dataKey="debtBar"
                            name="Долг"
                            stroke="var(--chart-4)"
                            strokeWidth={3}
                            dot={{ r: 4, fill: 'var(--chart-4)', stroke: 'var(--background)', strokeWidth: 2 }}
                            activeDot={{ r: 7, fill: 'var(--chart-4)', stroke: 'var(--background)', strokeWidth: 3 }}
                        />
                    )}
                    <Line
                        type="monotone"
                        dataKey="savings"
                        name="Тренд"
                        stroke="var(--chart-2)"
                        strokeWidth={3.5}
                        dot={(props) => {
                            const { cx, cy, payload } = props;
                            const isFuture = payload?.barOpacity !== undefined && payload.barOpacity < 0.5;
                            return (
                                <circle
                                    cx={cx}
                                    cy={cy}
                                    r={5}
                                    fill="var(--chart-2)"
                                    opacity={isFuture ? 0.35 : 1}
                                    stroke="var(--background)"
                                    strokeWidth={2}
                                />
                            );
                        }}
                        activeDot={{
                            r: 9,
                            fill: 'var(--chart-2)',
                            stroke: 'var(--background)',
                            strokeWidth: 3,
                        }}
                    />
                </ComposedChart>
            </ChartContainer>

            <p className="text-center text-sm text-muted-foreground">
                {isYearView
                    ? 'Накопления по месяцам'
                    : `Дневная динамика за ${monthNames[selectedMonth - 1]} ${selectedYear}`
                }
                {!isYearView && selectedYear === currentYear && selectedMonth === currentMonth && (
                    <span className="ml-3 text-primary">
                        Полупрозрачные столбцы — будущие дни
                    </span>
                )}
                {hasDebt && (
                    <span className="ml-3 text-destructive">
                        Столбцы вниз — задолженность по кредитным картам
                    </span>
                )}
            </p>
        </div>
    );
};

export default SavingsChart;