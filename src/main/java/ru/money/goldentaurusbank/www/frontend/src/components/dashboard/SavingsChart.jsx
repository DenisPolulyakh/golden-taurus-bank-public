// components/Dashboard/SavingsChart.jsx
import React, { useEffect, useState } from 'react';
import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid,
    Tooltip, ResponsiveContainer, Line, ComposedChart,
    Cell
} from 'recharts';
import api from '../../api/axios.js';

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
                    barColor: savings >= 0 ? '#667eea' : '#e53e3e',
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
                    barColor: savings >= 0 ? '#667eea' : '#e53e3e',
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
            const changeColor = change > 0 ? '#48bb78' : (change < 0 ? '#e53e3e' : '#718096');
            const changeSign = change > 0 ? '+' : '';
            return (
                <div style={{
                    background: 'white',
                    padding: '12px 16px',
                    borderRadius: '8px',
                    boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
                    border: '1px solid #e2e8f0',
                    minWidth: '180px'
                }}>
                    <p style={{ margin: '0 0 8px 0', fontWeight: 600, color: '#2d3748' }}>
                        {label}
                    </p>
                    <p style={{ margin: '4px 0', color: '#667eea', fontWeight: 600, fontSize: '18px' }}>
                        {formatCurrency(savings)}
                    </p>
                    <p style={{ margin: '4px 0 0 0', color: changeColor, fontWeight: 500, fontSize: '13px' }}>
                        {changeSign}{formatCurrency(change)} <span style={{ color: '#a0aec0' }}>{changeLabel}</span>
                    </p>
                    {debt > 0 && (
                        <p style={{ margin: '8px 0 0 0', color: '#c53030', fontWeight: 600, fontSize: '14px' }}>
                            Долг по картам: {formatCurrency(debt)}
                        </p>
                    )}
                </div>
            );
        }
        return null;
    };

    if (loading) {
        return <div style={{ textAlign: 'center', padding: '40px', color: '#718096' }}>Загрузка графика...</div>;
    }

    if (!data || data.length === 0) {
        return (
            <div style={{ textAlign: 'center', padding: '40px', color: '#a0aec0' }}>
                Нет данных для отображения
            </div>
        );
    }

    const isYearView = viewMode === 'year';
    // Красные столбцы вниз показываем только тем, у кого есть кредитные карты
    const hasDebt = data.some(item => Number(item.debt || 0) > 0);

    return (
        <div>
            <div style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                marginBottom: '20px',
                flexWrap: 'wrap',
                gap: '10px'
            }}>
                <div>
                    <h3 style={{ margin: 0, color: '#2d3748' }}>💰 Накопления</h3>
                    <p style={{ margin: '4px 0 0 0', color: '#718096', fontSize: '14px' }}>
                        Общая сумма: <span style={{ fontWeight: 600, color: '#667eea' }}>
                            {formatCurrency(totalSavings)}
                        </span>
                        {totalDebt > 0 && (
                            <>
                                {' · '}Долг по картам: <span style={{ fontWeight: 600, color: '#c53030' }}>
                                    {formatCurrency(totalDebt)}
                                </span>
                            </>
                        )}
                    </p>
                </div>
                <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', alignItems: 'center' }}>
                    <div style={{ display: 'flex', gap: '4px', background: '#f1f4f9', borderRadius: '8px', padding: '4px' }}>
                        <button
                            onClick={() => handleViewModeChange('year')}
                            style={{
                                padding: '6px 16px',
                                border: 'none',
                                borderRadius: '6px',
                                background: viewMode === 'year' ? '#667eea' : 'transparent',
                                color: viewMode === 'year' ? 'white' : '#4a5568',
                                cursor: 'pointer',
                                fontWeight: 500,
                                fontSize: '13px',
                                transition: 'all 0.3s ease'
                            }}
                        >
                            Год
                        </button>
                        <button
                            onClick={() => handleViewModeChange('month')}
                            style={{
                                padding: '6px 16px',
                                border: 'none',
                                borderRadius: '6px',
                                background: viewMode === 'month' ? '#667eea' : 'transparent',
                                color: viewMode === 'month' ? 'white' : '#4a5568',
                                cursor: 'pointer',
                                fontWeight: 500,
                                fontSize: '13px',
                                transition: 'all 0.3s ease'
                            }}
                        >
                            Месяц
                        </button>
                    </div>

                    <select
                        value={selectedYear || ''}
                        onChange={(e) => handleYearChange(Number(e.target.value))}
                        style={{
                            padding: '6px 12px',
                            borderRadius: '8px',
                            border: '1px solid #e2e8f0',
                            background: 'white',
                            fontSize: '13px',
                            cursor: 'pointer'
                        }}
                    >
                        {years.map(year => (
                            <option key={year} value={year}>{year}</option>
                        ))}
                    </select>

                    {!isYearView && (
                        <select
                            value={selectedMonth || ''}
                            onChange={(e) => handleMonthChange(Number(e.target.value))}
                            style={{
                                padding: '6px 12px',
                                borderRadius: '8px',
                                border: '1px solid #e2e8f0',
                                background: 'white',
                                fontSize: '13px',
                                cursor: 'pointer'
                            }}
                        >
                            {monthNames.map((name, index) => (
                                <option key={index + 1} value={index + 1}>{name}</option>
                            ))}
                        </select>
                    )}
                </div>
            </div>

            <ResponsiveContainer width="100%" height={380}>
                {/* stackOffset="sign" — иначе стек копит сумму и долг откладывается
                    вниз от вершины синего столбца, а не от нулевой линии */}
                <ComposedChart data={data} stackOffset="sign"
                               margin={{ top: 20, right: 30, left: 20, bottom: 20 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis
                        dataKey={isYearView ? 'monthLabel' : 'date'}
                        stroke="#718096"
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
                                fill={entry.barColor || '#667eea'}
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
                            fill="#c53030"
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
                            stroke="#ecc94b"
                            strokeWidth={3}
                            dot={{ r: 4, fill: '#ecc94b', stroke: 'white', strokeWidth: 2 }}
                            activeDot={{ r: 7, fill: '#ecc94b', stroke: '#fff', strokeWidth: 3 }}
                        />
                    )}
                    <Line
                        type="monotone"
                        dataKey="savings"
                        name="Тренд"
                        stroke="#48bb78"
                        strokeWidth={3.5}
                        dot={(props) => {
                            const { cx, cy, payload } = props;
                            const isFuture = payload?.barOpacity !== undefined && payload.barOpacity < 0.5;
                            return (
                                <circle
                                    cx={cx}
                                    cy={cy}
                                    r={5}
                                    fill="#48bb78"
                                    opacity={isFuture ? 0.35 : 1}
                                    stroke="white"
                                    strokeWidth={2}
                                    style={{
                                        filter: 'drop-shadow(0 2px 6px rgba(72, 187, 120, 0.3))'
                                    }}
                                />
                            );
                        }}
                        activeDot={{
                            r: 9,
                            fill: '#48bb78',
                            stroke: '#fff',
                            strokeWidth: 3,
                            style: {
                                filter: 'drop-shadow(0 4px 12px rgba(72, 187, 120, 0.5))'
                            }
                        }}
                    />
                </ComposedChart>
            </ResponsiveContainer>

            <div style={{
                textAlign: 'center',
                marginTop: '12px',
                color: '#a0aec0',
                fontSize: '13px'
            }}>
                {isYearView
                    ? '📊 Накопления по месяцам'
                    : `📊 Дневная динамика за ${monthNames[selectedMonth - 1]} ${selectedYear}`
                }
                {!isYearView && selectedYear === currentYear && selectedMonth === currentMonth && (
                    <span style={{ marginLeft: '12px', color: '#667eea' }}>
                        🔵 Полупрозрачные столбцы — будущие дни
                    </span>
                )}
                {hasDebt && (
                    <span style={{ marginLeft: '12px', color: '#c53030' }}>
                        🔴 Столбцы вниз — задолженность по кредитным картам
                    </span>
                )}
            </div>
        </div>
    );
};

export default SavingsChart;