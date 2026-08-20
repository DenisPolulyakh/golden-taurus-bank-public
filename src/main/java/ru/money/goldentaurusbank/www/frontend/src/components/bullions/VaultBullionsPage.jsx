import { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import api from '../../api/axios';
import BullionModal from './BullionModal';
import BullionTransactionModal from './BullionTransactionModal';
import { notifyAmountChange } from './bullionAmount';
import './Bullions.css';

const formatAmount = (amount) => {
    if (!amount && amount !== 0) return '0';
    const num = typeof amount === 'string' ? parseFloat(amount) : amount;
    if (isNaN(num)) return '0';

    const parts = num.toFixed(2).split('.');
    const integerPart = parts[0];
    const decimalPart = parts[1];
    const formattedInteger = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');

    if (decimalPart && decimalPart !== '00') {
        const trimmedDecimal = decimalPart.replace(/0+$/, '');
        if (trimmedDecimal) {
            return `${formattedInteger}.${trimmedDecimal}`;
        }
    }
    return formattedInteger;
};

function VaultBullionsPage() {
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

    const handleRefill = async (amount, userComment, selectedVaultId, dateOperation) => {
        setActionLoading(true);
        try {
            await api.post('/bullions/refill', {
                bullionNameId: transactionModal.bullion.bullionNameId,
                vaultId: parseInt(vaultId),
                amount: amount,
                userComment: userComment,
                dateOperation: dateOperation ? dateOperation : null
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

    const handleWithdraw = async (amount, userComment, selectedVaultId, dateOperation) => {
        setActionLoading(true);
        try {
            await api.post('/bullions/withdraw', {
                bullionNameId: transactionModal.bullion.bullionNameId,
                vaultId: parseInt(vaultId),
                amount: amount,
                userComment: userComment,
                dateOperation: dateOperation ? dateOperation : null
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
            await api.delete(`/bullions/${transactionModal.bullion.id}/transfer`, {
                data: {
                    fromVaultId: transactionModal.fromVaultId,
                    toVaultId: targetVaultId,
                    toLiquidityVault: toLiquidityVault,
                    description: description,
                    dateOperation: dateOperation ? dateOperation : null
                }
            });
            closeTransactionModal();
            await refreshData();
        } catch (err) {
            console.error('Ошибка удаления слитка с переносом:', err);
            throw err;
        } finally {
            setActionLoading(false);
        }
    };

    const handleTransfer = async (amount, toBullionId, comment, dateOperation) => {
        try {
            await api.post('/bullions/transfer', {
                fromBullionId: transferModal.fromBullionId,
                toBullionId: toBullionId,
                amount: amount,
                comment: comment,
                dateOperation: dateOperation ? dateOperation : null
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
            <div className="bullions-container">
                <div className="loading-spinner">Загрузка...</div>
            </div>
        );
    }

    if (!vault) {
        return (
            <div className="bullions-container">
                <div className="bullions-content">
                    <div className="error-message">Хранилище не найдено</div>
                    <button onClick={handleGoBack} className="back-btn">
                        ← Назад
                    </button>
                </div>
            </div>
        );
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
            buttonClass: 'refill-btn',
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
            buttonClass: 'withdraw-btn',
            handler: handleWithdraw,
            showVaultSelector: false,
            showAmount: true,
            showDescription: true,
            vaultSelectorLabel: "Хранилище",
            descriptionLabel: "📝 Комментарий",
            descriptionPlaceholder: "Комментарий к списанию (необязательно)...",
            type: 'withdraw'
        },
        delete: {
            title: `Удаление слитка "${transactionModal.bullion?.bullionNameTitle}"`,
            buttonText: 'Удалить и перенести',
            buttonClass: 'delete-btn',
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
        <div className="bullions-container">
            <div className="bullions-content">
                <div className="bullions-header">
                    <h1>🏦 {vault.name}</h1>
                    <div className="header-actions">
                        <button
                            onClick={handleGoBack}
                            className="back-btn"
                            disabled={actionLoading}
                        >
                            ← Назад
                        </button>
                        <button
                            onClick={handleAddBullion}
                            className="add-bullion-btn"
                            disabled={actionLoading || !allowedIncome}
                            title={!allowedIncome ? 'Хранилище заблокировано' : ''}
                        >
                            + Добавить слиток
                        </button>
                    </div>
                </div>

                <div className="stats-cards">
                    <StatCard label="Общая сумма в хранилище" value={`${formatAmount(totalAmount)} ₽`} />
                    <StatCard label="Количество наименований" value={bullionsCount} />
                    <StatCard label="Процентная ставка" value={`${vault.interestRate || 0}%`} />
                </div>

                <div className="controls-bar">
                    <div className="search-bar">
                        <input
                            type="text"
                            placeholder="🔍 Поиск по наименованию..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            disabled={actionLoading}
                        />
                    </div>
                    <div className="sort-buttons">
                        <SortButton
                            label="По сумме"
                            field="amount"
                            currentField={sortConfig.field}
                            currentOrder={sortConfig.order}
                            onSort={handleSort}
                            disabled={actionLoading}
                        />
                        <SortButton
                            label="По наименованию"
                            field="bullionNameTitle"
                            currentField={sortConfig.field}
                            currentOrder={sortConfig.order}
                            onSort={handleSort}
                            disabled={actionLoading}
                        />
                    </div>
                </div>

                {error && <div className="error-message">{error}</div>}

                <div className="bullions-grid">
                    {filteredAndSortedBullions.length === 0 ? (
                        <div className="empty-state">
                            {searchTerm ? 'Ничего не найдено' : 'Нет слитков в этом хранилище. Добавьте первый!'}
                        </div>
                    ) : (
                        filteredAndSortedBullions.map((bullion) => (
                            <BullionCard
                                key={bullion.id}
                                bullion={bullion}
                                onEdit={() => handleEditBullion(bullion)}
                                onDelete={() => handleOpenDelete(bullion)}
                                onRefill={() => handleOpenRefill(bullion)}
                                onWithdraw={() => handleOpenWithdraw(bullion)}
                                onTransfer={() => handleOpenTransfer(bullion)}
                                disabled={actionLoading}
                                vaultFlags={{
                                    allowedIncome,
                                    allowedExpense,
                                    allowedTransferOut
                                }}
                            />
                        ))
                    )}
                </div>

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
                        buttonClass={currentTransaction.buttonClass}
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
                        initialDateOperation={transactionModal.dateOperation}
                        availableAmount={transactionModal.bullion?.amount}
                    />
                )}

                {transferModal.isOpen && (
                    <BullionTransactionModal
                        isOpen={transferModal.isOpen}
                        onClose={closeTransferModal}
                        onSave={handleTransfer}
                        title="Перевод между слитками"
                        buttonText="Перевести"
                        buttonClass="transfer-btn"
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
                        initialDateOperation={transferModal.dateOperation}
                    />
                )}
            </div>
        </div>
    );
}

const StatCard = ({ label, value }) => (
    <div className="stat-card">
        <div className="stat-label">{label}</div>
        <div className="stat-value">{value}</div>
    </div>
);

const SortButton = ({ label, field, currentField, currentOrder, onSort, disabled }) => {
    const isActive = currentField === field;
    const arrow = isActive ? (currentOrder === 'asc' ? '↑' : '↓') : '';

    return (
        <button
            className={`sort-btn ${isActive ? 'active' : ''}`}
            onClick={() => onSort(field)}
            disabled={disabled}
        >
            {label} {arrow}
        </button>
    );
};

const BullionCard = ({ bullion, onEdit, onDelete, onRefill, onWithdraw, onTransfer, disabled, vaultFlags }) => {
    const formatDate = (dateString) => {
        if (!dateString) return '';
        const date = new Date(dateString);
        return date.toLocaleDateString('ru-RU', {
            year: 'numeric',
            month: 'long',
            day: 'numeric'
        });
    };

    const {
        allowedIncome = true,
        allowedExpense = true,
        allowedTransferOut = true
    } = vaultFlags || {};

    // Кнопки всегда видны, но disabled если флаг false или общий disabled.
    // Правка и удаление слитка галочками не ограничены: галочки про движение денег
    const isDisabled = (flag) => disabled || !flag;

    return (
        <div className="bullion-card vault-card">
            <div className="card-header">
                <h3>📁 {bullion.bullionNameTitle}</h3>
            </div>
            <div className="card-body">
                <div className="card-stats">
                    <div className="stat">
                        <span className="stat-name">💰 Сумма:</span>
                        <span className="stat-value amount-value">{formatAmount(bullion.amount)} ₽</span>
                    </div>

                    {bullion.description && (
                        <div className="stat">
                            <span className="stat-name">📝 Описание:</span>
                            <span className="stat-description">{bullion.description}</span>
                        </div>
                    )}

                    {bullion.createdAt && (
                        <div className="stat">
                            <span className="stat-name">📅 Дата создания:</span>
                            <span className="stat-description">{formatDate(bullion.createdAt)}</span>
                        </div>
                    )}
                    {bullion.updatedAt && bullion.updatedAt !== bullion.createdAt && (
                        <div className="stat">
                            <span className="stat-name">🔄 Последнее изменение:</span>
                            <span className="stat-description">{formatDate(bullion.updatedAt)}</span>
                        </div>
                    )}
                </div>
            </div>
            <div className="card-actions-horizontal">
                <button
                    className="refill-btn"
                    onClick={onRefill}
                    disabled={isDisabled(allowedIncome)}
                >
                    💰 Внести
                </button>
                <button
                    className="withdraw-btn"
                    onClick={onWithdraw}
                    disabled={isDisabled(allowedExpense)}
                >
                    💸 Снять
                </button>
                <button
                    className="edit-vault-btn"
                    onClick={onEdit}
                    disabled={disabled}
                >
                    ✏️ Редактировать
                </button>
                {/* Перевод отсюда - это снятие: нет галочки «Можно снимать», нет и перевода */}
                <button
                    className="transfer-btn"
                    onClick={onTransfer}
                    disabled={isDisabled(allowedTransferOut)}
                >
                    🔄 Перевод
                </button>
                <button
                    className="delete-vault-btn"
                    onClick={onDelete}
                    disabled={disabled}
                >
                    🗑️ Удалить
                </button>
            </div>
        </div>
    );
};

export default VaultBullionsPage;