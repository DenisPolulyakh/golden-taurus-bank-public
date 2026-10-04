import { useState, useEffect, useCallback, useMemo, useRef } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { toast } from 'sonner'
import { CheckCheck, CreditCard as CreditCardIcon, Download, History, KeyRound, Lock, Minus, Pencil, Plus, Printer, ScrollText, Settings, Trash2, Wallet } from 'lucide-react'
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
import { useBullionSelection } from '@/components/hooks/useBullionSelection'
import { useArrivalHighlight } from '@/components/hooks/useArrivalHighlight'
import { cn } from '@/lib/utils'
import { buildSheetHtml } from '@/lib/vaultSheet'
import CreditCardModal from './CreditCardModal'
import VaultUnlockDialog from './VaultUnlockDialog'
import CardRequisitesDialog from './CardRequisitesDialog'
import VaultSettingsDialog from './VaultSettingsDialog'
import { CardVaultProvider, useCardVault } from './CardVaultProvider'
import CreditCardOperationModal from './CreditCardOperationModal'
import CreditCardHistoryModal from './CreditCardHistoryModal'
import CreditCardSelectionPanel from './CreditCardSelectionPanel'
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

const getCardId = (card) => card.id

function CreditCardsPage() {
    const [cards, setCards] = useState([]);
    const [knownCards, setKnownCards] = useState(() => new Map());
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
    const [requisitesCard, setRequisitesCard] = useState(null);
    const [vaultSettingsOpen, setVaultSettingsOpen] = useState(false);
    const vault = useCardVault();
    const knownCardList = useMemo(() => [...knownCards.values()], [knownCards]);
    const {
        selectedItems: selectedCards,
        isSelected,
        onCardClick,
        remove: removeSelected,
        clear: clearSelection,
        selectMany,
    } = useBullionSelection(knownCardList, getCardId);

    const navigate = useNavigate();
    const highlightedCardId = useArrivalHighlight('creditCardId', !loading, 'credit-card');
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
            setKnownCards((prev) => {
                const next = search ? new Map(prev) : new Map();
                for (const card of data.cards || []) next.set(card.id, card);
                return next;
            });
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
            paymentAmount: form.paymentAmount === '' || form.paymentAmount === null
                ? null
                : Number(form.paymentAmount),
            limit: form.limit === '' ? 0 : Number(form.limit),
            debt: form.debt === '' ? 0 : Number(form.debt),
            bullionIds: form.bullionIds
        };

        let cardId;
        if (editingCard) {
            await api.put(`/credit-cards/${editingCard.id}`, payload);
            cardId = editingCard.id;
        } else {
            const response = await api.post('/credit-cards', payload);
            cardId = response.data.data.id;
        }

        if (form.requisites) {
            try {
                await vault.saveCard({ ...form.requisites, cardId });
            } catch (err) {
                await reload();
                toast.error('Карта сохранена, а реквизиты — нет. Повторите сохранение');
                throw err;
            }
        }

        setModalOpen(false);
        setEditingCard(null);
        await reload();
    };

    const handleDownloadPackage = async () => {
        try {
            const response = await api.get('/card-requisites/emergency-package')
            const blob = new Blob([JSON.stringify(response.data.data, null, 2)], {
                type: 'application/json',
            })
            const url = URL.createObjectURL(blob)
            const link = document.createElement('a')

            link.href = url
            link.download = `taurus-package-${new Date().toISOString().slice(0, 10)}.json`
            link.click()
            URL.revokeObjectURL(url)

            toast.success('Аварийный пакет скачан')
        } catch (err) {
            console.error('Ошибка выгрузки пакета:', err)
        }
    }

    const handlePrintSheet = () => {
        const sheet = window.open('', '_blank')
        if (!sheet) {
            toast.error('Браузер заблокировал новую вкладку — разрешите всплывающие окна')
            return
        }

        sheet.document.write(buildSheetHtml({ cards, entries: vault.cards }))
        sheet.document.close()
        vault.touch()
    }

    const handleDeleteCard = (card) => {
        confirm({
            title: 'Удаление карты',
            description: `Удалить карту "${card.name}"? История операций сохранится.`,
            onConfirm: async () => {
                setActionLoading(true);
                try {
                    await api.delete(`/credit-cards/${card.id}`);
                    removeSelected(card.id);
                    setKnownCards((prev) => {
                        const next = new Map(prev);
                        next.delete(card.id);
                        return next;
                    });
                    if (vault.isOpen && vault.cardById(card.id)) {
                        await vault.dropCard(card.id);
                    }
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

    const handleLocateCard = (id) => {
        const element = document.getElementById(`credit-card-${id}`);
        if (!element) {
            toast.info('Карта скрыта поиском');
            return;
        }
        element.scrollIntoView({ behavior: 'smooth', block: 'center' });
    };

    if (loading) {
        return (
            <PageContainer>
                <PageLoading />
            </PageContainer>
        )
    }

    const usage = limitUsage(totalDebt, totalLimit)
    const cardsWithDebt = cards.filter((card) => Number(card.debt) > 0)

    return (
        <PageContainer>
            <PageHeader title="Кредитные карты" onBack={() => navigate('/dashboard')}>
                <Button
                    variant="outline"
                    onClick={() => (vault.isOpen ? vault.lock() : vault.requestUnlock())}
                    disabled={!vault.loaded}
                >
                    {vault.isOpen ? <Lock /> : <KeyRound />}
                    {vault.isOpen ? 'Запереть реквизиты' : 'Открыть реквизиты'}
                </Button>
                {vault.isOpen && (
                    <Button variant="outline" onClick={handlePrintSheet}>
                        <Printer />
                        Аварийный лист
                    </Button>
                )}
                <Button
                    variant="outline"
                    size="icon"
                    onClick={handleDownloadPackage}
                    aria-label="Скачать аварийный пакет"
                    title="Скачать аварийный пакет"
                >
                    <Download />
                </Button>
                {vault.isOpen && (
                    <Button
                        variant="outline"
                        size="icon"
                        onClick={() => setVaultSettingsOpen(true)}
                        aria-label="Настройки сундука"
                    >
                        <Settings />
                    </Button>
                )}
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
                <StatCard
                    icon={Minus}
                    label="Текущий долг"
                    value={`${formatAmount(totalDebt)} ₽`}
                    tone="destructive"
                />
                <StatCard
                    icon={Wallet}
                    label="Общий лимит"
                    value={`${formatAmount(totalLimit)} ₽`}
                    tone="brand"
                    hint={
                        <span>
                            Лимит использован на:{' '}
                            <span className={cn('font-medium', usageClass(usage))}>
                                {usageText(usage)}
                            </span>
                        </span>
                    }
                />
                <StatCard
                    icon={CreditCardIcon}
                    label="Количество кредитных карт"
                    value={count}
                    tone="info"
                />
            </StatGrid>

            <div className="flex flex-wrap items-center justify-between gap-3">
                <SearchInput
                    value={searchTerm}
                    onChange={setSearchTerm}
                    placeholder="Поиск по названию или последним 4 цифрам..."
                />
                <div className="flex flex-wrap items-center gap-2">
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => selectMany(cardsWithDebt.map(getCardId))}
                        disabled={cardsWithDebt.length === 0}
                    >
                        <CheckCheck />
                        Все с долгом
                    </Button>
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
                            selected={isSelected(card.id)}
                            highlighted={card.id === highlightedCardId}
                            onSelect={(event) => onCardClick(event, card.id)}
                            onSpend={() => setOperation({ type: 'spend', card })}
                            onRepay={() => setOperation({ type: 'repay', card })}
                            onHistory={() => setHistoryCard(card)}
                            onEdit={() => {
                                setEditingCard(card)
                                setModalOpen(true)
                            }}
                            onDelete={() => handleDeleteCard(card)}
                            hasRequisites={vault.isOpen && !!vault.cardById(card.id)}
                            vaultLocked={!vault.isOpen}
                            onRequisites={() =>
                                vault.isOpen ? setRequisitesCard(card) : vault.requestUnlock()
                            }
                        />
                    ))}
                </div>
            )}

            <CreditCardSelectionPanel
                cards={selectedCards}
                onRemove={removeSelected}
                onClear={clearSelection}
                onRepay={(card, amount) => setOperation({ type: 'repay', card, amount })}
                onLocate={handleLocateCard}
            />

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
                    initialAmount={operation.amount}
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

            <VaultUnlockDialog />

            <CardRequisitesDialog card={requisitesCard} onClose={() => setRequisitesCard(null)} />

            <VaultSettingsDialog open={vaultSettingsOpen} onOpenChange={setVaultSettingsOpen} />

            {confirmDialog}
        </PageContainer>
    )
}

