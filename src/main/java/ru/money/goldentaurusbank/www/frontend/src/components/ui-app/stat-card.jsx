import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

/**
 * Плитка со сводным числом. Раньше — `.stat-card` с тремя вариантами вёрстки
 * в пяти разных css-файлах.
 */
export function StatCard({ icon: Icon, label, value, hint, accent = false, className }) {
    return (
        <Card className={cn('gap-0 py-4', accent && 'border-primary/30 bg-primary/5', className)}>
            <CardContent className="flex flex-col gap-1 px-4">
                <div className="flex items-center gap-2 text-sm text-muted-foreground">
                    {Icon && <Icon className="size-4 shrink-0" />}
                    <span className="truncate">{label}</span>
                </div>
                <div
                    className={cn(
                        'text-2xl font-semibold tabular-nums',
                        accent && 'text-primary'
                    )}
                >
                    {value}
                </div>
                {hint && <div className="text-xs text-muted-foreground">{hint}</div>}
            </CardContent>
        </Card>
    )
}

/** Ряд плиток: одинаковая сетка на всех страницах. */
export function StatGrid({ children, className }) {
    return (
        <div className={cn('grid gap-4 sm:grid-cols-2 lg:grid-cols-4', className)}>
            {children}
        </div>
    )
}
