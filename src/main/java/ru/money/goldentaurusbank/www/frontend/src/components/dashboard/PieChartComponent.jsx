import { useState } from 'react'
import { Cell, Pie, PieChart, ResponsiveContainer } from 'recharts'
import { EmptyState } from '@/components/ui-app/page-state'
import { cn } from '@/lib/utils'

/**
 * Распределение по наименованиям. Цвета берём не из палитры графиков, а из
 * самих наименований: у каждого свой цвет в справочнике.
 *
 * Раньше круг рисовался вручную — сотня строк математики с дугами и inline-стилями.
 */
const PieChartComponent = ({ data, formatAmount }) => {
    const [activeIndex, setActiveIndex] = useState(null)

    const total = data.reduce((sum, item) => sum + item.amount, 0)
    const percentage = (amount) => (total === 0 ? 0 : (amount / total) * 100)

    const hasData = data.length > 0 && data.some((item) => item.amount > 0)

    if (!hasData) {
        return <EmptyState className="border-0" title="Нет данных для отображения" />
    }

    const toggle = (index) => setActiveIndex(activeIndex === index ? null : index)

    // Процент пишем прямо в сектор, но только там, где он помещается:
    // на узких кусках подпись налезала бы на соседние
    const renderPercentLabel = ({ cx, cy, midAngle, innerRadius, outerRadius, percent, index }) => {
        if (percent * 100 <= 5) return null

        const radian = Math.PI / 180
        const radius = innerRadius + (outerRadius - innerRadius) * 0.55
        const x = cx + radius * Math.cos(-midAngle * radian)
        const y = cy + radius * Math.sin(-midAngle * radian)

        return (
            <text
                x={x}
                y={y}
                textAnchor="middle"
                dominantBaseline="central"
                fill="#fff"
                fontSize={12}
                fontWeight={600}
                opacity={activeIndex === null || activeIndex === index ? 1 : 0.35}
                // обводка цветом сектора: белые цифры на светлой заливке иначе теряются
                stroke="oklch(0 0 0 / 35%)"
                strokeWidth={2.5}
                paintOrder="stroke"
                className="pointer-events-none select-none"
            >
                {Math.round(percent * 100)}%
            </text>
        )
    }

    return (
        <div className="flex flex-col items-center gap-6">
            <div className="relative h-[300px] w-full max-w-[320px]">
                <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                        <Pie
                            data={data}
                            dataKey="amount"
                            nameKey="name"
                            cx="50%"
                            cy="50%"
                            innerRadius={55}
                            outerRadius={120}
                            paddingAngle={1}
                            startAngle={90}
                            endAngle={-270}
                            label={renderPercentLabel}
                            labelLine={false}
                            onClick={(_, index) => toggle(index)}
                        >
                            {data.map((item, index) => (
                                <Cell
                                    key={item.bullionNameId ?? index}
                                    fill={item.color || 'var(--muted-foreground)'}
                                    stroke="var(--background)"
                                    strokeWidth={2}
                                    opacity={activeIndex === null || activeIndex === index ? 1 : 0.35}
                                    className="cursor-pointer outline-none transition-opacity"
                                />
                            ))}
                        </Pie>
                    </PieChart>
                </ResponsiveContainer>

                {/* Итог в центре бублика: у recharts для этого нет своего слота */}
                <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                    <span className="text-sm font-semibold tabular-nums">
                        {formatAmount(total)}
                    </span>
                    <span className="text-xs text-muted-foreground">Всего ₽</span>
                </div>
            </div>

            <div className="grid w-full gap-2 sm:grid-cols-2 lg:grid-cols-3">
                {data.map((item, index) => {
                    const isActive = activeIndex === index
                    return (
                        <button
                            key={item.bullionNameId ?? index}
                            type="button"
                            onClick={() => toggle(index)}
                            className={cn(
                                'flex items-center gap-2 rounded-md border px-3 py-1.5 text-left text-sm transition-colors',
                                isActive
                                    ? 'border-primary bg-primary/10'
                                    : 'border-transparent bg-muted hover:bg-accent'
                            )}
                        >
                            <span
                                className="size-3 shrink-0 rounded-sm border"
                                style={{ backgroundColor: item.color || '#cccccc' }}
                            />
                            <span
                                className={cn(
                                    'min-w-0 flex-1 truncate',
                                    isActive ? 'font-semibold text-primary' : 'font-medium'
                                )}
                            >
                                {item.name}
                            </span>
                            <span className="shrink-0 tabular-nums">
                                {formatAmount(item.amount)} ₽
                            </span>
                            <span className="shrink-0 text-xs text-muted-foreground tabular-nums">
                                ({Math.round(percentage(item.amount))}%)
                            </span>
                        </button>
                    )
                })}
            </div>
        </div>
    )
}

export default PieChartComponent
