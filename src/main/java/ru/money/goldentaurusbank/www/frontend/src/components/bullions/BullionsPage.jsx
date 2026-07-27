import {useEffect, useMemo, useState} from 'react';
import {Link, useNavigate} from 'react-router-dom';
import api from '../../api/axios';
import BullionModal from './BullionModal';
import BullionTransactionModal from './BullionTransactionModal';
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
    const [categoryBullions, setCategoryBullions] = useState([]);
    const [allVaults, setAllVaults] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [modalOpen, setModalOpen] = useState(false);
    const [editingBullion, setEditingBullion] = useState(null);
    const [sortField, setSortField] = useState('categoryAmount');
    const [sortOrder, setSortOrder] = useState('desc');

    const [transactionModal, setTransactionModal] = useState({
        isOpen: false,
        type: null,
        categoryId: null,
        categoryName: null,
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
            setCategoryBullions(data.categoryBullionList || []);
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
        let filtered = [...categoryBullions];

        if (searchTerm && searchTerm.trim()) {
            const searchLower = searchTerm.toLowerCase().trim();
            filtered = filtered.filter(category => {
                const categoryMatch = category.categoryName?.toLowerCase().includes(searchLower);
                const vaultMatch = category.vaults?.some(vault =>
                    vault.name && vault.name.toLowerCase().includes(searchLower)
                );
                return categoryMatch || vaultMatch;
            });
        }

        filtered.sort((a, b) => {
            let aVal = sortField === 'categoryAmount' ? (a.categoryAmount || 0) : (a.categoryName || '');
            let bVal = sortField === 'categoryAmount' ? (b.categoryAmount || 0) : (b.categoryName || '');

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
    }, [categoryBullions, searchTerm, sortField, sortOrder]);

    const handleAddBullion = () => {
        setEditingBullion(null);
        setModalOpen(true);
    };

    const handleOpenRefill = (categoryId, categoryName, vaultId) => {
        setTransactionModal({
            isOpen: true,
            type: 'refill',
            categoryId: categoryId,
            categoryName: categoryName,
            selectedVaultId: vaultId,
            selectedVaultName: null,
            amount: null,
            bullionId: null,
            description: null,
            fromVaultId: null,
            dateOperation: null // будет установлено в модальном окне
        });
    };

    const handleOpenWithdraw = (categoryId, categoryName, vaultId) => {
        setTransactionModal({
            isOpen: true,
            type: 'withdraw',
            categoryId: categoryId,
            categoryName: categoryName,
            selectedVaultId: vaultId,
            selectedVaultName: null,
            amount: null,
            bullionId: null,
            description: null,
            fromVaultId: null,
            dateOperation: null
        });
    };

    const handleOpenDelete = (categoryId, categoryName, vaultId, vaultName, bullionId) => {
        setTransactionModal({
            isOpen: true,
            type: 'delete',
            categoryId: categoryId,
            categoryName: categoryName,
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
                categoryId: transactionModal.categoryId,
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
                categoryId: transactionModal.categoryId,
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
                categoryId: transactionModal.categoryId,
                description: description,
                dateOperation: dateOperation
            });
            await fetchGroupedBullions();
        } catch (err) {
            console.error('Ошибка удаления слитка с переносом:', err);
            throw err;
        }
    };

    const handleSaveBullion = async (categoryId, vaultId, amount, description, dateOperation) => {
        try {
            if (editingBullion) {
                await api.put(`/bullions/${editingBullion.id}`, {
                    categoryId, vaultId, amount, description, dateOperation
                });
            } else {
                await api.post('/bullions', {
                    categoryId, vaultId, amount, description, dateOperation
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
            categoryId: null,
            categoryName: null,
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

    // Получение хранилищ для категории с фильтрацией по типу операции
    const getVaultsForCategory = (categoryId, operationType = null) => {
        const category = categoryBullions.find(c => c.categoryId === categoryId);
        if (!category) return [];

        let vaults = category.vaults || [];

        // Фильтруем по типу операции - исключаем срочные хранилища
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
            title: `Пополнение слитка "${transactionModal.categoryName}"`,
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
            vaults: getVaultsForCategory(transactionModal.categoryId, 'refill')
        },
        withdraw: {
            title: `Списание со слитка "${transactionModal.categoryName}"`,
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
            vaults: getVaultsForCategory(transactionModal.categoryId, 'withdraw')
        },
        delete: {
            title: `Удаление слитка "${transactionModal.categoryName}"`,
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
        fromCategoryId: null,
        fromCategoryName: null,
        fromVaultId: null,
        fromBullionId: null,
        fromAmount: 0
    });

    const handleOpenTransfer = (categoryId, categoryName) => {
        setTransferModal({
            isOpen: true,
            fromCategoryId: categoryId,
            fromCategoryName: categoryName,
            fromVaultId: null,
            fromBullionId: null,
            fromAmount: 0
        });
    };

    const closeTransferModal = () => {
        setTransferModal({
            isOpen: false,
            fromCategoryId: null,
            fromCategoryName: null,
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

    // Фильтрация по флагу allowedTransfer - исключаем срочные хранилища
    const getTransferTargets = () => {
        const targets = [];

        categoryBullions.forEach(category => {
            category.vaults?.forEach(vault => {
                // Проверяем allowedTransfer - если false, то не показываем
                if (vault.id !== transferModal.fromBullionId && vault.allowedTransfer !== false) {
                    targets.push({
                        id: vault.bullionId,
                        categoryName: category.categoryName,
                        vaultName: vault.name,
                        amount: vault.amount,
                        allowedTransfer: vault.allowedTransfer,
                        accountType: vault.accountType,
                        closeDate: vault.closeDate
                    });
                }
            });
        });
        return targets;
    };

    const getFromBullions = () => {
        const targets = [];
        categoryBullions.forEach(category => {
            if (category.categoryId === transferModal.fromCategoryId) {
                category.vaults?.forEach(vault => {
                    // Исключаем срочные хранилища
                    if (vault.allowedTransfer !== false) {
                        targets.push({
                            id: vault.bullionId,
                            categoryName: category.categoryName,
                            vaultName: vault.name,
                            amount: vault.amount,
                            allowedTransfer: vault.allowedTransfer,
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
                            className={`sort-btn ${sortField === 'categoryAmount' ? 'active' : ''}`}
                            onClick={() => toggleSort('categoryAmount')}
                        >
                            По сумме {sortField === 'categoryAmount' && (sortOrder === 'asc' ? '↑' : '↓')}
                        </button>
                        <button
                            className={`sort-btn ${sortField === 'categoryName' ? 'active' : ''}`}
                            onClick={() => toggleSort('categoryName')}
                        >
                            По названию {sortField === 'categoryName' && (sortOrder === 'asc' ? '↑' : '↓')}
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
                        getFilteredAndSorted.map((category) => (
                            <div key={category.categoryId} className="bullion-card">
                                <div className="card-header">
                                    <h3>📁 {category.categoryName}</h3>
                                </div>
                                <div className="card-body">
                                    <div className="vaults-list">
                                        <span className="vaults-label">🏦 Хранилища:</span>
                                        <div className="vaults-tags">
                                            {category.vaults?.map((vault) => (
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
                                            <span className="stat-value">{formatAmount(category.categoryAmount)} ₽</span>
                                        </div>
                                        <div className="stat">
                                            <span className="stat-name">📈 Средняя ставка:</span>
                                            <span className="stat-value">{category.categoryAverageRate?.toFixed(2) || 0}%</span>
                                        </div>
                                    </div>
                                </div>
                                <div className="card-actions-horizontal">
                                    {/* Кнопки всегда активны - disabled=false */}
                                    <button
                                        className="refill-btn"
                                        onClick={() => {
                                            const firstVault = category.vaults?.[0];
                                            if (firstVault) {
                                                handleOpenRefill(category.categoryId, category.categoryName, firstVault.id);
                                            }
                                        }}
                                    >
                                        💰 Внести
                                    </button>
                                    <button
                                        className="withdraw-btn"
                                        onClick={() => {
                                            const firstVault = category.vaults?.[0];
                                            if (firstVault) {
                                                handleOpenWithdraw(category.categoryId, category.categoryName, firstVault.id);
                                            }
                                        }}
                                    >
                                        💸 Снять
                                    </button>
                                    <button
                                        className="transfer-btn"
                                        onClick={() => {
                                            handleOpenTransfer(category.categoryId, category.categoryName);
                                        }}
                                    >
                                        🔄 Перевод
                                    </button>
                                    <button
                                        className="delete-vault-btn"
                                        onClick={() => {
                                            const firstVault = category.vaults?.[0];
                                            if (firstVault) {
                                                handleOpenDelete(
                                                    category.categoryId,
                                                    category.categoryName,
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
                    initialCategoryId={editingBullion?.categoryId}
                    initialCategoryName={editingBullion?.categoryName}
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
                        vaults={currentTransaction.vaults || getVaultsForCategory(transactionModal.categoryId)}
                        initialVaultId={currentTransaction.initialVaultId || transactionModal.selectedVaultId}
                        initialAmount={currentTransaction.initialAmount}
                        initialDescription={currentTransaction.initialDescription}
                        showAmount={currentTransaction.showAmount}
                        showDescription={currentTransaction.showDescription}
                        isConfirm={currentTransaction.isConfirm || false}
                        confirmMessage={currentTransaction.confirmMessage}
                        bullionName={transactionModal.categoryName}
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
                        title={`Перевод из категории "${transferModal.fromCategoryName}"`}
                        buttonText="Перевести"
                        buttonClass="transfer-btn"
                        bullionName={transferModal.fromCategoryName}
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