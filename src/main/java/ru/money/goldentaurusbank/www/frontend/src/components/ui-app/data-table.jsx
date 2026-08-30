import { ArrowDown, ArrowUp, ArrowUpDown, ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Pagination, PaginationContent, PaginationItem } from '@/components/ui/pagination'
import { TableHead } from '@/components/ui/table'
import { cn } from '@/lib/utils'

/**
 * Заголовок таблицы, по которому сортируют. Раньше — `.sortable-header` со
 * стрелкой в отдельном `<span class="sort-indicator">`.
 */
export function SortableHead({ label, field, sortField, sortOrder, onSort, className, children }) {
    const active = sortField === field
    const Icon = !active ? ArrowUpDown : sortOrder === 'asc' ? ArrowUp : ArrowDown

    return (
        <TableHead className={cn('p-0', className)}>
            <Button
                variant="ghost"
                size="sm"
                onClick={() => onSort(field)}
                className={cn('h-9 w-full justify-start gap-1.5 px-3 font-medium', active && 'text-foreground')}
            >
                {label || children}
                <Icon className={cn('size-3.5', active ? 'opacity-100' : 'opacity-40')} />
            </Button>
        </TableHead>
    )
}

/**
 * Кнопка сортировки вне таблицы — для экранов с карточками, где раньше был
 * ряд `.sort-btn`.
 */
export function SortButton({ label, field, sortField, sortOrder, onSort }) {
    const active = sortField === field
    const Icon = !active ? ArrowUpDown : sortOrder === 'asc' ? ArrowUp : ArrowDown

    return (
        <Button variant={active ? 'secondary' : 'outline'} size="sm" onClick={() => onSort(field)}>
            {label}
            <Icon className={cn('size-3.5', active ? 'opacity-100' : 'opacity-40')} />
        </Button>
    )
}

/**
 * Постраничная навигация. `page` — с единицы, как показывается пользователю.
 * Ничего не рисует, пока страница одна: так же вело себя прежнее `.pagination`.
 */
export function TablePager({ page, totalPages, onPageChange, total, totalLabel }) {
    if (totalPages <= 1) {
        return total != null && totalLabel ? (
            <p className="text-sm text-muted-foreground">
                {totalLabel} {total}
            </p>
        ) : null
    }

    return (
        <div className="flex flex-wrap items-center justify-between gap-3">
            {total != null && totalLabel ? (
                <p className="text-sm text-muted-foreground">
                    {totalLabel} {total}
                </p>
            ) : (
                <span />
            )}
            <Pagination className="mx-0 w-auto justify-end">
                <PaginationContent>
                    <PaginationItem>
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => onPageChange(page - 1)}
                            disabled={page <= 1}
                        >
                            <ChevronLeft />
                            Назад
                        </Button>
                    </PaginationItem>
                    <PaginationItem>
                        <span className="px-3 text-sm text-muted-foreground tabular-nums">
                            {page} из {totalPages}
                        </span>
                    </PaginationItem>
                    <PaginationItem>
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => onPageChange(page + 1)}
                            disabled={page >= totalPages}
                        >
                            Вперёд
                            <ChevronRight />
                        </Button>
                    </PaginationItem>
                </PaginationContent>
            </Pagination>
        </div>
    )
}
