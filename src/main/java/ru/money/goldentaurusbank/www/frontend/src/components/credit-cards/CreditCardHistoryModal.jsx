import { useState, useEffect, useCallback } from 'react'
import { toast } from 'sonner'
import { Undo2 } from 'lucide-react'
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
import { InfoDialog } from '@/components/ui-app/form-dialog'
import { EmptyState } from '@/components/ui-app/page-state'
import { useConfirm } from '@/components/ui-app/confirm-dialog'
import { formatAmount, formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'

const OPERATION_LABELS = {
    SPEND: 'Списание',
    REPAY: 'Погашение',
    OPENING_DEBT: 'Начальный долг',
}

function CreditCardHistoryModal({ isOpen, card, onClose, onRolledBack }) {
    const [operations, setOperations] = useState([])
    const [loading, setLoading] = useState(true)
    const [rollbackId, setRollbackId] = useState(null)
    const { confirm, confirmDialog } = useConfirm()

    const fetchHistory = useCallback(async () => {
        setLoading(true)
        try {
            const response = await api.get(`/credit-cards/${card.id}/history`)
            setOperations(response.data.data || [])
        } catch (err) {
            console.error('Ошибка загрузки истории карты:', err)
        } finally {
            setLoading(false)
        }
    }, [card.id])

    useEffect(() => {
        if (isOpen) fetchHistory()
    }, [isOpen, fetchHistory])

    const handleRollback = (operation) => {
        const extra = operation.bullionTransactionId
            ? ' Деньги вернутся в слиток-накопитель.'
            : ''

        confirm({
            title: 'Откат операции',
            description: `Откатить операцию на ${formatAmount(operation.amount)} ₽?${extra}`,
            confirmText: 'Откатить',
            onConfirm: async () => {
                setRollbackId(operation.id)
                try {
                    await api.post(`/credit-cards/history/${operation.id}/rollback`)
                    toast.success('Операция откачена')
                    await fetchHistory()
                    if (onRolledBack) onRolledBack()
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
                title={`История карты «${card.name}»`}
            >
                {loading ? (
                    <div className="flex flex-col gap-2">
                        {Array.from({ length: 4 }).map((_, i) => (
                            <Skeleton key={i} className="h-10" />
                        ))}
                    </div>
                ) : operations.length === 0 ? (
                    <EmptyState className="border-0" title="По этой карте ещё не было операций" />
                ) : (
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead>Дата</TableHead>
                                <TableHead className="w-full">Операция</TableHead>
                                <TableHead className="text-right">Сумма</TableHead>
                                <TableHead className="text-right">Долг после</TableHead>
                                <TableHead />
                            </TableRow>
                        </TableHeader>
                        <TableBody>
                            {operations.map((operation) => (
                                <TableRow
                                    key={operation.id}
                                    className={cn(operation.reversalOfId && 'text-muted-foreground')}
                                >
                                    <TableCell className="whitespace-nowrap">
                                        {formatDateTime(operation.dateOperation)}
                                    </TableCell>
                                    <TableCell className="w-full whitespace-normal">
                                        <div>
                                            {OPERATION_LABELS[operation.operation] || operation.operation}
                                        </div>
                                        {operation.comment && (
                                            <div className="max-w-xs break-words text-xs text-muted-foreground">
                                                {operation.comment}
                                            </div>
                                        )}
                                    </TableCell>
                                    <TableCell
                                        className={cn(
                                            'text-right tabular-nums',
                                            Number(operation.signedAmount) > 0
                                                ? 'text-destructive'
                                                : 'text-success'
                                        )}
                                    >
                                        {Number(operation.signedAmount) > 0 ? '+' : '−'}
                                        {formatAmount(operation.amount)} ₽
                                    </TableCell>
                                    <TableCell className="text-right tabular-nums">
                                        {formatAmount(operation.debtAfter)} ₽
                                    </TableCell>
                                    <TableCell className="w-0 text-right">
                                        {operation.canRollback ? (
                                            <Button
                                                variant="ghost"
                                                size="sm"
                                                onClick={() => handleRollback(operation)}
                                                disabled={rollbackId === operation.id}
                                            >
                                                <Undo2 />
                                                Откатить
                                            </Button>
                                        ) : (
                                            <Badge variant="outline">
                                                {operation.reversalOfId ? 'откат' : 'откачена'}
                                            </Badge>
                                        )}
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                )}
            </InfoDialog>

            {confirmDialog}
        </>
    )
}

export default CreditCardHistoryModal
