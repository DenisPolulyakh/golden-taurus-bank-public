import { useState, useEffect, useCallback, useMemo } from 'react'
import { useParams, useNavigate, useLocation } from 'react-router-dom'
import { Coins, CreditCard, Minus, Pencil, PiggyBank, Plus, Repeat, Trash2, TrendingUp, Wallet } from 'lucide-react'
import api from '@/api/axios'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { ButtonGroup } from '@/components/ui/button-group'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { PageContainer, PageHeader } from '@/components/ui-app/page-header'
import { SearchInput } from '@/components/ui-app/search-input'
import { SortButton } from '@/components/ui-app/data-table'
import { EmptyState, ErrorMessage, PageLoading } from '@/components/ui-app/page-state'
import { StatCard, StatGrid } from '@/components/ui-app/stat-card'
import { formatAmount, formatDate } from '@/lib/format'
import BullionModal from './BullionModal'
import BullionTypeStamp from './BullionTypeStamp'
import { useBudgetBullionId } from '@/components/hooks/useBudgetBullion'
import BullionTransactionModal from './BullionTransactionModal'
import CreditCardOperationModal from '../credit-cards/CreditCardOperationModal'
import { isEmptyBullionAmount, notifyAmountChange } from './bullionAmount'

function VaultBullionsPage() {
    // Галочку «Трата бюджета» показываем только у бюджетного слитка
    const budgetBullionId = useBudgetBullionId();
    const { vaultId } = useParams();
    const navigate = useNavigate();
    const location = useLocation();

    const from = location.state?.from || 'bullions';

    const [vault, setVault] = useState(null);
    const [vaultSummary, setVaultSummary] = useState(null);
    const [allVaults, setAllVaults] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [modalOpen, setModalOpen] = useState(false);
    const [editingBullion, setEditingBullion] = useState(null);
    const [sortConfig, setSortConfig] = useState({ field: 'amount', order: 'desc' });
    const [actionLoading, setActionLoading] = useState(false);

    const [transactionModal, setTransactionModal] = useState({
        isOpen: false,
        type: null,
        bullion: null,
        fromVaultId: null,
        dateOperation: null
    });

    const [transferModal, setTransferModal] = useState({
        isOpen: false,
        fromBullion: null,
        fromAmount: 0,
        fromBullionId: null,
        fromBullionNameId: null,
        dateOperation: null
    });

    const [transferTargets, setTransferTargets] = useState([]);
    const [allBullions, setAllBullions] = useState([]);

    // Погашение долга карты из накопителя: одна кнопка — две операции
    const [repayModal, setRepayModal] = useState({ isOpen: false, bullion: null, card: null });

    const fetchVault = useCallback(async () => {
        try {
            const response = await api.get(`/vaults/${vaultId}`);
            const data = response.data.data;
            setVault(data);
        } catch (err) {
            console.error('Ошибка загрузки хранилища:', err);
            setError('Хранилище не найдено');
        }
    }, [vaultId]);

    const fetchVaultSummary = useCallback(async () => {
        try {
            setLoading(true);
            const response = await api.get(`/vaults/${vaultId}/summary`);
            const summaryData = response.data.data;
            setVaultSummary(summaryData);
            setError('');
        } catch (err) {
            console.error('Ошибка загрузки сводки хранилища:', err);
            setError('Не удалось загрузить данные о слитках');
        } finally {
            setLoading(false);
        }
    }, [vaultId]);

    const fetchAllVaults = useCallback(async () => {
        try {
            const response = await api.get('/vaults?sortBy=name&sortOrder=asc&size=100');
            setAllVaults(response.data.data.content || []);
        } catch (err) {
            console.error('Ошибка загрузки хранилищ:', err);
        }
    }, []);

    const fetchAllBullions = useCallback(async () => {
        try {
            const response = await api.get('/bullions');
            setAllBullions(response.data.data || []);
        } catch (err) {
            console.error('Ошибка загрузки всех слитков:', err);
        }
    }, []);

    const getTransferTargets = useCallback(() => {
        const targets = [];
        const fromBullionId = transferModal.fromBullionId;
        allBullions.forEach(bullion => {
            // Получатель должен разрешать и перевод, и внесение
            const isAllowed = bullion.vault?.allowedTransferIn !== false;
            if (bullion.id !== fromBullionId && isAllowed) {
                targets.push({
                    id: bullion.id,
                    bullionNameTitle: bullion.bullionName?.title || 'Без наименования',
                    vaultName: bullion.vault?.name || 'Без хранилища',
                    amount: bullion.amount || 0,
                    allowedTransferIn: bullion.vault?.allowedTransferIn
                });
            }
        });
        return targets;
    }, [allBullions, transferModal.fromBullionId]);

    const getFromBullions = useCallback(() => {
        if (!vaultSummary?.bullions) return [];
        return vaultSummary.bullions.map(bullion => ({
            id: bullion.id,
            bullionNameTitle: bullion.bullionNameTitle,
            vaultName: vault?.name || 'Текущее хранилище',
            amount: bullion.amount || 0,
            allowedTransferOut: vault?.allowedTransferOut
        }));
    }, [vaultSummary, vault]);

    const filteredAndSortedBullions = useMemo(() => {
        if (!vaultSummary?.bullions) return [];

        let filtered = [...vaultSummary.bullions];

        if (searchTerm.trim()) {
            filtered = filtered.filter(item =>
                item.bullionNameTitle?.toLowerCase().includes(searchTerm.toLowerCase())
            );
        }

        filtered.sort((a, b) => {
            let aVal = sortConfig.field === 'amount' ? a.amount : (a.bullionNameTitle || '');
            let bVal = sortConfig.field === 'amount' ? b.amount : (b.bullionNameTitle || '');

            if (typeof aVal === 'string') {
                aVal = aVal.toLowerCase();
                bVal = bVal.toLowerCase();
            }

            if (sortConfig.order === 'asc') {
                return aVal > bVal ? 1 : -1;
            } else {
                return aVal < bVal ? 1 : -1;
            }
        });

        return filtered;
    }, [vaultSummary, searchTerm, sortConfig]);

    useEffect(() => {
        fetchVault();
        fetchVaultSummary();
        fetchAllVaults();
        fetchAllBullions();
    }, [fetchVault, fetchVaultSummary, fetchAllVaults, fetchAllBullions]);

    useEffect(() => {
        if (transferModal.isOpen) {
            const targets = getTransferTargets();
            setTransferTargets(targets);
        }
    }, [transferModal.isOpen, getTransferTargets]);

    const handleGoBack = () => {
        if (from === 'vaults') {
            navigate('/vaults');
        } else {
            navigate('/bullions');
        }
    };

    const handleAddBullion = () => {
        setEditingBullion(null);
        setModalOpen(true);
    };

    const handleEditBullion = (bullion) => {
        setEditingBullion({
            id: bullion.id,
            bullionNameId: bullion.bullionNameId,
            bullionNameTitle: bullion.bullionNameTitle,
            amount: bullion.amount,
            description: bullion.description || '',
            bullionType: bullion.bullionType || 'DEBIT'
        });
        setModalOpen(true);
    };

    const handleOpenRefill = (bullion) => {
        setTransactionModal({
            isOpen: true,
            type: 'refill',
            bullion: bullion,
            fromVaultId: parseInt(vaultId),
            dateOperation: null
        });
    };

    const handleOpenWithdraw = (bullion) => {
        setTransactionModal({
            isOpen: true,
            type: 'withdraw',
            bullion: bullion,
            fromVaultId: parseInt(vaultId),
            dateOperation: null
        });
    };

    const handleOpenDelete = (bullion) => {
        setTransactionModal({
            isOpen: true,
            type: 'delete',
            bullion: bullion,
            fromVaultId: parseInt(vaultId),
            dateOperation: null
        });
    };

    const handleOpenTransfer = (bullion) => {
        setTransferModal({
            isOpen: true,
            fromBullion: bullion,
            fromAmount: bullion.amount,
            fromBullionId: bullion.id,
            fromBullionNameId: bullion.bullionNameId,
            dateOperation: null
        });
    };

    const handleSelectFromBullion = (bullionId, amount) => {
        setTransferModal(prev => ({
            ...prev,
            fromBullionId: bullionId,
            fromAmount: amount
        }));
    };

    // Карта нужна модалке целиком (остаток лимита, долг), а сводка хранилища
    // знает про неё только маску и долг
    const handleOpenRepayCard = async (bullion) => {
        try {
            const response = await api.get(`/credit-cards/${bullion.creditCardId}`);
            setRepayModal({ isOpen: true, bullion, card: response.data.data });
        } catch (err) {
            console.error('Ошибка загрузки кредитной карты:', err);
        }
    };

    const handleRepayCard = async ({ amount, comment, dateOperation }) => {
        setActionLoading(true);
        try {
            await api.post('/credit-cards/repay-from-bullion', {
                bullionId: repayModal.bullion.id,
                amount: Number(amount),
                comment: comment || null,
                dateOperation: dateOperation || null
            });
            setRepayModal({ isOpen: false, bullion: null, card: null });
            await refreshData();
        } catch (err) {
            console.error('Ошибка погашения по карте:', err);
            throw err;
        } finally {
            setActionLoading(false);
        }
    };

    const handleRefill = async (amount, userComment, selectedVaultId, dateOperation, budgetOperation) => {
        setActionLoading(true);
        try {
            await api.post('/bullions/refill', {
                bullionNameId: transactionModal.bullion.bullionNameId,
                vaultId: parseInt(vaultId),
                amount: amount,
                userComment: userComment,
                dateOperation: dateOperation ? dateOperation : null,
                budgetOperation: budgetOperation
            });
            closeTransactionModal();
            await refreshData();
        } catch (err) {
            console.error('Ошибка пополнения слитка:', err);
            throw err;
        } finally {
            setActionLoading(false);
        }
    };

    const handleWithdraw = async (amount, userComment, selectedVaultId, dateOperation, budgetOperation) => {
        setActionLoading(true);
        try {
            await api.post('/bullions/withdraw', {
                bullionNameId: transactionModal.bullion.bullionNameId,
                vaultId: parseInt(vaultId),
                amount: amount,
                userComment: userComment,
                dateOperation: dateOperation ? dateOperation : null,
                budgetOperation: budgetOperation
            });
            closeTransactionModal();
            await refreshData();
        } catch (err) {
            console.error('Ошибка списания со слитка:', err);
            throw err;
        } finally {
            setActionLoading(false);
        }
    };

    const handleDeleteWithTransfer = async (targetVaultId, toLiquidityVault, description, dateOperation) => {
        setActionLoading(true);
        try {
            if (isEmptyBullionAmount(transactionModal.bullion?.amount)) {
                // Пустой слиток уходит в архив обычным DELETE: переносить нечего,
                // и операции в истории не появляется
                await api.delete(`/bullions/${transactionModal.bullion.id}`);
            } else {
                await api.delete(`/bullions/${transactionModal.bullion.id}/transfer`, {
                    data: {
                        fromVaultId: transactionModal.fromVaultId,
                        toVaultId: targetVaultId,
                        toLiquidityVault: toLiquidityVault,
                        description: description,
                        dateOperation: dateOperation ? dateOperation : null
                    }
                });
            }
            closeTransactionModal();
            await refreshData();
        } catch (err) {
            console.error('Ошибка удаления слитка с переносом:', err);
            throw err;
        } finally {
            setActionLoading(false);
        }
    };

    const handleTransfer = async (amount, toBullionId, comment, dateOperation, budgetOperation) => {
        try {
            await api.post('/bullions/transfer', {
                fromBullionId: transferModal.fromBullionId,
                toBullionId: toBullionId,
                amount: amount,
                comment: comment,
                dateOperation: dateOperation ? dateOperation : null,
                budgetOperation: budgetOperation
            });
            closeTransferModal();
            await refreshData();
        } catch (err) {
            console.error('Ошибка перевода:', err);
            throw err;
        }
    };

    const refreshData = useCallback(async () => {
        await Promise.all([fetchVaultSummary(), fetchVault(), fetchAllVaults(), fetchAllBullions()]);
    }, [fetchVaultSummary, fetchVault, fetchAllVaults, fetchAllBullions]);

    const handleSaveBullion = async (bullionNameId, vaultIdParam, amount, description, dateOperation, bullionType) => {
        setActionLoading(true);
        try {
            await api.post('/bullions', {
                bullionNameId,
                vaultId: vaultIdParam,
                amount,
                description,
                dateOperation: dateOperation ? dateOperation : null,
                bullionType
            });
            closeModal();
            await refreshData();
        } catch (err) {
            console.error('Ошибка сохранения слитка:', err);
            throw err;
        } finally {
            setActionLoading(false);
        }
    };

    const handleUpdateBullion = async (bullionId, bullionNameId, vaultIdParam, amount, description, dateOperation, userComment, bullionType) => {
        setActionLoading(true);
        // Сумму до правки знает только страница: ответ PUT про проведённую операцию молчит
        const previousAmount = editingBullion?.amount;
        try {
            await api.put(`/bullions/${bullionId}`, {
                bullionNameId,
                vaultId: vaultIdParam,
                amount,
                description,
                dateOperation: dateOperation ? dateOperation : null,
                userComment: userComment ?? null,
                bullionType
            });
            closeModal();
            notifyAmountChange(previousAmount, amount);
            await refreshData();
        } catch (err) {
            console.error('Ошибка обновления слитка:', err);
            throw err;
        } finally {
            setActionLoading(false);
        }
    };

    const handleSort = (field) => {
        setSortConfig(prev => ({
            field,
            order: prev.field === field && prev.order === 'asc' ? 'desc' : 'asc'
        }));
    };

    const closeModal = () => {
        setModalOpen(false);
        setEditingBullion(null);
    };

    const closeTransactionModal = () => {
        setTransactionModal({
            isOpen: false,
            type: null,
            bullion: null,
            fromVaultId: null,
            dateOperation: null
        });
    };

    const closeTransferModal = () => {
        setTransferModal({
            isOpen: false,
            fromBullion: null,
            fromAmount: 0,
            fromBullionId: null,
            fromBullionNameId: null,
            dateOperation: null
        });
    };

    // Перенос остатка - это внесение, поэтому только хранилища с «Можно вносить».
    // Ликвидный резерв доступен всегда - он отдельным переключателем в модалке
    const getAvailableVaultsForDelete = useCallback(() => {
        return allVaults.filter(vault => vault.id !== parseInt(vaultId) && vault.allowedIncome !== false);
    }, [allVaults, vaultId]);

    if (loading) {
        return (
            <PageContainer>
                <PageLoading />
            </PageContainer>
        )
    }

    if (!vault) {
        return (
            <PageContainer>
                <ErrorMessage>Хранилище не найдено</ErrorMessage>
                <div>
                    <Button variant="outline" onClick={handleGoBack}>
                        Назад
                    </Button>
                </div>
            </PageContainer>
        )
    }

    const totalAmount = vaultSummary?.totalAmount || 0;
    const bullionsCount = filteredAndSortedBullions.length;

    // Используем флаги из vault
    const {
        allowedIncome = true,
        allowedExpense = true,
        allowedTransferOut = true,
        allowedChangeAmount = true
    } = vault;

    const transactionConfig = {
        refill: {
            title: `Пополнение слитка "${transactionModal.bullion?.bullionNameTitle}"`,
            buttonText: 'Внести',
            handler: handleRefill,
            showVaultSelector: false,
            showAmount: true,
            showDescription: true,
            vaultSelectorLabel: "Хранилище",
            descriptionLabel: "📝 Комментарий",
            descriptionPlaceholder: "Комментарий к пополнению (необязательно)...",
            type: 'refill'
        },
        withdraw: {
            title: `Списание со слитка "${transactionModal.bullion?.bullionNameTitle}"`,
            buttonText: 'Снять',
            handler: handleWithdraw,
            showVaultSelector: false,
            showAmount: true,
            showDescription: true,
            vaultSelectorLabel: "Хранилище",
            descriptionLabel: "📝 Комментарий",
            descriptionPlaceholder: "Комментарий к списанию (необязательно)...",
            type: 'withdraw'
        },
        delete: isEmptyBullionAmount(transactionModal.bullion?.amount)
            ? {
                title: `Удаление пустого слитка "${transactionModal.bullion?.bullionNameTitle}"`,
                buttonText: 'Удалить',
                handler: handleDeleteWithTransfer,
                showVaultSelector: false,
                showAmount: false,
                // Комментарий и дату писать некуда: операции не создаётся
                showDescription: false,
                showDateOperation: false,
                notice: 'Слиток пустой: переносить нечего, в историю операций удаление не попадёт.',
                isConfirm: false,
                type: 'delete'
            }
            : {
                title: `Удаление слитка "${transactionModal.bullion?.bullionNameTitle}"`,
                buttonText: 'Удалить и перенести',
                handler: handleDeleteWithTransfer,
                showVaultSelector: true,
                showAmount: false,
                showDescription: true,
                vaultSelectorLabel: "Хранилище для переноса остатков",
                descriptionLabel: "📝 Комментарий",
                descriptionPlaceholder: "Комментарий к удалению (необязательно)...",
                isConfirm: false,
                vaults: getAvailableVaultsForDelete(),
                initialVaultId: null,
                type: 'delete'
            }
    };

    const currentTransaction = transactionModal.type ? transactionConfig[transactionModal.type] : null;

    return (
        <PageContainer>
            <PageHeader title={vault.name} onBack={handleGoBack}>
                <Button
                    onClick={handleAddBullion}
                    disabled={actionLoading || !allowedIncome}
                    title={!allowedIncome ? 'Хранилище заблокировано' : ''}
                >
                    <Plus />
                    Добавить слиток
                </Button>
            </PageHeader>

            <StatGrid className="lg:grid-cols-3">
                <StatCard
                    icon={Wallet}
                    label="Общая сумма в хранилище"
                    value={`${formatAmount(totalAmount)} ₽`}
                    tone="brand"
                />
                <StatCard
                    icon={Coins}
                    label="Количество наименований"
                    value={bullionsCount}
                    tone="info"
                />
                <StatCard
                    icon={TrendingUp}
                    label="Процентная ставка"
                    value={`${vault.interestRate || 0}%`}
                    tone="success"
                />
            </StatGrid>

            <div className="flex flex-wrap items-center justify-between gap-3">
                <SearchInput
                    value={searchTerm}
                    onChange={setSearchTerm}
                    placeholder="Поиск по наименованию..."
                />
                <div className="flex flex-wrap items-center gap-2">
                    <SortButton
                        label="По сумме"
                        field="amount"
                        sortField={sortConfig.field}
                        sortOrder={sortConfig.order}
                        onSort={handleSort}
                    />
                    <SortButton
                        label="По наименованию"
                        field="bullionNameTitle"
                        sortField={sortConfig.field}
                        sortOrder={sortConfig.order}
                        onSort={handleSort}
                    />
                </div>
            </div>

            <ErrorMessage>{error}</ErrorMessage>

            {filteredAndSortedBullions.length === 0 ? (
                <EmptyState
                    icon={Coins}
                    title={searchTerm ? 'Ничего не найдено' : 'Нет слитков в этом хранилище'}
                    description={searchTerm ? 'Попробуйте изменить запрос' : 'Добавьте первый слиток'}
                />
            ) : (
                <div className="grid gap-4 lg:grid-cols-2">
                    {filteredAndSortedBullions.map((bullion) => (
                        <BullionCard
                            key={bullion.id}
                            bullion={bullion}
                            onEdit={() => handleEditBullion(bullion)}
                            onDelete={() => handleOpenDelete(bullion)}
                            onRefill={() => handleOpenRefill(bullion)}
                            onWithdraw={() => handleOpenWithdraw(bullion)}
                            onTransfer={() => handleOpenTransfer(bullion)}
                            onRepayCard={() => handleOpenRepayCard(bullion)}
                            disabled={actionLoading}
                            vaultFlags={{
                                allowedIncome,
                                allowedExpense,
                                allowedTransferOut,
                            }}
                        />
                    ))}
                </div>
            )}

            <BullionModal
                isOpen={modalOpen}
                onClose={closeModal}
                onSave={editingBullion
                    ? (bullionNameId, vaultIdParam, amount, description, dateOperation, userComment, bullionType) =>
                        handleUpdateBullion(editingBullion.id, bullionNameId, vaultIdParam, amount, description, dateOperation, userComment, bullionType)
                    : (bullionNameId, vaultIdParam, amount, description, dateOperation, userComment, bullionType) =>
                        handleSaveBullion(bullionNameId, vaultIdParam, amount, description, dateOperation, bullionType)
                }
                initialBullionNameId={editingBullion?.bullionNameId}
                initialBullionNameTitle={editingBullion?.bullionNameTitle}
                initialVaultId={parseInt(vaultId)}
                initialAmount={editingBullion?.amount}
                initialDescription={editingBullion?.description}
                initialDateOperation={editingBullion?.dateOperation}
                initialBullionType={editingBullion?.bullionType}
                isEditing={!!editingBullion}
                allowedChangeAmount={allowedChangeAmount}
                allowedIncome={allowedIncome}
                allowedExpense={allowedExpense}
            />

            {currentTransaction && transactionModal.bullion && (
                <BullionTransactionModal
                    isOpen={transactionModal.isOpen}
                    onClose={closeTransactionModal}
                    onSave={currentTransaction.handler}
                    title={currentTransaction.title}
                    buttonText={currentTransaction.buttonText}
                    bullionName={transactionModal.bullion?.bullionNameTitle}
                    showVaultSelector={currentTransaction.showVaultSelector}
                    vaults={currentTransaction.vaults || []}
                    initialVaultId={currentTransaction.initialVaultId}
                    initialAmount={currentTransaction.showAmount ? '' : undefined}
                    initialDescription={currentTransaction.showDescription ? '' : undefined}
                    showAmount={currentTransaction.showAmount}
                    showDescription={currentTransaction.showDescription}
                    isConfirm={currentTransaction.isConfirm || false}
                    confirmMessage={currentTransaction.confirmMessage}
                    descriptionPlaceholder={currentTransaction.descriptionPlaceholder}
                    descriptionLabel={currentTransaction.descriptionLabel}
                    vaultSelectorLabel={currentTransaction.vaultSelectorLabel}
                    type={currentTransaction.type || null}
                    showDateOperation={currentTransaction.showDateOperation !== false}
                    notice={currentTransaction.notice}
                    showBudgetOperation={
                        ['refill', 'withdraw'].includes(currentTransaction.type) &&
                        transactionModal.bullion?.budget === true
                    }
                    initialDateOperation={transactionModal.dateOperation}
                    availableAmount={transactionModal.bullion?.amount}
                />
            )}

            {repayModal.isOpen && (
                <CreditCardOperationModal
                    isOpen={repayModal.isOpen}
                    card={repayModal.card}
                    bullion={repayModal.bullion}
                    type="repay-bullion"
                    onClose={() => setRepayModal({ isOpen: false, bullion: null, card: null })}
                    onSave={handleRepayCard}
                />
            )}

            {transferModal.isOpen && (
                <BullionTransactionModal
                    isOpen={transferModal.isOpen}
                    onClose={closeTransferModal}
                    onSave={handleTransfer}
                    title="Перевод между слитками"
                    buttonText="Перевести"
                    bullionName=""
                    type="transfer"
                    maxTransferAmount={transferModal.fromAmount}
                    transferTargets={transferTargets}
                    fromBullionId={transferModal.fromBullionId}
                    showAmount={true}
                    showDescription={true}
                    descriptionLabel="📝 Комментарий"
                    descriptionPlaceholder="Комментарий к переводу (необязательно)..."
                    fromBullions={getFromBullions()}
                    onSelectFromBullion={handleSelectFromBullion}
                    selectedFromBullionId={transferModal.fromBullionId}
                    showBudgetOperation={
                        budgetBullionId != null &&
                        (transferModal.fromBullionId === budgetBullionId ||
                            transferTargets.some((target) => target.id === budgetBullionId))
                    }
                    initialDateOperation={transferModal.dateOperation}
                />
            )}
        </PageContainer>
    )
}

