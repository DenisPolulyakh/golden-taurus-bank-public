import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { ChevronDown, CreditCard, RefreshCw, SlidersHorizontal, Undo2 } from 'lucide-react'
import api from '@/api/axios'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select'
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { DatePicker } from '@/components/ui-app/date-picker'
import { PageContainer, PageHeader } from '@/components/ui-app/page-header'
import { TablePager } from '@/components/ui-app/data-table'
import { EmptyState, PageLoading } from '@/components/ui-app/page-state'
import { useConfirm } from '@/components/ui-app/confirm-dialog'
import { formatCurrency, formatDateTime } from '@/lib/format'
import { cn } from '@/lib/utils'

// Radix Select не умеет пустую строку значением пункта — «все типы» ездит сторожевым
const ALL_KINDS = 'all'

// Вид операции выводится бэкендом из того, какие ноги заполнены.
const KIND_LABELS = {
    DEPOSIT: 'Пополнение',
    WITHDRAWAL: 'Списание',
    TRANSFER: 'Перевод',
    OPENING_BALANCE: 'Начальный остаток',
}

// Перевод и начальный остаток накопления не двигают — красим их нейтрально.
const KIND_AMOUNT_CLASS = {
    DEPOSIT: 'text-success',
    WITHDRAWAL: 'text-destructive',
    TRANSFER: 'text-primary',
    OPENING_BALANCE: 'text-muted-foreground',
}

const KIND_BADGE_VARIANT = {
    DEPOSIT: 'secondary',
    WITHDRAWAL: 'secondary',
    TRANSFER: 'outline',
    OPENING_BALANCE: 'outline',
}