const CreditCard = ({ card, highlighted, selected, onSelect, disabled, onSpend, onRepay, onHistory, onEdit, onDelete, hasRequisites, vaultLocked, onRequisites }) => {
    const accumulators = card.accumulators || []

    return (
        <Card
            id={`credit-card-${card.id}`}
            onClick={onSelect}
            className={cn('gap-4 transition-shadow hover:shadow-md', highlighted && 'ring-2 ring-primary', selected && 'border-primary bg-primary/10')}
        >
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
                {card.paymentAmount != null && (
                    <Row label="К внесению" value={`${formatAmount(card.paymentAmount)} ₽`} />
                )}
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
                                    <Link
                                        to={`/vaults/${item.vaultId}`}
                                        state={{ from: 'credit-cards', bullionId: item.bullionId, creditCardId: card.id }}
                                    >
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
                    <Button
                        className="text-warning hover:text-warning"
                        variant="outline"
                        size="sm"
                        onClick={onSpend}
                        disabled={disabled}
                    >
                        <Minus />
                        Списание
                    </Button>
                    <Button
                        className="text-success hover:text-success"
                        variant="outline"
                        size="sm"
                        onClick={onRepay}
                        disabled={disabled || Number(card.debt) <= 0}
                    >
                        <Plus />
                        Погашение
                    </Button>
                    <Button
                        className="text-info hover:text-info"
                        variant="outline"
                        size="sm"
                        onClick={onHistory}
                        disabled={disabled}
                    >
                        <History />
                        История
                    </Button>
                    {(hasRequisites || vaultLocked) && (
                        <Button variant="outline" size="sm" onClick={onRequisites} disabled={disabled}>
                            <ScrollText />
                            Реквизиты
                        </Button>
                    )}
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

export default function CreditCardsPageWithVault() {
    return (
        <CardVaultProvider>
            <CreditCardsPage />
        </CardVaultProvider>
    )
}
