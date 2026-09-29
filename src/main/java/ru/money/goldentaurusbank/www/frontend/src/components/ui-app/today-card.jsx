import { useEffect, useState } from 'react'
import { CalendarDays } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Progress } from '@/components/ui/progress'
import { pluralDays } from '@/lib/format'
import { cn } from '@/lib/utils'

function formatToday(date) {
    return date.toLocaleDateString('ru-RU', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
    })
}

function formatClock(date) {
    return date.toLocaleTimeString('ru-RU', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
    })
}

function monthInfo(date) {
    const daysInMonth = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
    const daysLeft = daysInMonth - date.getDate() + 1
    const passedPercent = ((date.getDate() - 1) / daysInMonth) * 100
    return { daysInMonth, daysLeft, passedPercent }
}

export function TodayCard({ className }) {
    const [now, setNow] = useState(() => new Date())

    useEffect(() => {
        const timerId = setInterval(() => setNow(new Date()), 1000)
        return () => clearInterval(timerId)
    }, [])

    const { daysInMonth, daysLeft, passedPercent } = monthInfo(now)

    return (
        <Card className={cn('gap-0 py-4 transition-shadow hover:shadow-md', className)}>
            <CardContent className="flex flex-col gap-4 px-4 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex items-start gap-3">
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-muted text-muted-foreground">
                        <CalendarDays className="size-5" />
                    </span>
                    <div className="flex min-w-0 flex-col gap-0.5">
                        <p className="text-sm text-muted-foreground first-letter:uppercase">
                            {formatToday(now)}
                        </p>
                        <p className="text-2xl font-semibold tabular-nums">{formatClock(now)}</p>
                    </div>
                </div>
                <div className="flex flex-col gap-1.5 sm:w-64">
                    <div className="flex items-baseline justify-between gap-3">
                        <span className="text-sm text-muted-foreground">До конца месяца</span>
                        <span className="text-lg font-semibold tabular-nums">{pluralDays(daysLeft)}</span>
                    </div>
                    <Progress value={passedPercent} aria-label="Прошедшая часть месяца" />
                    <span className="text-xs text-muted-foreground">{pluralDays(daysInMonth)} в месяце</span>
                </div>
            </CardContent>
        </Card>
    )
}
