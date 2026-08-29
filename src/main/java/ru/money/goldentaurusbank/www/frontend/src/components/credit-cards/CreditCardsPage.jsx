import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { toast } from 'sonner'
import { History, Minus, Pencil, Plus, Trash2, Wallet } from 'lucide-react'
import api from '@/api/axios'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ButtonGroup } from '@/components/ui/button-group'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { PageContainer, PageHeader } from '@/components/ui-app/page-header'
import { SearchInput } from '@/components/ui-app/search-input'
import { SortButton } from '@/components/ui-app/data-table'
import { EmptyState, ErrorMessage, PageLoading } from '@/components/ui-app/page-state'
import { StatCard, StatGrid } from '@/components/ui-app/stat-card'
import { useConfirm } from '@/components/ui-app/confirm-dialog'
import { cn } from '@/lib/utils'
import CreditCardModal from './CreditCardModal'
import CreditCardOperationModal from './CreditCardOperationModal'
import CreditCardHistoryModal from './CreditCardHistoryModal'
import {
    formatAmount,
    formatSigned,
    formatDate,
    graceClass,
    graceText,
    limitUsage,
    usageClass,
    usageText,
} from './creditCardFormat'

function CreditCardsPage() {
    const [cards, setCards] = useState([]);
    const [totalDebt, setTotalDebt] = useState(0);
    const [totalLimit, setTotalLimit] = useState(0);
    const [count, setCount] = useState(0);
    const [searchTerm, setSearchTerm] = useState('');
    // По умолчанию — ближайший платёж: сверху карта, которую гасить раньше всех
    const [sortConfig, setSortConfig] = useState({ field: 'grace', order: 'asc' });
    const [loading, setLoading] = useState(true);
    const [actionLoading, setActionLoading] = useState(false);
    const [error, setError] = useState('');

    const [modalOpen, setModalOpen] = useState(false);
    const [editingCard, setEditingCard] = useState(null);
    const [operation, setOperation] = useState(null);
    const [historyCard, setHistoryCard] = useState(null);

    const navigate = useNavigate();
    const { confirm, confirmDialog } = useConfirm();
    const timeoutRef = useRef(null);
    const isInitialMount = useRef(true);

    const fetchCards = useCallback(async (search, sortBy, sortOrder) => {
        try {
            const params = new URLSearchParams();
            if (search) params.append('search', search);
            if (sortBy) params.append('sortBy', sortBy);
            if (sortOrder) params.append('sortOrder', sortOrder);

            const response = await api.get(`/credit-cards?${params.toString()}`);
            const data = response.data.data;
            setCards(data.cards || []);
            setTotalDebt(data.totalDebt || 0);
            setTotalLimit(data.totalLimit || 0);
            setCount(data.count || 0);
            setError('');
        } catch (err) {
            console.error('Ошибка загрузки кредитных карт:', err);
            setError('Не удалось загрузить кредитные карты');
        } finally {
            setLoading(false);
        }
    }, []);

    // Поиск и сортировка считаются на бэке, поэтому набор в поле не должен
    // дёргать сервер на каждую букву
    useEffect(() => {
        if (isInitialMount.current) {
            isInitialMount.current = false;
            fetchCards(searchTerm, sortConfig.field, sortConfig.order);
            return;
        }

        if (timeoutRef.current) clearTimeout(timeoutRef.current);
        timeoutRef.current = setTimeout(() => {
            fetchCards(searchTerm, sortConfig.field, sortConfig.order);
        }, 300);

        return () => {
            if (timeoutRef.current) clearTimeout(timeoutRef.current);
        };
    }, [searchTerm, sortConfig, fetchCards]);

    const reload = () => fetchCards(searchTerm, sortConfig.field, sortConfig.order);

    const handleSort = (field) => {
        // У дней интересен минимум, у денег — максимум, отсюда разное
        // направление при первом переключении на поле
        setSortConfig(prev => prev.field === field
            ? { field, order: prev.order === 'asc' ? 'desc' : 'asc' }
            : { field, order: field === 'grace' ? 'asc' : 'desc' });
    };

    const handleSaveCard = async (form) => {
        const payload = {
            name: form.name,
            last4: form.last4 || null,
            gracePeriodDate: form.gracePeriodDate || null,
            limit: form.limit === '' ? 0 : Number(form.limit),
            debt: form.debt === '' ? 0 : Number(form.debt),
            bullionIds: form.bullionIds
        };

        if (editingCard) {
            await api.put(`/credit-cards/${editingCard.id}`, payload);
        } else {
            await api.post('/credit-cards', payload);
        }

        setModalOpen(false);
        setEditingCard(null);
        await reload();
    };

    const handleDeleteCard = (card) => {
        confirm({
            title: 'Удаление карты',
            description: `Удалить карту "${card.name}"? История операций сохранится.`,
            onConfirm: async () => {
                setActionLoading(true);
                try {
                    await api.delete(`/credit-cards/${card.id}`);
                    toast.success('Кредитная карта удалена');
                    await reload();
                } catch (err) {
                    console.error('Ошибка удаления карты:', err);
                } finally {
                    setActionLoading(false);
                }
            },
        });
    };

    const handleOperation = async ({ amount, comment, dateOperation }) => {
        const url = operation.type === 'spend'
            ? `/credit-cards/${operation.card.id}/spend`
            : `/credit-cards/${operation.card.id}/repay`;

        await api.post(url, { amount: Number(amount), comment: comment || null, dateOperation: dateOperation || null });
        toast.success(operation.type === 'spend' ? 'Списание проведено' : 'Задолженность погашена');
        setOperation(null);
        await reload();
    };

    if (loading) {
        return (
            <PageContainer>
                <PageLoading />
            </PageContainer>
        )
    }

    const usage = limitUsage(totalDebt, totalLimit)

    return (
        <PageContainer>
            <PageHeader title="Кредитные карты" onBack={() => navigate('/dashboard')}>
                <Button
                    onClick={() => {
                        setEditingCard(null)
                        setModalOpen(true)
                    }}
                >
                    <Plus />
                    Добавить карту
                </Button>
            </PageHeader>

            <StatGrid className="lg:grid-cols-3">
                <StatCard label="Текущий долг" value={`${formatAmount(totalDebt)} ₽`} accent />
                <StatCard
                    label="Общий лимит"
                    value={`${formatAmount(totalLimit)} ₽`}
                    hint={
                        <span>
                            Лимит использован на:{' '}
                            <span className={cn('font-medium', usageClass(usage))}>
                                {usageText(usage)}
                            </span>
                        </span>
                    }
                />
                <StatCard label="Количество кредитных карт" value={count} />
            </StatGrid>

            <div className="flex flex-wrap items-center justify-between gap-3">
                <SearchInput
                    value={searchTerm}
                    onChange={setSearchTerm}
                    placeholder="Поиск по названию или последним 4 цифрам..."
                />
                <div className="flex flex-wrap items-center gap-2">
                    <SortButton
                        label="По остатку дней"
                        field="grace"
                        sortField={sortConfig.field}
                        sortOrder={sortConfig.order}
                        onSort={handleSort}
                    />
                    <SortButton
                        label="По задолженности"
                        field="debt"
                        sortField={sortConfig.field}
                        sortOrder={sortConfig.order}
                        onSort={handleSort}
                    />
                    <SortButton
                        label="По лимиту"
                        field="limit"
                        sortField={sortConfig.field}
                        sortOrder={sortConfig.order}
                        onSort={handleSort}
                    />
                    <SortButton
                        label="По остатку"
                        field="remainder"
                        sortField={sortConfig.field}
                        sortOrder={sortConfig.order}
                        onSort={handleSort}
                    />
                </div>
            </div>

            <ErrorMessage>{error}</ErrorMessage>

            {cards.length === 0 ? (
                <EmptyState
                    icon={Wallet}
                    title={searchTerm ? 'Ничего не найдено' : 'Нет кредитных карт'}
                    description={searchTerm ? 'Попробуйте изменить запрос' : 'Добавьте первую карту'}
                />
            ) : (
                <div className="grid gap-4 lg:grid-cols-2">
                    {cards.map((card) => (
                        <CreditCard
                            key={card.id}
                            card={card}
                            disabled={actionLoading}
                            onSpend={() => setOperation({ type: 'spend', card })}
                            onRepay={() => setOperation({ type: 'repay', card })}
                            onHistory={() => setHistoryCard(card)}
                            onEdit={() => {
                                setEditingCard(card)
                                setModalOpen(true)
                            }}
                            onDelete={() => handleDeleteCard(card)}
                        />
                    ))}
                </div>
            )}

            {modalOpen && (
                <CreditCardModal
                    isOpen={modalOpen}
                    card={editingCard}
                    onClose={() => {
                        setModalOpen(false)
                        setEditingCard(null)
                    }}
                    onSave={handleSaveCard}
                />
            )}

            {operation && (
                <CreditCardOperationModal
                    isOpen={!!operation}
                    card={operation.card}
                    type={operation.type}
                    onClose={() => setOperation(null)}
                    onSave={handleOperation}
                />
            )}

            {historyCard && (
                <CreditCardHistoryModal
                    isOpen={!!historyCard}
                    card={historyCard}
                    onClose={() => setHistoryCard(null)}
                    onRolledBack={reload}
                />
            )}

            {confirmDialog}
        </PageContainer>
    )
}

