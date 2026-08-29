import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Coins, CreditCard, Minus, Plus, Repeat, Trash2, TrendingUp, Wallet } from 'lucide-react'
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
import { formatAmount } from '@/lib/format'
import BullionModal from './BullionModal'
import BullionTypeStamp from './BullionTypeStamp'
import BullionTransactionModal from './BullionTransactionModal'
import CreditCardOperationModal from '../credit-cards/CreditCardOperationModal'
import { notifyAmountChange } from './bullionAmount'

function BullionsPage() {
    const [groupedData, setGroupedData] = useState(null);
    const [bullionNameBullions, setBullionNameBullions] = useState([]);
    const [allVaults, setAllVaults] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [modalOpen, setModalOpen] = useState(false);
    const [editingBullion, setEditingBullion] = useState(null);
    const [sortField, setSortField] = useState('bullionNameAmount');
    const [sortOrder, setSortOrder] = useState('desc');

    // Погашение карты из накопителя. У наименования накопителей может быть
    // несколько (разные хранилища, а то и разные карты), поэтому слиток
    // выбирается уже внутри модалки, а карты грузим заранее — все, что нужны
    const [repayModal, setRepayModal] = useState({ isOpen: false, options: [], bullionId: null, cards: {} });

    const [transactionModal, setTransactionModal] = useState({
        isOpen: false,
        type: null,
        bullionNameId: null,
        bullionNameTitle: null,
        selectedVaultId: null,
        selectedVaultName: null,
        amount: null,
        bullionId: null,
        description: null,
        fromVaultId: null,
        dateOperation: null // 👈 ДОБАВИТЬ ПОЛЕ ДАТЫ
    });

    const navigate = useNavigate();

    useEffect(() => {
        fetchGroupedBullions();
        fetchAllVaults();
    }, []);

    const fetchGroupedBullions = async () => {
        try {
            const response = await api.get('/bullions/grouped');
            const data = response.data.data;
            setGroupedData(data);
            setBullionNameBullions(data.bullionNameBullionList || []);
        } catch (err) {
            console.error('Ошибка загрузки слитков:', err);
            setError('Не удалось загрузить слитки');
        } finally {
            setLoading(false);
        }
    };

    const fetchAllVaults = async () => {
        try {
            const response = await api.get('/vaults?sortBy=name&sortOrder=asc&size=100');
            setAllVaults(response.data.data.content || []);
        } catch (err) {
            console.error('Ошибка загрузки хранилищ:', err);
        }
    };

    const getFilteredAndSorted = useMemo(() => {
        let filtered = [...bullionNameBullions];

        if (searchTerm && searchTerm.trim()) {
            const searchLower = searchTerm.toLowerCase().trim();
            filtered = filtered.filter(bullionName => {
                const bullionNameMatch = bullionName.bullionNameTitle?.toLowerCase().includes(searchLower);
                const vaultMatch = bullionName.vaults?.some(vault =>
                    vault.name && vault.name.toLowerCase().includes(searchLower)
                );
                return bullionNameMatch || vaultMatch;
            });
        }

        filtered.sort((a, b) => {
            let aVal = sortField === 'bullionNameAmount' ? (a.bullionNameAmount || 0) : (a.bullionNameTitle || '');
            let bVal = sortField === 'bullionNameAmount' ? (b.bullionNameAmount || 0) : (b.bullionNameTitle || '');

            if (typeof aVal === 'string') {
                aVal = aVal.toLowerCase();
                bVal = bVal.toLowerCase();
            }

            if (sortOrder === 'asc') {
                return aVal > bVal ? 1 : -1;
            } else {
                return aVal < bVal ? 1 : -1;
            }
        });

        return filtered;
    }, [bullionNameBullions, searchTerm, sortField, sortOrder]);

    const handleAddBullion = () => {
        setEditingBullion(null);
        setModalOpen(true);
    };

    // Печати карточки: по одной на каждый встреченный тип, дебетовая первой.
    // Тип пришёл позже слитков, у старых записей его может не быть — это DEBIT
    const stampTypes = (bullionName) => {
        const types = new Set((bullionName.vaults || []).map(v => v.bullionType || 'DEBIT'));
        return ['DEBIT', 'CREDIT'].filter(type => types.has(type));
    };

    // Модалка открывается на первом хранилище, где операция разрешена:
    // vaults[0] может оказаться как раз закрытым, и селектор стартовал бы с недоступного
    const firstAllowedVault = (bullionName, flag) =>
        bullionName.vaults?.find(v => v[flag] !== false) || null;

    // Слитки наименования, которые чей-то накопитель и из которых разрешено
    // снимать: только они годятся в источник погашения
    const repayOptions = (bullionName) => (bullionName.vaults || [])
        .filter(vault => vault.creditCardId && vault.allowedExpense !== false)
        .map(vault => ({
            id: vault.bullionId,
            vaultName: vault.name,
            amount: vault.amount,
            bullionNameTitle: bullionName.bullionNameTitle,
            creditCardId: vault.creditCardId,
            creditCardMasked: vault.creditCardMasked,
            creditCardDebt: vault.creditCardDebt
        }));

    const hasAccumulator = (bullionName) => (bullionName.vaults || []).some(vault => vault.creditCardId);

    // Гасить есть чем и есть что: в слитке лежат деньги, а на карте висит долг
    const canRepay = (option) => Number(option.creditCardDebt) > 0 && Number(option.amount) > 0;

    const handleOpenRepay = async (bullionName) => {
        const options = repayOptions(bullionName);
        if (options.length === 0) return;

        try {
            // Модалке нужна карта целиком (остаток лимита, долг), а сгруппированный
            // ответ знает про неё только маску и долг
            const cardIds = [...new Set(options.map(option => option.creditCardId))];
            const responses = await Promise.all(cardIds.map(id => api.get(`/credit-cards/${id}`)));
            const cards = Object.fromEntries(responses.map(response => {
                const card = response.data.data;
                return [card.id, card];
            }));

            const initial = options.find(canRepay) || options[0];
            setRepayModal({ isOpen: true, options, bullionId: initial.id, cards });
        } catch (err) {
            console.error('Ошибка загрузки кредитной карты:', err);
        }
    };

    const handleRepay = async ({ amount, comment, dateOperation }) => {
        try {
            await api.post('/credit-cards/repay-from-bullion', {
                bullionId: repayModal.bullionId,
                amount: Number(amount),
                comment: comment || null,
                dateOperation: dateOperation || null
            });
            setRepayModal({ isOpen: false, options: [], bullionId: null, cards: {} });
            await fetchGroupedBullions();
        } catch (err) {
            console.error('Ошибка погашения по карте:', err);
            throw err;
        }
    };

    const handleOpenRefill = (bullionNameId, bullionNameTitle, vaultId) => {
        setTransactionModal({
            isOpen: true,
            type: 'refill',
            bullionNameId: bullionNameId,
            bullionNameTitle: bullionNameTitle,
            selectedVaultId: vaultId,
            selectedVaultName: null,
            amount: null,
            bullionId: null,
            description: null,
            fromVaultId: null,
            dateOperation: null // будет установлено в модальном окне
        });
    };

    const handleOpenWithdraw = (bullionNameId, bullionNameTitle, vaultId) => {
        setTransactionModal({
            isOpen: true,
            type: 'withdraw',
            bullionNameId: bullionNameId,
            bullionNameTitle: bullionNameTitle,
            selectedVaultId: vaultId,
            selectedVaultName: null,
            amount: null,
            bullionId: null,
            description: null,
            fromVaultId: null,
            dateOperation: null
        });
    };

    const handleOpenDelete = (bullionNameId, bullionNameTitle, vaultId, vaultName, bullionId) => {
        setTransactionModal({
            isOpen: true,
            type: 'delete',
            bullionNameId: bullionNameId,
            bullionNameTitle: bullionNameTitle,
            selectedVaultId: vaultId,
            selectedVaultName: vaultName,
            amount: null,
            bullionId: bullionId,
            description: null,
            fromVaultId: vaultId,
            dateOperation: null
        });
    };

    const handleRefill = async (amount, description, vaultId, dateOperation) => {
        try {
            await api.post('/bullions/refill', {
                bullionNameId: transactionModal.bullionNameId,
                vaultId: vaultId,
                amount: amount,
                description: description,
                dateOperation: dateOperation
            });
            await fetchGroupedBullions();
        } catch (err) {
            console.error('Ошибка пополнения слитка:', err);
            throw err;
        }
    };

    const handleWithdraw = async (amount, description, vaultId, dateOperation) => {
        try {
            await api.post('/bullions/withdraw', {
                bullionNameId: transactionModal.bullionNameId,
                vaultId: vaultId,
                amount: amount,
                description: description,
                dateOperation: dateOperation
            });
            await fetchGroupedBullions();
        } catch (err) {
            console.error('Ошибка списания со слитка:', err);
            throw err;
        }
    };

    const handleDeleteWithTransfer = async (targetVaultId, toLiquidityVault, description, dateOperation) => {
        try {
            // Эндпоинт один на оба экрана: DELETE /api/bullions/{id}/transfer.
            // Раньше отсюда уходил POST на несуществующий /bullions/delete-with-transfer
            await api.delete(`/bullions/${transactionModal.bullionId}/transfer`, {
                data: {
                    toVaultId: targetVaultId,
                    toLiquidityVault: toLiquidityVault,
                    description: description,
                    dateOperation: dateOperation
                }
            });
            await fetchGroupedBullions();
        } catch (err) {
            console.error('Ошибка удаления слитка с переносом:', err);
            throw err;
        }
    };

    const handleSaveBullion = async (bullionNameId, vaultId, amount, description, dateOperation, userComment, bullionType) => {
        // Сумму до правки знает только страница: ответ PUT про проведённую операцию молчит
        const previousAmount = editingBullion?.amount;
        try {
            if (editingBullion) {
                await api.put(`/bullions/${editingBullion.id}`, {
                    bullionNameId, vaultId, amount, description, dateOperation, bullionType,
                    userComment: userComment ?? null
                });
                notifyAmountChange(previousAmount, amount);
            } else {
                await api.post('/bullions', {
                    bullionNameId, vaultId, amount, description, dateOperation, bullionType
                });
            }
            setModalOpen(false);
            setEditingBullion(null);
            await fetchGroupedBullions();
        } catch (err) {
            console.error('Ошибка сохранения слитка:', err);
            throw err;
        }
    };

    const toggleSort = (field) => {
        if (sortField === field) {
            setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
        } else {
            setSortField(field);
            setSortOrder('desc');
        }
    };

    const closeTransactionModal = () => {
        setTransactionModal({
            isOpen: false,
            type: null,
            bullionNameId: null,
            bullionNameTitle: null,
            selectedVaultId: null,
            selectedVaultName: null,
            amount: null,
            bullionId: null,
            description: null,
            fromVaultId: null,
            dateOperation: null
        });
    };

    const totalAmount = groupedData?.totalAmount || 0;
    const averageRate = groupedData?.averageRate || 0;

    // Получение хранилищ для наименования с фильтрацией по типу операции
    const getVaultsForBullionName = (bullionNameId, operationType = null) => {
        const bullionName = bullionNameBullions.find(c => c.bullionNameId === bullionNameId);
        if (!bullionName) return [];

        let vaults = bullionName.vaults || [];

        // Показываем только те хранилища, где операция разрешена галочкой
        if (operationType === 'refill') {
            vaults = vaults.filter(v => v.allowedIncome !== false);
        } else if (operationType === 'withdraw') {
            vaults = vaults.filter(v => v.allowedExpense !== false);
        }

        return vaults;
    };

    // Перенос остатка - это внесение, поэтому только хранилища с «Можно вносить».
    // Ликвидный резерв доступен всегда - он отдельным переключателем в модалке
    const getAvailableVaultsForDelete = (currentVaultId) => {
        return allVaults.filter(vault => vault.id !== currentVaultId && vault.allowedIncome !== false);
    };

    const transactionConfig = {
        refill: {
            title: `Пополнение слитка "${transactionModal.bullionNameTitle}"`,
            buttonText: 'Внести',
            handler: handleRefill,
            showVaultSelector: true,
            showAmount: true,
            showDescription: true,
            vaultSelectorLabel: "Хранилище",
            descriptionLabel: "📝 Комментарий",
            descriptionPlaceholder: "Комментарий к пополнению (необязательно)...",
            type: 'refill',
            vaults: getVaultsForBullionName(transactionModal.bullionNameId, 'refill')
        },
        withdraw: {
            title: `Списание со слитка "${transactionModal.bullionNameTitle}"`,
            buttonText: 'Снять',
            handler: handleWithdraw,
            showVaultSelector: true,
            showAmount: true,
            showDescription: true,
            vaultSelectorLabel: "Хранилище",
            descriptionLabel: "📝 Комментарий",
            descriptionPlaceholder: "Комментарий к списанию (необязательно)...",
            type: 'withdraw',
            vaults: getVaultsForBullionName(transactionModal.bullionNameId, 'withdraw')
        },
        delete: {
            title: `Удаление слитка "${transactionModal.bullionNameTitle}"`,
            buttonText: 'Удалить и перенести',
            handler: handleDeleteWithTransfer,
            showVaultSelector: true,
            showAmount: false,
            showDescription: true,
            vaultSelectorLabel: "Хранилище для переноса остатков",
            descriptionLabel: "📝 Комментарий",
            descriptionPlaceholder: "Комментарий к удалению (необязательно)...",
            isConfirm: false,
            vaults: getAvailableVaultsForDelete(transactionModal.fromVaultId),
            initialVaultId: null,
            type: 'delete'
        }
    };

    const [transferModal, setTransferModal] = useState({
        isOpen: false,
        fromBullionNameId: null,
        fromBullionNameTitle: null,
        fromVaultId: null,
        fromBullionId: null,
        fromAmount: 0
    });

    const handleOpenTransfer = (bullionNameId, bullionNameTitle) => {
        setTransferModal({
            isOpen: true,
            fromBullionNameId: bullionNameId,
            fromBullionNameTitle: bullionNameTitle,
            fromVaultId: null,
            fromBullionId: null,
            fromAmount: 0
        });
    };

    const closeTransferModal = () => {
        setTransferModal({
            isOpen: false,
            fromBullionNameId: null,
            fromBullionNameTitle: null,
            fromVaultId: null,
            fromBullionId: null,
            fromAmount: 0
        });
    };

    const handleTransfer = async (amount, toBullionId, comment, dateOperation) => {
        try {
            await api.post('/bullions/transfer', {
                fromBullionId: transferModal.fromBullionId,
                toBullionId: toBullionId,
                amount: amount,
                comment: comment,
                dateOperation: dateOperation
            });
            await fetchGroupedBullions();
        } catch (err) {
            console.error('Ошибка перевода:', err);
            throw err;
        }
    };

    // Куда переводить: хранилища с allowedTransferIn (переводить И вносить).
    // Сравниваем именно bullionId - перевод адресуется слитку, а не хранилищу.
    const getTransferTargets = () => {
        const targets = [];

        bullionNameBullions.forEach(bullionName => {
            bullionName.vaults?.forEach(vault => {
                if (vault.bullionId !== transferModal.fromBullionId && vault.allowedTransferIn !== false) {
                    targets.push({
                        id: vault.bullionId,
                        bullionNameTitle: bullionName.bullionNameTitle,
                        vaultName: vault.name,
                        amount: vault.amount,
                        allowedTransferIn: vault.allowedTransferIn,
                        accountType: vault.accountType,
                        closeDate: vault.closeDate
                    });
                }
            });
        });
        return targets;
    };

    // Откуда переводить: хранилища с allowedTransferOut (переводить И снимать)
    const getFromBullions = () => {
        const targets = [];
        bullionNameBullions.forEach(bullionName => {
            if (bullionName.bullionNameId === transferModal.fromBullionNameId) {
                bullionName.vaults?.forEach(vault => {
                    if (vault.allowedTransferOut !== false) {
                        targets.push({
                            id: vault.bullionId,
                            bullionNameTitle: bullionName.bullionNameTitle,
                            vaultName: vault.name,
                            amount: vault.amount,
                            allowedTransferOut: vault.allowedTransferOut,
                            accountType: vault.accountType,
                            closeDate: vault.closeDate
                        });
                    }
                });
            }
        });
        return targets;
    };

    const currentTransaction = transactionModal.type ? transactionConfig[transactionModal.type] : null;

    if (loading) {
        return (
            <PageContainer>
                <PageLoading />
            </PageContainer>
        )
    }

    return (
        <PageContainer>
            <PageHeader title="Мои слитки" onBack={() => navigate('/dashboard')}>
                <Button onClick={handleAddBullion}>
                    <Plus />
                    Добавить слиток
                </Button>
            </PageHeader>

            <StatGrid className="lg:grid-cols-2">
                <StatCard
                    icon={Wallet}
                    label="Общая сумма"
                    value={`${formatAmount(totalAmount)} ₽`}
                    tone="brand"
                />
                <StatCard
                    icon={TrendingUp}
                    label="Средняя ставка"
                    value={`${averageRate.toFixed(2)}%`}
                    tone="success"
                />
            </StatGrid>

            <div className="flex flex-wrap items-center justify-between gap-3">
                <SearchInput
                    value={searchTerm}
                    onChange={setSearchTerm}
                    placeholder="Поиск по слиткам"
                />
                <div className="flex flex-wrap items-center gap-2">
                    <SortButton
                        label="По сумме"
                        field="bullionNameAmount"
                        sortField={sortField}
                        sortOrder={sortOrder}
                        onSort={toggleSort}
                    />
                    <SortButton
                        label="По названию"
                        field="bullionNameTitle"
                        sortField={sortField}
                        sortOrder={sortOrder}
                        onSort={toggleSort}
                    />
                </div>
            </div>

            <ErrorMessage>{error}</ErrorMessage>

            {getFilteredAndSorted.length === 0 ? (
                <EmptyState
                    icon={Coins}
                    title={searchTerm ? 'Ничего не найдено' : 'Нет слитков'}
                    description={
                        searchTerm ? 'Попробуйте изменить запрос' : 'Добавьте первый слиток'
                    }
                >
                    {!searchTerm && (
                        <Button onClick={handleAddBullion}>
                            <Plus />
                            Добавить слиток
                        </Button>
                    )}
                </EmptyState>
            ) : (
                <div className="grid gap-4 lg:grid-cols-2">
                    {getFilteredAndSorted.map((bullionName) => (
                        <Card
                            key={bullionName.bullionNameId}
                            className="gap-4 overflow-hidden pt-0 transition-shadow hover:shadow-md"
                        >
                            {/* Цвет наименования уже хранится в справочнике —
                                здесь он и опознаёт карточку */}
                            <div
                                className="h-1 w-full"
                                style={{
                                    backgroundColor:
                                        bullionName.bullionNameColor || 'var(--border)',
                                }}
                            />
                            <CardHeader className="pt-4">
                                <CardTitle className="flex items-center gap-2 text-base">
                                    <span
                                        className="size-2.5 shrink-0 rounded-full"
                                        style={{
                                            backgroundColor:
                                                bullionName.bullionNameColor || 'var(--border)',
                                        }}
                                    />
                                    {bullionName.bullionNameTitle}
                                </CardTitle>
                                {/* Наименование складывается из слитков разных хранилищ:
                                    типы у них могут не совпасть, тогда печатей две */}
                                <CardAction className="flex gap-1">
                                    {stampTypes(bullionName).map((type) => (
                                        <BullionTypeStamp key={type} type={type} />
                                    ))}
                                </CardAction>
                            </CardHeader>

                            <CardContent className="flex flex-col gap-3">
                                <div className="flex flex-col gap-1.5">
                                    <span className="text-sm text-muted-foreground">Хранилища</span>
                                    <div className="flex flex-wrap gap-1.5">
                                        {bullionName.vaults?.map((vault) => (
                                            <Badge key={vault.id} variant="outline" asChild>
                                                <Link
                                                    to={`/vaults/${vault.id}`}
                                                    state={{ from: 'bullions' }}
                                                >
                                                    {vault.name} ({formatAmount(vault.amount)} ₽)
                                                </Link>
                                            </Badge>
                                        ))}
                                    </div>
                                </div>

                                <div className="flex flex-wrap gap-x-8 gap-y-1 text-sm">
                                    <span className="flex items-center gap-2">
                                        <span className="text-muted-foreground">Общая сумма</span>
                                        <span className="font-medium tabular-nums">
                                            {formatAmount(bullionName.bullionNameAmount)} ₽
                                        </span>
                                    </span>
                                    <span className="flex items-center gap-2">
                                        <span className="text-muted-foreground">Средняя ставка</span>
                                        <span className="font-medium tabular-nums">
                                            {bullionName.bullionNameAverageRate?.toFixed(2) || 0}%
                                        </span>
                                    </span>
                                </div>
                            </CardContent>

                            <CardContent>
                                {/* Кнопку гасят групповые флаги: операция доступна, пока её
                                    разрешает хоть одно хранилище наименования. Считает их бэк. */}
                                <ButtonGroup className="flex-wrap">
                                    <Button
                                        className="text-success hover:text-success"
                                        variant="outline"
                                        size="sm"
                                        disabled={bullionName.allowedIncome === false}
                                        title={
                                            bullionName.allowedIncome === false
                                                ? 'Ни в одно хранилище этого слитка вносить нельзя'
                                                : ''
                                        }
                                        onClick={() => {
                                            const vault = firstAllowedVault(bullionName, 'allowedIncome')
                                            if (vault) {
                                                handleOpenRefill(bullionName.bullionNameId, bullionName.bullionNameTitle, vault.id)
                                            }
                                        }}
                                    >
                                        <Plus />
                                        Внести
                                    </Button>
                                    <Button
                                        className="text-warning hover:text-warning"
                                        variant="outline"
                                        size="sm"
                                        disabled={bullionName.allowedExpense === false}
                                        title={
                                            bullionName.allowedExpense === false
                                                ? 'Ни из одного хранилища этого слитка снимать нельзя'
                                                : ''
                                        }
                                        onClick={() => {
                                            const vault = firstAllowedVault(bullionName, 'allowedExpense')
                                            if (vault) {
                                                handleOpenWithdraw(bullionName.bullionNameId, bullionName.bullionNameTitle, vault.id)
                                            }
                                        }}
                                    >
                                        <Minus />
                                        Снять
                                    </Button>
                                    <Button
                                        className="text-primary hover:text-primary"
                                        variant="outline"
                                        size="sm"
                                        disabled={bullionName.allowedTransfer === false}
                                        title={
                                            bullionName.allowedTransfer === false
                                                ? 'Переводить не из чего или некуда: проверьте галочки хранилищ'
                                                : ''
                                        }
                                        onClick={() => {
                                            handleOpenTransfer(bullionName.bullionNameId, bullionName.bullionNameTitle)
                                        }}
                                    >
                                        <Repeat />
                                        Перевод
                                    </Button>
                                    {/* Кнопка есть только у наименований, чьи слитки
                                        привязаны к карте: остальным гасить нечего */}
                                    {hasAccumulator(bullionName) && (
                                        <Button
                                            className="text-success hover:text-success"
                                            variant="outline"
                                            size="sm"
                                            disabled={!repayOptions(bullionName).some(canRepay)}
                                            title={
                                                repayOptions(bullionName).some(canRepay)
                                                    ? ''
                                                    : 'Гасить нечего: долга по карте нет либо снимать из этих хранилищ нельзя'
                                            }
                                            onClick={() => handleOpenRepay(bullionName)}
                                        >
                                            <CreditCard />
                                            Погашение
                                        </Button>
                                    )}
                                    <Button
                                        variant="outline"
                                        size="sm"
                                        className="text-destructive hover:text-destructive"
                                        onClick={() => {
                                            const firstVault = bullionName.vaults?.[0]
                                            if (firstVault) {
                                                // Пятым идёт id слитка, а не хранилища: удаляем слиток
                                                handleOpenDelete(
                                                    bullionName.bullionNameId,
                                                    bullionName.bullionNameTitle,
                                                    firstVault.id,
                                                    firstVault.name,
                                                    firstVault.bullionId
                                                )
                                            }
                                        }}
                                    >
                                        <Trash2 />
                                        Удалить
                                    </Button>
                                </ButtonGroup>
                            </CardContent>
                        </Card>
                    ))}
                </div>
            )}

            <BullionModal
                isOpen={modalOpen}
                onClose={() => {
                    setModalOpen(false);
                    setEditingBullion(null);
                }}
                onSave={handleSaveBullion}
                initialBullionNameId={editingBullion?.bullionNameId}
                initialBullionNameTitle={editingBullion?.bullionNameTitle}
                initialVaultId={editingBullion?.vaultId}
                initialAmount={editingBullion?.amount}
                initialDescription={editingBullion?.description}
                initialDateOperation={editingBullion?.dateOperation}
                initialBullionType={editingBullion?.bullionType}
                isEditing={!!editingBullion}
            />

            {repayModal.isOpen && (() => {
                const selected = repayModal.options.find(option => option.id === repayModal.bullionId);
                return (
                    <CreditCardOperationModal
                        isOpen={repayModal.isOpen}
                        card={repayModal.cards[selected?.creditCardId]}
                        bullion={selected}
                        bullionOptions={repayModal.options}
                        onSelectBullion={(bullionId) => setRepayModal(prev => ({ ...prev, bullionId }))}
                        type="repay-bullion"
                        onClose={() => setRepayModal({ isOpen: false, options: [], bullionId: null, cards: {} })}
                        onSave={handleRepay}
                    />
                );
            })()}

            {currentTransaction && (
                <BullionTransactionModal
                    isOpen={transactionModal.isOpen}
                    onClose={closeTransactionModal}
                    onSave={currentTransaction.handler}
                    title={currentTransaction.title}
                    buttonText={currentTransaction.buttonText}
                    showVaultSelector={currentTransaction.showVaultSelector}
                    vaults={currentTransaction.vaults || getVaultsForBullionName(transactionModal.bullionNameId)}
                    initialVaultId={currentTransaction.initialVaultId || transactionModal.selectedVaultId}
                    initialAmount={currentTransaction.initialAmount}
                    initialDescription={currentTransaction.initialDescription}
                    showAmount={currentTransaction.showAmount}
                    showDescription={currentTransaction.showDescription}
                    isConfirm={currentTransaction.isConfirm || false}
                    confirmMessage={currentTransaction.confirmMessage}
                    bullionName={transactionModal.bullionNameTitle}
                    descriptionPlaceholder={currentTransaction.descriptionPlaceholder}
                    descriptionLabel={currentTransaction.descriptionLabel}
                    vaultSelectorLabel={currentTransaction.vaultSelectorLabel}
                    type={currentTransaction.type || null}
                    maxTransferAmount={currentTransaction.type === 'transfer' ? 0 : 0} // не используется для refill/withdraw/delete
                    transferTargets={currentTransaction.type === 'transfer' ? getTransferTargets() : []}
                    fromBullionId={currentTransaction.type === 'transfer' ? null : null}
                    fromBullions={currentTransaction.type === 'transfer' ? getFromBullions() : []}
                    onSelectFromBullion={currentTransaction.type === 'transfer' ? (bullionId, amount) => {
                        setTransferModal(prev => ({
                            ...prev,
                            fromBullionId: bullionId,
                            fromAmount: amount
                        }));
                    } : null}
                    selectedFromBullionId={currentTransaction.type === 'transfer' ? transferModal.fromBullionId : null}
                    initialDateOperation={transactionModal.dateOperation} // 👈 ПЕРЕДАЕМ ДАТУ
                />
            )}

            {transferModal.isOpen && (
                <BullionTransactionModal
                    isOpen={transferModal.isOpen}
                    onClose={closeTransferModal}
                    onSave={handleTransfer}
                    title={`Перевод из наименования "${transferModal.fromBullionNameTitle}"`}
                    buttonText="Перевести"
                    bullionName={transferModal.fromBullionNameTitle}
                    type="transfer"
                    maxTransferAmount={transferModal.fromAmount}
                    transferTargets={getTransferTargets()}
                    fromBullionId={transferModal.fromBullionId}
                    showAmount={true}
                    showDescription={true}
                    descriptionLabel="📝 Комментарий"
                    descriptionPlaceholder="Комментарий к переводу (необязательно)..."
                    fromBullions={getFromBullions()}
                    onSelectFromBullion={(bullionId, amount) => {
                        setTransferModal(prev => ({
                            ...prev,
                            fromBullionId: bullionId,
                            fromAmount: amount
                        }));
                    }}
                    selectedFromBullionId={transferModal.fromBullionId}
                    initialDateOperation={null} // Для transfer модалки будем устанавливать дату внутри (сегодня)
                />
            )}
        </PageContainer>
    )
}

export default BullionsPage