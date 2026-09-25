import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { CreditCard, Undo2 } from 'lucide-react'
import api from '@/api/axios'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { InfoDialog } from '@/components/ui-app/form-dialog'
import { EmptyState } from '@/components/ui-app/page-state'
import { SearchInput } from '@/components/ui-app/search-input'
import { TablePager } from '@/components/ui-app/data-table'
import { useConfirm } from '@/components/ui-app/confirm-dialog'
import { DescriptionSegments } from '@/components/history/DescriptionSegments'
import { IncomeTypeSelect } from '@/components/history/IncomeTypeSelect'
import { KIND_BADGE_VARIANT, getKindLabel } from '@/components/history/transactionKinds'
import { formatAmount, formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'

const PAGE_SIZE = 10

function BullionHistoryModal({ isOpen, bullion, incomeTypes, onClose, onRolledBack }) {
    const [operations, setOperations] = useState([])
    const [loading, setLoading] = useState(true)
    const [page, setPage] = useState(1)
    const [totalPages, setTotalPages] = useState(0)
    const [totalElements, setTotalElements] = useState(0)
    const [searchInput, setSearchInput] = useState('')
    const [search, setSearch] = useState('')
    const [rollbackId, setRollbackId] = useState(null)
    const { confirm, confirmDialog } = useConfirm()

    useEffect(() => {
        const timer = setTimeout(() => {
            setSearch(searchInput.trim())
            setPage(1)
        }, 300)
        return () => clearTimeout(timer)
    }, [searchInput])

    const fetchHistory = useCallback(async () => {
        setLoading(true)
        try {
            const response = await api.get(`/bullions/${bullion.id}/history`, {
                params: { page, size: PAGE_SIZE, search: search || undefined },
            })
            const data = response.data.data
            setOperations(data.content || [])
            setTotalPages(data.totalPages || 0)
            setTotalElements(data.totalElements || 0)
        } catch (err) {
            console.error('Ошибка загрузки истории слитка:', err)
        } finally {
            setLoading(false)
        }
    }, [bullion.id, page, search])

    useEffect(() => {
        if (isOpen) fetchHistory()
    }, [isOpen, fetchHistory])

    const handleIncomeTypeChanged = (id, incomeType) =>
        setOperations((prev) => prev.map((op) => (op.id === id ? { ...op, incomeType } : op)))

    const handleRollback = (op) => {
        confirm({
            title: 'Откат операции',
            description: `Откатить операцию на ${formatAmount(op.amount)} ₽?`,
            confirmText: 'Откатить',
            onConfirm: async () => {
                setRollbackId(op.id)
                try {
                    await api.post(`/transactions/${op.id}/rollback`)
                    toast.success('Операция откачена')
                    await fetchHistory()
                    onRolledBack?.()
                } catch (err) {
                    console.error('Ошибка отката операции:', err)
                } finally {
                    setRollbackId(null)
                }
            },
        })
    }

    return (
        <>
            <InfoDialog
                open={isOpen}
                onOpenChange={(open) => !open && onClose()}
                title={`История слитка «${bullion.bullionNameTitle}»`}
                className="sm:max-w-3xl"
            >
                <TooltipProvider>
                    <div className="flex flex-col gap-3">
                        <SearchInput
                            value={searchInput}
                            onChange={setSearchInput}
                            placeholder="Поиск по комментарию..."
                        />

                        {loading ? (
                            <div className="flex flex-col gap-2">
                                {Array.from({ length: 4 }).map((_, i) => (
                                    <Skeleton key={i} className="h-10" />
                                ))}
                            </div>
                        ) : operations.length === 0 ? (
                            <EmptyState
                                className="border-0"
                                title={search ? 'Ничего не найдено' : 'По этому слитку ещё не было операций'}
                            />
                        ) : (
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>Дата</TableHead>
                                        <TableHead className="w-full">Операция</TableHead>
                                        <TableHead className="text-right">Сумма</TableHead>
                                        <TableHead className="text-right">Остаток после</TableHead>
                                        <TableHead />
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {operations.map((op) => {
                                        const incoming = op.targetBullionId === bullion.id
                                        const amountClass =
                                            op.kind === 'OPENING_BALANCE'
                                                ? 'text-muted-foreground'
                                                : incoming
                                                    ? 'text-success'
                                                    : 'text-destructive'

                                        return (
                                            <TableRow key={op.id}>
                                                <TableCell className="whitespace-nowrap">
                                                    {formatDateTime(op.dateOperation)}
                                                </TableCell>
                                                <TableCell className="w-full whitespace-normal">
                                                    <div className="flex flex-wrap items-center gap-1.5">
                                                        <Badge variant={KIND_BADGE_VARIANT[op.kind] || 'outline'}>
                                                            {getKindLabel(op.kind)}
                                                        </Badge>
                                                        {op.reversalOfId && (
                                                            <Badge
                                                                variant="outline"
                                                                title={`Откат операции #${op.reversalOfId}`}
                                                            >
                                                                откат
                                                            </Badge>
                                                        )}
                                                        <IncomeTypeSelect
                                                            transaction={op}
                                                            incomeTypes={incomeTypes}
                                                            onChanged={(incomeType) =>
                                                                handleIncomeTypeChanged(op.id, incomeType)
                                                            }
                                                        />
                                                    </div>
                                                    {op.kind === 'TRANSFER' && (
                                                        <div className="mt-1 max-w-md break-words text-xs">
                                                            <DescriptionSegments transaction={op} />
                                                        </div>
                                                    )}
                                                    {op.comment && (
                                                        <div className="max-w-md break-words text-xs text-muted-foreground">
                                                            {op.comment}
                                                        </div>
                                                    )}
                                                </TableCell>
                                                <TableCell className={cn('text-right tabular-nums', amountClass)}>
                                                    {incoming ? '+' : '−'}
                                                    {formatAmount(op.amount)} ₽
                                                </TableCell>
                                                <TableCell className="text-right tabular-nums">
                                                    {formatAmount(op.balanceAfter)} ₽
                                                </TableCell>
                                                <TableCell className="w-0 text-right">
                                                    {op.canRollback ? (
                                                        <Button
                                                            variant="ghost"
                                                            size="sm"
                                                            onClick={() => handleRollback(op)}
                                                            disabled={rollbackId === op.id}
                                                        >
                                                            <Undo2 />
                                                            Откатить
                                                        </Button>
                                                    ) : op.lockedByCard ? (
                                                        <Tooltip>
                                                            <TooltipTrigger asChild>
                                                                <Badge variant="outline">
                                                                    <CreditCard />
                                                                    откат из карты
                                                                </Badge>
                                                            </TooltipTrigger>
                                                            <TooltipContent>
                                                                Операция входит в погашение по кредитной карте —
                                                                откатывайте её из истории карты
                                                            </TooltipContent>
                                                        </Tooltip>
                                                    ) : (
                                                        <Badge variant="outline">откачена</Badge>
                                                    )}
                                                </TableCell>
                                            </TableRow>
                                        )
                                    })}
                                </TableBody>
                            </Table>
                        )}

                        <TablePager
                            page={page}
                            totalPages={totalPages}
                            onPageChange={setPage}
                            total={totalElements}
                            totalLabel="Всего операций:"
                        />
                    </div>
                </TooltipProvider>
            </InfoDialog>

            {confirmDialog}
        </>
    )
}

export default BullionHistoryModal