const CreditCard = ({ card, disabled, onSpend, onRepay, onHistory, onEdit, onDelete }) => {
    const accumulators = card.accumulators || []

    return (
        <Card className="gap-4">
            <CardHeader>
                <CardTitle className="text-base">{card.name}</CardTitle>
            </CardHeader>

            <CardContent className="flex flex-col gap-1.5 text-sm">
                <Row label="Номер карты" value={card.maskedNumber} />
                <Row
                    label="Ближайший платёж"
                    value={card.gracePeriodDate ? formatDate(card.gracePeriodDate) : '—'}
                />
                <Row
                    label="Осталось"
                    value={graceText(card.graceDaysLeft)}
                    valueClassName={graceClass(card.graceDaysLeft)}
                />
                <Row label="Лимит" value={`${formatAmount(card.limit)} ₽`} />
                <Row
                    label="Задолженность"
                    value={`${formatAmount(card.debt)} ₽`}
                    valueClassName="text-destructive"
                />
                <Row label="Остаток" value={`${formatAmount(card.remainder)} ₽`} />
                <Row
                    label="Дисбаланс"
                    value={`${formatSigned(card.imbalance)} ₽`}
                    valueClassName={
                        Number(card.imbalance || 0) < 0 ? 'text-destructive' : 'text-success'
                    }
                />

                <div className="flex flex-col gap-1 pt-1">
                    <span className="text-muted-foreground">Накопитель</span>
                    {accumulators.length === 0 ? (
                        <span className="text-muted-foreground">
                            Необходимо выбрать слиток для накопления
                        </span>
                    ) : (
                        <div className="flex flex-wrap gap-1.5">
                            {accumulators.map((item) => (
                                <Badge key={item.bullionId} variant="outline" asChild>
                                    <Link to={`/vaults/${item.vaultId}`}>
                                        <span
                                            style={
                                                item.bullionNameColor
                                                    ? { color: item.bullionNameColor }
                                                    : undefined
                                            }
                                        >
                                            {item.bullionNameTitle}
                                        </span>
                                        <span className="text-muted-foreground">
                                            | {item.vaultName}
                                        </span>
                                    </Link>
                                </Badge>
                            ))}
                        </div>
                    )}
                </div>
            </CardContent>

            <CardContent>
                <ButtonGroup className="flex-wrap">
                    <Button variant="outline" size="sm" onClick={onSpend} disabled={disabled}>
                        <Minus />
                        Списание
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={onRepay}
                        disabled={disabled || Number(card.debt) <= 0}
                    >
                        <Plus />
                        Погашение
                    </Button>
                    <Button variant="outline" size="sm" onClick={onHistory} disabled={disabled}>
                        <History />
                        История
                    </Button>
                    <Button variant="outline" size="sm" onClick={onEdit} disabled={disabled}>
                        <Pencil />
                        Редактировать
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        className="text-destructive hover:text-destructive"
                        onClick={onDelete}
                        disabled={disabled}
                    >
                        <Trash2 />
                        Удалить
                    </Button>
                </ButtonGroup>
            </CardContent>
        </Card>
    )
}

const Row = ({ label, value, valueClassName }) => (
    <div className="flex justify-between gap-3">
        <span className="text-muted-foreground">{label}</span>
        <span className={cn('text-right font-medium tabular-nums', valueClassName)}>{value}</span>
    </div>
)

export default CreditCardsPage
