import { ArrowLeft } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * Шапка внутренней страницы: кнопка «назад», заголовок и ряд действий справа.
 * Повторялась почти дословно на семи экранах.
 */
export function PageHeader({ title, description, onBack, children, className }) {
    return (
        <div className={cn('flex flex-wrap items-center justify-between gap-4', className)}>
            <div className="flex min-w-0 items-center gap-3">
                {onBack && (
                    <Button variant="ghost" size="icon" onClick={onBack} aria-label="Назад">
                        <ArrowLeft />
                    </Button>
                )}
                <div className="min-w-0">
                    <h1 className="truncate text-2xl font-semibold tracking-tight">{title}</h1>
                    {description && (
                        <p className="text-sm text-muted-foreground">{description}</p>
                    )}
                </div>
            </div>
            {children && <div className="flex flex-wrap items-center gap-2">{children}</div>}
        </div>
    )
}

/** Обёртка страницы: одинаковые отступы и предельная ширина. */
export function PageContainer({ children, className }) {
    return (
        <div className={cn('mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8', className)}>
            <div className="flex flex-col gap-6">{children}</div>
        </div>
    )
}
