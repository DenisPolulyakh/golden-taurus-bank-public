import {useEffect, useMemo, useState} from 'react';
import {Link, useNavigate} from 'react-router-dom';
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

    // Модалка открывается на первом хранилище, где операция разрешена:
    // vaults[0] может оказаться как раз закрытым, и селектор стартовал бы с недоступного
    const firstAllowedVault = (bullionName, flag) =>
        bullionName.vaults?.find(v => v[flag] !== false) || null;

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
            await api.post('/bullions/delete-with-transfer', {
                bullionId: transactionModal.bullionId,
                fromVaultId: transactionModal.fromVaultId,
                toVaultId: targetVaultId,
                toLiquidityVault: toLiquidityVault,
                bullionNameId: transactionModal.bullionNameId,
                description: description,
                dateOperation: dateOperation
            });
            await fetchGroupedBullions();
        } catch (err) {
            console.error('Ошибка удаления слитка с переносом:', err);
            throw err;
        }
    };

    const handleSaveBullion = async (bullionNameId, vaultId, amount, description, dateOperation, userComment) => {
        // Сумму до правки знает только страница: ответ PUT про проведённую операцию молчит
        const previousAmount = editingBullion?.amount;
        try {
            if (editingBullion) {
                await api.put(`/bullions/${editingBullion.id}`, {
                    bullionNameId, vaultId, amount, description, dateOperation,
                    userComment: userComment ?? null
                });
                notifyAmountChange(previousAmount, amount);
            } else {
                await api.post('/bullions', {
                    bullionNameId, vaultId, amount, description, dateOperation
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
        } else if (operationType === 'delete') {
            vaults = vaults.filter(v => v.allowedDelete !== false);
        }

        return vaults;
    };

    const getAvailableVaultsForDelete = (currentVaultId) => {
        return allVaults.filter(vault => vault.id !== currentVaultId);
    };

    const transactionConfig = {
        refill: {
            title: `Пополнение слитка "${transactionModal.bullionNameTitle}"`,
            buttonText: 'Внести',
            buttonClass: 'refill-btn',
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
            buttonClass: 'withdraw-btn',
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
            buttonClass: 'delete-btn',
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
        return <div className="bullions-container">Загрузка...</div>;
    }

    return (
        <div className="bullions-container">
            <div className="bullions-content">
                <div className="bullions-header">
                    <h1>💰 Мои слитки</h1>
                    <div className="header-actions">
                        <button onClick={() => navigate('/dashboard')} className="back-btn">
                            ← Назад
                        </button>
                        <button onClick={handleAddBullion} className="add-bullion-btn">
                            + Добавить слиток
                        </button>
                    </div>
                </div>

                <div className="stats-cards">
                    <div className="stat-card">
                        <div className="stat-label">Общая сумма</div>
                        <div className="stat-value">{formatAmount(totalAmount)} ₽</div>
                    </div>
                    <div className="stat-card">
                        <span className="stat-name">📈 Средняя ставка:</span>
                        <div className="stat-value">{averageRate.toFixed(2)}%</div>
                    </div>
                </div>

                <div className="controls-bar">
                    <div className="search-bar">
                        <input
                            type="text"
                            placeholder="🔍 Поиск по слиткам"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                    </div>
                    <div className="sort-buttons">
                        <button
                            className={`sort-btn ${sortField === 'bullionNameAmount' ? 'active' : ''}`}
                            onClick={() => toggleSort('bullionNameAmount')}
                        >
                            По сумме {sortField === 'bullionNameAmount' && (sortOrder === 'asc' ? '↑' : '↓')}
                        </button>
                        <button
                            className={`sort-btn ${sortField === 'bullionNameTitle' ? 'active' : ''}`}
                            onClick={() => toggleSort('bullionNameTitle')}
                        >
                            По названию {sortField === 'bullionNameTitle' && (sortOrder === 'asc' ? '↑' : '↓')}
                        </button>
                    </div>
                </div>

                {error && <div className="error-message">{error}</div>}

                <div className="bullions-grid">
                    {getFilteredAndSorted.length === 0 ? (
                        <div className="empty-state">
                            {searchTerm ? 'Ничего не найдено' : 'Нет слитков. Добавьте первый!'}
                        </div>
                    ) : (
                        getFilteredAndSorted.map((bullionName) => (
                            <div key={bullionName.bullionNameId} className="bullion-card">
                                <div className="card-header">
                                    <h3>📁 {bullionName.bullionNameTitle}</h3>
                                </div>
                                <div className="card-body">
                                    <div className="vaults-list">
                                        <span className="vaults-label">🏦 Хранилища:</span>
                                        <div className="vaults-tags">
                                            {bullionName.vaults?.map((vault) => (
                                                <Link
                                                    key={vault.id}
                                                    to={`/vaults/${vault.id}`}
                                                    state={{from: 'bullions'}}
                                                    className="vault-link"
                                                >
                                                    {vault.name} ({formatAmount(vault.amount)} ₽)
                                                </Link>
                                            ))}
                                        </div>
                                    </div>
                                    <div className="card-stats">
                                        <div className="stat">
                                            <span className="stat-name">💰 Общая сумма:</span>
                                            <span className="stat-value">{formatAmount(bullionName.bullionNameAmount)} ₽</span>
                                        </div>
                                        <div className="stat">
                                            <span className="stat-name">📈 Средняя ставка:</span>
                                            <span className="stat-value">{bullionName.bullionNameAverageRate?.toFixed(2) || 0}%</span>
                                        </div>
                                    </div>
                                </div>
                                <div className="card-actions-horizontal">
                                    {/* Кнопку гасят групповые флаги: операция доступна, пока её
                                        разрешает хоть одно хранилище наименования. Считает их бэк. */}
                                    <button
                                        className="refill-btn"
                                        disabled={bullionName.allowedIncome === false}
                                        title={bullionName.allowedIncome === false ? 'Ни в одно хранилище этого слитка вносить нельзя' : ''}
                                        onClick={() => {
                                            const vault = firstAllowedVault(bullionName, 'allowedIncome');
                                            if (vault) {
                                                handleOpenRefill(bullionName.bullionNameId, bullionName.bullionNameTitle, vault.id);
                                            }
                                        }}
                                    >
                                        💰 Внести
                                    </button>
                                    <button
                                        className="withdraw-btn"
                                        disabled={bullionName.allowedExpense === false}
                                        title={bullionName.allowedExpense === false ? 'Ни из одного хранилища этого слитка снимать нельзя' : ''}
                                        onClick={() => {
                                            const vault = firstAllowedVault(bullionName, 'allowedExpense');
                                            if (vault) {
                                                handleOpenWithdraw(bullionName.bullionNameId, bullionName.bullionNameTitle, vault.id);
                                            }
                                        }}
                                    >
                                        💸 Снять
                                    </button>
                                    <button
                                        className="transfer-btn"
                                        disabled={bullionName.allowedTransfer === false}
                                        title={bullionName.allowedTransfer === false ? 'Переводить не из чего или некуда: проверьте галочки хранилищ' : ''}
                                        onClick={() => {
                                            handleOpenTransfer(bullionName.bullionNameId, bullionName.bullionNameTitle);
                                        }}
                                    >
                                        🔄 Перевод
                                    </button>
                                    <button
                                        className="delete-vault-btn"
                                        onClick={() => {
                                            const firstVault = bullionName.vaults?.[0];
                                            if (firstVault) {
                                                handleOpenDelete(
                                                    bullionName.bullionNameId,
                                                    bullionName.bullionNameTitle,
                                                    firstVault.id,
                                                    firstVault.name,
                                                    firstVault.id
                                                );
                                            }
                                        }}
                                    >
                                        🗑️ Удалить
                                    </button>
                                </div>
                            </div>
                        ))
                    )}
                </div>

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
                    isEditing={!!editingBullion}
                />

                {currentTransaction && (
                    <BullionTransactionModal
                        isOpen={transactionModal.isOpen}
                        onClose={closeTransactionModal}
                        onSave={currentTransaction.handler}
                        title={currentTransaction.title}
                        buttonText={currentTransaction.buttonText}
                        buttonClass={currentTransaction.buttonClass}
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
                        buttonClass="transfer-btn"
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
            </div>
        </div>
    );
}

export default BullionsPage;