const BullionCard = ({ bullion, onEdit, onDelete, onRefill, onWithdraw, onTransfer, onRepayCard, disabled, vaultFlags }) => {
    const {
        allowedIncome = true,
        allowedExpense = true,
        allowedTransferOut = true,
    } = vaultFlags || {}

    // Кнопки всегда видны, но disabled если флаг false или общий disabled.
    // Правка и удаление слитка галочками не ограничены: галочки про движение денег
    const isDisabled = (flag) => disabled || !flag

    return (
        <Card className="gap-4 transition-shadow hover:shadow-md">
            <CardHeader>
                <CardTitle className="text-base">{bullion.bullionNameTitle}</CardTitle>
                <CardAction>
                    <BullionTypeStamp type={bullion.bullionType} />
                </CardAction>
            </CardHeader>

            <CardContent className="flex flex-col gap-2 text-sm">
                <div className="flex items-center gap-2">
                    <span className="text-muted-foreground">Сумма</span>
                    <span className="text-lg font-semibold tabular-nums">
                        {formatAmount(bullion.amount)} ₽
                    </span>
                </div>

                {bullion.budget && (
                    <Badge
                        variant="outline"
                        className="w-fit"
                        title="По этому слитку считается отчёт «Бюджет на месяц»"
                    >
                        <PiggyBank />
                        Бюджет
                    </Badge>
                )}

                {bullion.creditCardId && (
                    <Badge
                        variant="secondary"
                        className="w-fit"
                        title="Слиток копит деньги на погашение этой карты"
                    >
                        <CreditCard />
                        Накопитель карты {bullion.creditCardMasked}
                    </Badge>
                )}

                {bullion.description && (
                    <div className="flex justify-between gap-3">
                        <span className="text-muted-foreground">Описание</span>
                        <span className="text-right">{bullion.description}</span>
                    </div>
                )}

                {bullion.createdAt && (
                    <div className="flex justify-between gap-3">
                        <span className="text-muted-foreground">Дата создания</span>
                        <span>{formatDate(bullion.createdAt)}</span>
                    </div>
                )}
                {bullion.updatedAt && bullion.updatedAt !== bullion.createdAt && (
                    <div className="flex justify-between gap-3">
                        <span className="text-muted-foreground">Последнее изменение</span>
                        <span>{formatDate(bullion.updatedAt)}</span>
                    </div>
                )}
            </CardContent>

            <CardContent>
                <ButtonGroup className="flex-wrap">
                    <Button
                        className="text-success hover:text-success"
                        variant="outline"
                        size="sm"
                        onClick={onRefill}
                        disabled={isDisabled(allowedIncome)}
                    >
                        <Plus />
                        Внести
                    </Button>
                    <Button
                        className="text-warning hover:text-warning"
                        variant="outline"
                        size="sm"
                        onClick={onWithdraw}
                        disabled={isDisabled(allowedExpense)}
                    >
                        <Minus />
                        Снять
                    </Button>
                    <Button variant="outline" size="sm" onClick={onEdit} disabled={disabled}>
                        <Pencil />
                        Редактировать
                    </Button>
                    {/* Перевод отсюда - это снятие: нет галочки «Можно снимать», нет и перевода */}
                    <Button
                        className="text-primary hover:text-primary"
                        variant="outline"
                        size="sm"
                        onClick={onTransfer}
                        disabled={isDisabled(allowedTransferOut)}
                    >
                        <Repeat />
                        Перевод
                    </Button>
                    {/* Погашение — тоже снятие со слитка, поэтому и оно под галочкой */}
                    {bullion.creditCardId && (
                        <Button
                            className="text-success hover:text-success"
                            variant="outline"
                            size="sm"
                            onClick={onRepayCard}
                            disabled={isDisabled(allowedExpense) || !(Number(bullion.creditCardDebt) > 0)}
                            title={
                                Number(bullion.creditCardDebt) > 0
                                    ? 'Списать со слитка и погасить долг карты'
                                    : 'Задолженность по карте уже нулевая'
                            }
                        >
                            <CreditCard />
                            Погашение
                        </Button>
                    )}
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

export default VaultBullionsPage
