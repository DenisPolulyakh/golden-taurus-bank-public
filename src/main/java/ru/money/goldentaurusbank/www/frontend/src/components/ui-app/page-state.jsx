import { Empty, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'
import { Skeleton } from '@/components/ui/skeleton'
import { Spinner } from '@/components/ui/spinner'
import { cn } from '@/lib/utils'

/** Заглушка на время загрузки страницы вместо голого «Загрузка...». */
export function PageLoading({ rows = 3 }) {
    return (
        <div className="flex flex-col gap-4" aria-busy="true">
            <Skeleton className="h-9 w-64" />
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                {Array.from({ length: 4 }).map((_, i) => (
                    <Skeleton key={i} className="h-24" />
                ))}
            </div>
            {Array.from({ length: rows }).map((_, i) => (
                <Skeleton key={i} className="h-16" />
            ))}
        </div>
    )
}

/** Полноэкранная загрузка — используется, пока восстанавливается сессия. */
export function FullScreenLoading({ children = 'Загрузка...' }) {
    return (
        <div className="flex min-h-screen flex-col items-center justify-center gap-3 text-muted-foreground">
            <Spinner className="size-8" />
            <p>{children}</p>
        </div>
    )
}

/** Пусто: ни одной записи или ничего не нашлось по поиску. */
export function EmptyState({ icon: Icon, title, description, children, className }) {
    return (
        <Empty className={cn('border', className)}>
            <EmptyHeader>
                {Icon && (
                    <EmptyMedia variant="icon" className="bg-primary/10 text-primary">
                        <Icon />
                    </EmptyMedia>
                )}
                <EmptyTitle>{title}</EmptyTitle>
                {description && <EmptyDescription>{description}</EmptyDescription>}
            </EmptyHeader>
            {children}
        </Empty>
    )
}

/** Красная плашка с ошибкой — бывший `.error-message`. */
export function ErrorMessage({ children, className }) {
    if (!children) return null
    return (
        <div
            role="alert"
            className={cn(
                'rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive',
                className
            )}
        >
            {children}
        </div>
    )
}
