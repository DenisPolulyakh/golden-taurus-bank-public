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
    const [viewMode, setViewMode] = useState('year');
    const [loading, setLoading] = useState(true);
    const [totalSavings, setTotalSavings] = useState(0);

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

            const total = response.data.totalIncome || 0;
            setTotalSavings(total);

            let monthlyData = response.data.monthlyData || [];

            if (monthlyData.length === 0) {
                for (let i = 0; i < 12; i++) {
                    monthlyData.push({
                        month: `${selectedYear}-${String(i + 1).padStart(2, '0')}`,
                        monthLabel: `${monthNames[i]} ${selectedYear}`,
                        income: 0,
                        expense: 0,
                        netChange: 0,
                        transactionCount: 0
                    });
                }
            }

            let cumulativeTotal = 0;
            const transformedData = monthlyData.map((item) => {
                const [year, month] = item.month.split('-');
                const monthlyChange = (item.income || 0) - (item.expense || 0);
                cumulativeTotal += monthlyChange;

                const monthNum = parseInt(month);
                // Для годового режима определяем, наступил ли месяц
                const isFuture = selectedYear === currentYear && monthNum > currentMonth;
                const isCurrentMonth = selectedYear === currentYear && monthNum === currentMonth;

                return {
                    ...item,
                    monthLabel: `${monthNames[parseInt(month) - 1]} ${year}`,
                    savings: Math.round(cumulativeTotal * 100) / 100,
                    monthNumber: parseInt(month),
                    barColor: cumulativeTotal >= 0 ? '#667eea' : '#e53e3e',
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

            const dailyData = response.data.dailyData || [];
            const total = response.data.totalAmount || 0;
            setTotalSavings(total);

            const isCurrentMonth = selectedYear === currentYear && selectedMonth === currentMonth;

            if (dailyData.length > 0) {
                const transformedData = dailyData.map((item) => {
                    const isFuture = isCurrentMonth && item.day > currentDay;
                    const isToday = isCurrentMonth && item.day === currentDay;

                    return {
                        ...item,
                        date: `${String(item.day).padStart(2, '0')}.${String(selectedMonth).padStart(2, '0')}`,
                        barColor: item.savings >= 0 ? '#667eea' : '#e53e3e',
                        barOpacity: isFuture ? 0.35 : (isToday ? 0.7 : 1)
                    };
                });
                setData(transformedData);
            } else {
                const daysInMonth = new Date(selectedYear, selectedMonth, 0).getDate();
                let emptyData = [];
                for (let day = 1; day <= daysInMonth; day++) {
                    const isFuture = isCurrentMonth && day > currentDay;
                    const isToday = isCurrentMonth && day === currentDay;

                    emptyData.push({
                        day: day,
                        date: `${String(day).padStart(2, '0')}.${String(selectedMonth).padStart(2, '0')}`,
                        savings: 0,
                        dailyChange: 0,
                        income: 0,
                        expense: 0,
                        transactionCount: 0,
                        barColor: '#667eea',
                        barOpacity: isFuture ? 0.35 : (isToday ? 0.7 : 1)
                    });
                }
                setData(emptyData);
            }
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
            const savings = payload[0]?.value || 0;
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
                <ComposedChart data={data} margin={{ top: 20, right: 30, left: 20, bottom: 20 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis
                        dataKey={isYearView ? 'monthLabel' : 'date'}
                        stroke="#718096"
                        interval={isYearView ? 0 : Math.floor(data.length / 15)}
                    />
                    <YAxis
                        tickFormatter={(value) => {
                            if (value >= 1000000) return `${(value / 1000000).toFixed(1)}M`;
                            if (value >= 1000) return `${(value / 1000).toFixed(0)}K`;
                            return value;
                        }}
                    />
                    <Tooltip content={<CustomTooltip />} />
                    <Bar
                        dataKey="savings"
                        name="Накопления"
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
            </div>
        </div>
    );
};

export default SavingsChart;