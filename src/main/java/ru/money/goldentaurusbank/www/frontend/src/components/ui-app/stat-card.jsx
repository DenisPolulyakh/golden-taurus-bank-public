import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

// Тон плитки: подсветка иконки и самого числа. Раньше все плитки были
// одинаково серыми, и глазу не за что было зацепиться.
const TONES = {
    default: {
        chip: 'bg-muted text-muted-foreground',
        value: 'text-foreground',
        card: '',
    },
    brand: {
        chip: 'brand-gradient text-white',
        value: 'brand-text',
        card: 'border-primary/25 bg-primary/5',
    },
    primary: {
        chip: 'bg-primary/12 text-primary',
        value: 'text-primary',
        card: 'border-primary/20',
    },
    success: {
        chip: 'bg-success/12 text-success',
        value: 'text-success',
        card: 'border-success/20',
    },
    warning: {
        chip: 'bg-warning/12 text-warning',
        value: 'text-warning',
        card: 'border-warning/20',
    },
    destructive: {
        chip: 'bg-destructive/12 text-destructive',
        value: 'text-destructive',
        card: 'border-destructive/20',
    },
    info: {
        chip: 'bg-info/12 text-info',
        value: 'text-info',
        card: 'border-info/20',
    },
}

/**
 * Плитка со сводным числом. Раньше — `.stat-card` с тремя вариантами вёрстки
 * в пяти разных css-файлах.
 */
export function StatCard({ icon: Icon, label, value, hint, tone = 'default', accent = false, className }) {
    // accent остался ради вызовов «главной» плитки — это тот же фирменный тон
    const palette = TONES[accent ? 'brand' : tone] || TONES.default

    return (
        <Card className={cn('gap-0 py-4 transition-shadow hover:shadow-md', palette.card, className)}>
            <CardContent className="flex items-start gap-3 px-4">
                {Icon && (
                    <span
                        className={cn(
                            'flex size-10 shrink-0 items-center justify-center rounded-lg',
                            palette.chip
                        )}
                    >
                        <Icon className="size-5" />
                    </span>
                )}
                <div className="flex min-w-0 flex-col gap-0.5">
                    <span className="truncate text-sm text-muted-foreground">{label}</span>
                    <span className={cn('text-2xl font-semibold tabular-nums', palette.value)}>
                        {value}
                    </span>
                    {hint && <span className="text-xs text-muted-foreground">{hint}</span>}
                </div>
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
