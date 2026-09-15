import { useState } from 'react'
import { Cell, Pie, PieChart, ResponsiveContainer, Sector } from 'recharts'
import { EmptyState } from '@/components/ui-app/page-state'
import { formatAmount } from '@/lib/format'
import { cn } from '@/lib/utils'
import { incomeTypeColor } from './incomeTypeColors'

/**
 * Доли типов дохода за период — тот же рисунок бублика, что у наименований
 * на дашборде (см. PieChartComponent), но цвет берётся не из справочника
 * пользователя, а из фиксированной палитры типов дохода.
 */
const IncomeDonut = ({ data, total, typeLabel }) => {
    const [activeIndex, setActiveIndex] = useState(null)

    if (!data || data.length === 0 || total <= 0) {
        return <EmptyState className="border-0" title="Нет данных для отображения" />
    }

    const toggle = (index) => setActiveIndex(activeIndex === index ? null : index)
    const percentage = (amount) => (total === 0 ? 0 : (amount / total) * 100)

    const RADIAN = Math.PI / 180
    const SELECTED_OFFSET = 10

    const offsetFor = (index, midAngle) => {
        const distance = activeIndex === index ? SELECTED_OFFSET : 0
        return {
            dx: distance * Math.cos(-midAngle * RADIAN),
            dy: distance * Math.sin(-midAngle * RADIAN),
        }
    }

    const renderSector = ({ isActive, ...props }) => {
        const { dx, dy } = offsetFor(props.index, props.midAngle)
        return <Sector {...props} cx={props.cx + dx} cy={props.cy + dy} />
    }

    // Подпись-процент только там, где кусок достаточно широкий, чтобы её вместить
    const renderPercentLabel = ({ cx, cy, midAngle, innerRadius, outerRadius, percent, index }) => {
        if (percent * 100 <= 5) return null
        const radius = innerRadius + (outerRadius - innerRadius) * 0.55
        const { dx, dy } = offsetFor(index, midAngle)
        const x = cx + dx + radius * Math.cos(-midAngle * RADIAN)
        const y = cy + dy + radius * Math.sin(-midAngle * RADIAN)
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
        <div className="flex flex-col items-center gap-6 sm:flex-row sm:items-start sm:justify-center">
            <div className="relative h-[260px] w-full max-w-[280px] shrink-0">
                <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                        <Pie
                            data={data}
                            dataKey="amount"
                            nameKey="type"
                            cx="50%"
                            cy="50%"
                            innerRadius={50}
                            outerRadius={100}
                            paddingAngle={1}
                            startAngle={90}
                            endAngle={-270}
                            label={renderPercentLabel}
                            labelLine={false}
                            shape={renderSector}
                            isAnimationActive={false}
                            onClick={(_, index) => toggle(index)}
                        >
                            {data.map((item, index) => (
                                <Cell
                                    key={item.type}
                                    fill={incomeTypeColor(item.type)}
                                    stroke="var(--background)"
                                    strokeWidth={2}
                                    opacity={activeIndex === null || activeIndex === index ? 1 : 0.35}
                                    className="cursor-pointer outline-none transition-all"
                                />
                            ))}
                        </Pie>
                    </PieChart>
                </ResponsiveContainer>
                <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                    <span className="text-sm font-semibold tabular-nums">{formatAmount(total)}</span>
                    <span className="text-xs text-muted-foreground">Всего ₽</span>
                </div>
            </div>

            {/* Легенда — она же табличное представление тех же чисел */}
            <div className="grid w-full max-w-md gap-2 sm:grid-cols-1">
                {data.map((item, index) => {
                    const isActive = activeIndex === index
                    return (
                        <button
                            key={item.type}
                            type="button"
                            onClick={() => toggle(index)}
                            className={cn(
                                'flex items-center gap-2 rounded-md border px-3 py-1.5 text-left text-sm transition-colors',
                                isActive ? 'border-primary bg-primary/10' : 'border-transparent bg-muted hover:bg-accent'
                            )}
                        >
                            <span
                                className="size-3 shrink-0 rounded-sm"
                                style={{ backgroundColor: incomeTypeColor(item.type) }}
                            />
                            <span className={cn('min-w-0 flex-1 truncate', isActive ? 'font-semibold text-primary' : 'font-medium')}>
                                {typeLabel(item.type)}
                            </span>
                            <span className="shrink-0 tabular-nums">{formatAmount(item.amount)} ₽</span>
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

export default IncomeDonut