const TransactionHistoryPage = () => {
    const [transactions, setTransactions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [filters, setFilters] = useState({
        kind: '',
        fromDate: '',
        toDate: ''
    });
    const [showFilters, setShowFilters] = useState(false);

    const navigate = useNavigate();
    const { confirm, confirmDialog } = useConfirm();

    useEffect(() => {
        fetchTransactions();
    }, [page, filters]);

    const fetchTransactions = async () => {
        setLoading(true);
        try {
            const params = {
                page,
                size: 20,
            };

            if (filters.kind) params.kind = filters.kind;
            if (filters.fromDate) params.fromDate = filters.fromDate;
            if (filters.toDate) params.toDate = filters.toDate;

            const response = await api.get('/transactions/history', { params });
            setTransactions(response.data.content || []);
            setTotalPages(response.data.totalPages || 0);
            setTotalElements(response.data.totalElements || 0);
        } catch (err) {
            console.error('Error fetching transactions:', err);
        } finally {
            setLoading(false);
        }
    };

    const handleRollback = (id) => {
        confirm({
            title: 'Откат транзакции',
            description: 'Вы уверены, что хотите откатить эту транзакцию?',
            confirmText: 'Откатить',
            onConfirm: async () => {
                try {
                    await api.post(`/transactions/${id}/rollback`);
                    toast.success('Транзакция откачена');
                    fetchTransactions();
                } catch (err) {
                    console.error('Rollback failed:', err);
                }
            },
        });
    };

    const getKindLabel = (kind) => KIND_LABELS[kind] || kind || '—';

    const renderDescription = (tx) => {
        const segments = tx.descriptionSegments;
        if (Array.isArray(segments) && segments.length > 0) {
            // seg.archived — хранилища больше нет: цвет остаётся своим, но гаснет
            return segments.map((seg, i) => (
                <span
                    key={i}
                    className={seg.archived ? 'opacity-50 line-through' : undefined}
                    title={seg.archived ? 'Хранилище удалено' : undefined}
                    style={seg.color ? { color: seg.color, fontWeight: 600 } : undefined}
                >
                    {seg.text}
                </span>
            ));
        }
        return tx.description || tx.comment || '-';
    };

    if (loading && page === 0) {
        return (
            <PageContainer>
                <PageLoading />
            </PageContainer>
        )
    }

    return (
        <TooltipProvider>
            <PageContainer>
                <PageHeader title="История операций" onBack={() => navigate('/dashboard')}>
                    <Button
                        variant="outline"
                        onClick={() => {
                            setFilters({ kind: '', fromDate: '', toDate: '' })
                            setPage(0)
                        }}
                    >
                        Сбросить
                    </Button>
                    <Button variant="outline" onClick={fetchTransactions}>
                        <RefreshCw />
                        Обновить
                    </Button>
                </PageHeader>

                <Collapsible open={showFilters} onOpenChange={setShowFilters}>
                    <CollapsibleTrigger asChild>
                        <Button variant="ghost" size="sm">
                            <SlidersHorizontal />
                            Фильтры
                            <ChevronDown
                                className={cn('transition-transform', showFilters && 'rotate-180')}
                            />
                        </Button>
                    </CollapsibleTrigger>
                    <CollapsibleContent>
                        <div className="mt-3 flex flex-wrap items-center gap-3">
                            <Select
                                value={filters.kind || ALL_KINDS}
                                onValueChange={(value) => {
                                    setFilters({ ...filters, kind: value === ALL_KINDS ? '' : value })
                                    setPage(0)
                                }}
                            >
                                <SelectTrigger className="w-56">
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    <SelectItem value={ALL_KINDS}>Все типы</SelectItem>
                                    {Object.entries(KIND_LABELS).map(([value, label]) => (
                                        <SelectItem key={value} value={value}>
                                            {label}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>

                            <DatePicker
                                value={filters.fromDate}
                                onChange={(value) => {
                                    setFilters({ ...filters, fromDate: value })
                                    setPage(0)
                                }}
                                placeholder="Дата с"
                                clearable
                                className="w-auto"
                            />
                            <DatePicker
                                value={filters.toDate}
                                onChange={(value) => {
                                    setFilters({ ...filters, toDate: value })
                                    setPage(0)
                                }}
                                placeholder="Дата по"
                                clearable
                                className="w-auto"
                            />
                        </div>
                    </CollapsibleContent>
                </Collapsible>

                {transactions.length === 0 ? (
                    <EmptyState title="Нет транзакций" description="По выбранным фильтрам ничего не найдено" />
                ) : (
                    <Card className="overflow-hidden py-0">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <TableHead>Дата</TableHead>
                                    <TableHead>Тип</TableHead>
                                    <TableHead className="text-right">Сумма</TableHead>
                                    <TableHead>Описание</TableHead>
                                    <TableHead className="text-right">Действия</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {transactions.map((tx) => (
                                    <TableRow key={tx.id}>
                                        <TableCell className="whitespace-nowrap">
                                            {formatDateTime(tx.dateOperation)}
                                        </TableCell>
                                        <TableCell>
                                            <span className="flex flex-wrap items-center gap-1.5">
                                                <Badge variant={KIND_BADGE_VARIANT[tx.kind] || 'outline'}>
                                                    {getKindLabel(tx.kind)}
                                                </Badge>
                                                {tx.reversalOfId && (
                                                    <Badge
                                                        variant="outline"
                                                        title={`Откат операции #${tx.reversalOfId}`}
                                                    >
                                                        откат
                                                    </Badge>
                                                )}
                                            </span>
                                        </TableCell>
                                        <TableCell
                                            className={cn(
                                                'text-right font-medium tabular-nums',
                                                KIND_AMOUNT_CLASS[tx.kind]
                                            )}
                                        >
                                            {formatCurrency(tx.amount)}
                                        </TableCell>
                                        <TableCell className="max-w-md">
                                            {renderDescription(tx)}
                                        </TableCell>
                                        <TableCell className="text-right">
                                            {tx.canRollback ? (
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={() => handleRollback(tx.id)}
                                                >
                                                    <Undo2 />
                                                    Откатить
                                                </Button>
                                            ) : tx.lockedByCard ? (
                                                // Нога погашения по карте: откат только целиком, из истории карты
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
                                                <span className="text-sm text-muted-foreground">
                                                    Откачена
                                                </span>
                                            )}
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </Card>
                )}

                <TablePager
                    page={page + 1}
                    totalPages={totalPages}
                    onPageChange={(next) => setPage(next - 1)}
                    total={totalElements}
                    totalLabel="Всего транзакций:"
                />

                {confirmDialog}
            </PageContainer>
        </TooltipProvider>
    )
}

export default TransactionHistoryPage
