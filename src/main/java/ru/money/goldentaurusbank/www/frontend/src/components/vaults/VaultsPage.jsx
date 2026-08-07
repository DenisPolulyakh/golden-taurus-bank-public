import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';  // ← добавлено Link
import api from '../../api/axios';
import VaultModal from './VaultModal';
import './Vaults.css';

function VaultsPage() {
    const [vaults, setVaults] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [loading, setLoading] = useState(false);
    const [tableLoading, setTableLoading] = useState(false);
    const [error, setError] = useState('');
    const [modalOpen, setModalOpen] = useState(false);
    const [editingVault, setEditingVault] = useState(null);

    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);

    const [sortField, setSortField] = useState('name');
    const [sortOrder, setSortOrder] = useState('asc');

    const navigate = useNavigate();
    const timeoutRef = useRef(null);
    const isInitialMount = useRef(true);

    const LIQUIDITY_RESERVE_NAME = "Ликвидный резерв";

    const fetchVaults = useCallback(async (search, sortBy, sortOrderParam, page, size, showTableLoader = true) => {
        if (showTableLoader) {
            setTableLoading(true);
        } else {
            setLoading(true);
        }

        try {
            const params = new URLSearchParams();
            if (search) params.append('search', search);
            if (sortBy) params.append('sortBy', sortBy);
            if (sortOrderParam) params.append('sortOrder', sortOrderParam);
            params.append('page', page);
            params.append('size', size);

            const response = await api.get(`/vaults?${params.toString()}`);
            const pageData = response.data.data;
            setVaults(pageData.content || []);
            setCurrentPage(pageData.pageNumber);
            setTotalPages(pageData.totalPages);
            setTotalElements(pageData.totalElements);
        } catch (err) {
            console.error('Ошибка загрузки хранилищ:', err);
            setError('Не удалось загрузить хранилища');
        } finally {
            setLoading(false);
            setTableLoading(false);
        }
    }, []);

    const debouncedFetch = useCallback((search, sortBy, sortOrderParam, page, size) => {
        if (timeoutRef.current) {
            clearTimeout(timeoutRef.current);
        }
        timeoutRef.current = setTimeout(() => {
            fetchVaults(search, sortBy, sortOrderParam, page, size, true);
        }, 300);
    }, [fetchVaults]);

    useEffect(() => {
        if (isInitialMount.current) {
            isInitialMount.current = false;
            fetchVaults(searchTerm, sortField, sortOrder, currentPage, pageSize, false);
            return;
        }

        debouncedFetch(searchTerm, sortField, sortOrder, currentPage, pageSize);

        return () => {
            if (timeoutRef.current) {
                clearTimeout(timeoutRef.current);
            }
        };
    }, [searchTerm, sortField, sortOrder, currentPage, pageSize, debouncedFetch, fetchVaults]);

    const handleAddVault = () => {
        setEditingVault(null);
        setModalOpen(true);
    };

    const handleEditVault = (vault) => {
        setEditingVault(vault);
        setModalOpen(true);
    };

    const handleSaveVault = async (name, interestRate, description, bankId, accountType, closeDate) => {
        try {
            let savedVault;

            const requestData = {
                name,
                interestRate,
                description,
                bankId: bankId || null,
                accountType: accountType || 'SAVINGS',
                closeDate: closeDate || null
            };

            if (editingVault) {
                const response = await api.put(`/vaults/${editingVault.id}`, requestData);
                savedVault = response.data.data;
                setVaults(prev => prev.map(v => v.id === editingVault.id ? savedVault : v));
            } else {
                const response = await api.post('/vaults', requestData);
                savedVault = response.data.data;
                setVaults(prev => [savedVault, ...prev]);
                if (vaults.length + 1 > pageSize) {
                    fetchVaults(searchTerm, sortField, sortOrder, 1, pageSize, true);
                }
            }

            setModalOpen(false);
            setEditingVault(null);
        } catch (err) {
            console.error('Ошибка сохранения хранилища:', err);
            fetchVaults(searchTerm, sortField, sortOrder, currentPage, pageSize, true);
            throw err;
        }
    };

    const handleDeleteVault = async (id, name) => {
        if (window.confirm(`Удалить хранилище "${name}"?`)) {
            const oldVaults = [...vaults];
            setVaults(prev => prev.filter(vault => vault.id !== id));

            try {
                await api.delete(`/vaults/${id}`);
                if (vaults.length === 1 && currentPage > 1) {
                    setCurrentPage(currentPage - 1);
                }
            } catch (err) {
                console.error('Ошибка удаления хранилища:', err);
                setVaults(oldVaults);
            }
        }
    };

    const handleVaultClick = (vaultId) => {
        navigate(`/vaults/${vaultId}`, { state: { from: 'vaults' } });
    };

    const toggleSort = (field) => {
        setCurrentPage(1);
        if (sortField === field) {
            setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
        } else {
            setSortField(field);
            setSortOrder('asc');
        }
    };

    const goToPage = (page) => {
        setCurrentPage(page);
    };

    const isLiquidityReserve = (vaultName) => vaultName === LIQUIDITY_RESERVE_NAME;
    const hasAnyActions = vaults.some(v => !isLiquidityReserve(v.name));

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

    if (loading && vaults.length === 0) {
        return <div className="vaults-container">Загрузка...</div>;
    }

    return (
        <div className="vaults-container">
            <div className="vaults-content">
                <div className="vaults-header">
                    <h1>🏦 Хранилища</h1>
                    <div className="header-actions">
                        <button onClick={() => navigate('/dashboard')} className="back-btn">
                            ← Назад
                        </button>
                        <button onClick={handleAddVault} className="add-vault-btn">
                            + Добавить хранилище
                        </button>
                    </div>
                </div>

                <div className="search-bar">
                    <input
                        type="text"
                        placeholder="🔍 Поиск хранилищ..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                    />
                </div>

                {error && <div className="error-message">{error}</div>}

                <div className="vaults-table-wrapper">
                    <table className="vaults-table">
                        <thead>
                        <tr>
                            <th className="sortable-header" onClick={() => toggleSort('bankName')}>
                                Банк
                                {sortField === 'bankName' && (
                                    <span className="sort-indicator">{sortOrder === 'asc' ? ' ↑' : ' ↓'}</span>
                                )}
                            </th>
                            <th className="sortable-header" onClick={() => toggleSort('name')}>
                                Имя хранилища
                                {sortField === 'name' && (
                                    <span className="sort-indicator">{sortOrder === 'asc' ? ' ↑' : ' ↓'}</span>
                                )}
                            </th>
                            <th className="sortable-header" onClick={() => toggleSort('totalAmount')}>
                                Всего в хранилище
                                {sortField === 'totalAmount' && (
                                    <span className="sort-indicator">{sortOrder === 'asc' ? ' ↑' : ' ↓'}</span>
                                )}
                            </th>
                            <th>Тип счета</th>
                            <th className="sortable-header" onClick={() => toggleSort('interestRate')}>
                                Ставка (%)
                                {sortField === 'interestRate' && (
                                    <span className="sort-indicator">{sortOrder === 'asc' ? ' ↑' : ' ↓'}</span>
                                )}
                            </th>
                            <th>Описание</th>
                            {hasAnyActions && <th>Действия</th>}
                        </tr>
                        </thead>

                        <tbody>
                        {tableLoading ? (
                            <tr>
                                <td colSpan={hasAnyActions ? 7 : 6} className="empty-row">
                                    <div className="table-loader">Обновление...</div>
                                </td>
                            </tr>
                        ) : vaults.length === 0 ? (
                            <tr>
                                <td colSpan={hasAnyActions ? 7 : 6} className="empty-row">
                                    {searchTerm ? 'Ничего не найдено' : 'Нет хранилищ. Добавьте первое или импортируйте из Excel!'}
                                </td>
                            </tr>
                        ) : (
                            vaults.map((vault) => {
                                const hasActions = !isLiquidityReserve(vault.name);
                                const bankName = vault.bankName || '—';
                                const totalAmount = vault.totalAmount || 0;

                                return (
                                    <tr key={vault.id}>
                                        <td className="vault-bank">
                                            {bankName !== '—' ? (
                                                <Link to={`/banks/${vault.bankId}`} className="bank-link">
                                                    {bankName}
                                                </Link>
                                            ) : (
                                                <span className="no-bank">—</span>
                                            )}
                                        </td>
                                        <td className="vault-name clickable" onClick={() => handleVaultClick(vault.id)}>
                                            {vault.name}
                                        </td>
                                        <td className="vault-total">
                                            {formatAmount(totalAmount)} ₽
                                        </td>
                                        <td>
                        <span className="account-type-badge">
                            {vault.accountType === 'SAVINGS' ? '💰 Накопительный' : '⏳ Срочный'}
                            {vault.closeDate && vault.accountType === 'TERM' && (
                                <span className="close-date"> (до {new Date(vault.closeDate).toLocaleDateString('ru-RU')})</span>
                            )}
                        </span>
                                        </td>
                                        <td className="interest-rate">{vault.interestRate}%</td>
                                        <td className="vault-description">
                                            {vault.description || ''}
                                        </td>
                                        {hasActions && hasAnyActions && (
                                            <td className="actions">
                                                <button onClick={() => handleEditVault(vault)} className="edit-btn" title="Редактировать">
                                                    ✏️
                                                </button>
                                                <button onClick={() => handleDeleteVault(vault.id, vault.name)} className="delete-btn" title="Удалить">
                                                    🗑️
                                                </button>
                                            </td>
                                        )}
                                    </tr>
                                );
                            })
                        )}
                        </tbody>
                    </table>
                </div>

                {totalPages > 1 && !tableLoading && (
                    <div className="pagination">
                        <button
                            onClick={() => goToPage(currentPage - 1)}
                            disabled={currentPage === 1}
                            className="page-btn"
                        >
                            ← Назад
                        </button>
                        <span className="page-info">
                            Страница {currentPage} из {totalPages} (всего {totalElements})
                        </span>
                        <button
                            onClick={() => goToPage(currentPage + 1)}
                            disabled={currentPage === totalPages}
                            className="page-btn"
                        >
                            Вперёд →
                        </button>
                    </div>
                )}

                <div className="page-size-selector">
                    <label>Показывать на странице: </label>
                    <select
                        value={pageSize}
                        onChange={(e) => {
                            setPageSize(Number(e.target.value));
                            setCurrentPage(1);
                        }}
                    >
                        <option value={5}>5</option>
                        <option value={10}>10</option>
                        <option value={20}>20</option>
                        <option value={50}>50</option>
                    </select>
                </div>

                <VaultModal
                    isOpen={modalOpen}
                    onClose={() => {
                        setModalOpen(false);
                        setEditingVault(null);
                    }}
                    onSave={handleSaveVault}
                    initialName={editingVault?.name || ''}
                    initialInterestRate={editingVault?.interestRate || 0}
                    initialDescription={editingVault?.description || ''}
                    initialBankId={editingVault?.bankId || ''}
                    initialAccountType={editingVault?.accountType || 'SAVINGS'}
                    initialCloseDate={editingVault?.closeDate || ''}
                    isEditing={!!editingVault}
                />
            </div>
        </div>
    );
}

export default VaultsPage;