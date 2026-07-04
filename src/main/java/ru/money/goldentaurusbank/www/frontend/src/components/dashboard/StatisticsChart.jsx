// StatisticsChart.jsx - исправленный формат
import React, { useEffect, useState } from 'react';
import {
    BarChart, Bar, XAxis, YAxis, CartesianGrid,
    Tooltip, Legend, ResponsiveContainer, Line, ComposedChart
} from 'recharts';
import api from '../../api/axios.js';

const StatisticsChart = ({ refreshKey }) => {
    const [data, setData] = useState([]);
    const [years, setYears] = useState([]);
    const [selectedYear, setSelectedYear] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetchAvailableYears();
    }, []);

    useEffect(() => {
        if (selectedYear !== undefined) {
            fetchStatistics();
        }
    }, [selectedYear, refreshKey]);

    const fetchAvailableYears = async () => {
        try {
            const response = await api.get('/transactions/available-years');
            setYears(response.data);
            const currentYear = new Date().getFullYear();
            if (response.data.includes(currentYear)) {
                setSelectedYear(currentYear);
            } else if (response.data.length > 0) {
                setSelectedYear(response.data[response.data.length - 1]);
            } else {
                setSelectedYear(currentYear);
            }
        } catch (err) {
            console.error('Error fetching years:', err);
            setSelectedYear(new Date().getFullYear());
        }
    };

    const fetchStatistics = async () => {
        setLoading(true);
        try {
            const response = await api.get('/transactions/dashboard/statistics', {
                params: { year: selectedYear }
            });

            // Преобразуем данные с русскими названиями месяцев
            const monthNames = ['Янв', 'Фев', 'Мар', 'Апр', 'Май', 'Июн', 'Июл', 'Авг', 'Сен', 'Окт', 'Ноя', 'Дек'];
            const transformedData = (response.data.monthlyData || []).map(item => {
                const [year, month] = item.month.split('-');
                return {
                    ...item,
                    monthLabel: `${monthNames[parseInt(month) - 1]} ${year}`
                };
            });
            setData(transformedData);
        } catch (err) {
            console.error('Error fetching statistics:', err);
            setData([]);
        } finally {
            setLoading(false);
        }
    };

    const formatCurrency = (value) => {
        if (!value) return '0 ₽';
        return new Intl.NumberFormat('ru-RU', {
            style: 'currency',
            currency: 'RUB',
            minimumFractionDigits: 0,
            maximumFractionDigits: 0
        }).format(value);
    };

    const CustomTooltip = ({ active, payload, label }) => {
        if (active && payload && payload.length) {
            return (
                <div style={{
                    background: 'white',
                    padding: '12px 16px',
                    borderRadius: '8px',
                    boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
                    border: '1px solid #e2e8f0'
                }}>
                    <p style={{ margin: '0 0 8px 0', fontWeight: 600, color: '#2d3748' }}>
                        {label}
                    </p>
                    <p style={{ margin: '4px 0', color: '#48bb78' }}>
                        📈 Доход: {formatCurrency(payload[0]?.value || 0)}
                    </p>
                    <p style={{ margin: '4px 0', color: '#e53e3e' }}>
                        📉 Расход: {formatCurrency(payload[1]?.value || 0)}
                    </p>
                    <p style={{ margin: '4px 0', color: '#667eea', fontWeight: 600 }}>
                        💰 Изменение: {formatCurrency(payload[2]?.value || 0)}
                    </p>
                </div>
            );
        }
        return null;
    };

    if (loading) {
        return <div style={{ textAlign: 'center', padding: '40px' }}>Загрузка графика...</div>;
    }

    if (!data || data.length === 0) {
        return (
            <div style={{ textAlign: 'center', padding: '40px', color: '#a0aec0' }}>
                Нет данных для отображения
            </div>
        );
    }

    return (
        <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px', flexWrap: 'wrap', gap: '10px' }}>
                <h3 style={{ margin: 0 }}>📊 Динамика доходов и расходов</h3>
                <select
                    value={selectedYear || ''}
                    onChange={(e) => setSelectedYear(Number(e.target.value))}
                    style={{
                        padding: '8px 16px',
                        borderRadius: '8px',
                        border: '1px solid #e2e8f0',
                        background: 'white',
                        fontSize: '14px',
                        cursor: 'pointer'
                    }}
                >
                    {years.map(year => (
                        <option key={year} value={year}>{year}</option>
                    ))}
                </select>
            </div>

            <ResponsiveContainer width="100%" height={400}>
                <ComposedChart data={data} margin={{ top: 20, right: 30, left: 20, bottom: 20 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="#e2e8f0" />
                    <XAxis dataKey="monthLabel" stroke="#718096" />
                    <YAxis
                        tickFormatter={(value) => {
                            if (value >= 1000000) return `${(value / 1000000).toFixed(0)}M`;
                            if (value >= 1000) return `${(value / 1000).toFixed(0)}K`;
                            return value;
                        }}
                    />
                    <Tooltip content={<CustomTooltip />} />
                    <Legend />
                    <Bar dataKey="income" name="Доход" fill="#48bb78" radius={[4, 4, 0, 0]} />
                    <Bar dataKey="expense" name="Расход" fill="#e53e3e" radius={[4, 4, 0, 0]} />
                    <Line
                        type="monotone"
                        dataKey="netChange"
                        name="Изменение"
                        stroke="#667eea"
                        strokeWidth={3}
                        dot={{ r: 6 }}
                        activeDot={{ r: 8 }}
                    />
                </ComposedChart>
            </ResponsiveContainer>
        </div>
    );
};

export default StatisticsChart;