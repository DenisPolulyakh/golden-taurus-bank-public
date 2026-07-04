// components/banks/BankDetailPage.jsx
import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import api from '../../api/axios';
import './BankDetailPage.css';

function BankDetailPage() {
    const { bankId } = useParams();
    const navigate = useNavigate();

    const [bankData, setBankData] = useState(null);
    const [vaults, setVaults] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [sortField, setSortField] = useState('name');
    const [sortOrder, setSortOrder] = useState('asc');
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [totalAmount, setTotalAmount] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        fetchBankData();
    }, [bankId, searchTerm, sortField, sortOrder, currentPage, pageSize]);

    const fetchBankData = async () => {
        setLoading(true);
        try {
            const response = await api.get(`/banks/${bankId}`, {
                params: {
                    search: searchTerm || undefined,
                    sortBy: sortField,
                    sortOrder: sortOrder,
                    page: currentPage,
                    size: pageSize
                }
            });

            const data = response.data.data;
            setBankData(data);
            setVaults(data.vaults?.content || []);
            setTotalAmount(data.totalAmount || 0);
            setTotalPages(data.vaults?.totalPages || 0);
            setTotalElements(data.vaults?.totalElements || 0);
        } catch (err) {
            console.error('Ошибка загрузки данных банка:', err);
            setError('Не удалось загрузить данные банка');
        } finally {
            setLoading(false);
        }
    };

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

    const handleVaultClick = (vaultId) => {
        navigate(`/vaults/${vaultId}`, { state: { from: 'banks' } });
    };

    const handleSort = (field) => {
        if (sortField === field) {
            setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
        } else {
            setSortField(field);
            setSortOrder('asc');
        }
        setCurrentPage(1);
    };

    const toggleSort = handleSort;

    if (loading) {
        return (
            <div className="bank-detail-container">
                <div className="loading-spinner">Загрузка...</div>
            </div>
        );
    }

    if (error || !bankData) {
        return (
            <div className="bank-detail-container">
                <div className="error-message">{error || 'Банк не найден'}</div>
                <button onClick={() => navigate('/vaults')} className="bank-back-btn">
                    ← Назад к хранилищам
                </button>
            </div>
        );
    }

    return (
        <div className="bank-detail-container">
            <div className="bank-detail-content">
                <div className="bank-detail-header">
                    <div className="header-left">
                        <h1>🏛️ {bankData.bankName}</h1>
                    </div>
                    <div className="header-actions">
                        <button onClick={() => navigate('/vaults')} className="bank-back-btn">
                            ← Назад
                        </button>
                    </div>
                </div>

                <div className="bank-stats-cards">
                    <div className="stat-card total-card">
                        <div className="stat-label">💰 Общая сумма в банке</div>
                        <div className="stat-value large">{formatAmount(totalAmount)} ₽</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-label">🏦 Количество хранилищ</div>
                        <div className="stat-value">{totalElements}</div>
                    </div>
                </div>

                <div className="controls-bar">
                    <div className="search-bar">
                        <input
                            type="text"
                            placeholder="🔍 Поиск хранилищ в банке..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                    </div>
                    <div className="sort-buttons">
                        <button
                            className={`sort-btn ${sortField === 'name' ? 'active' : ''}`}
                            onClick={() => toggleSort('name')}
                        >
                            По названию {sortField === 'name' && (sortOrder === 'asc' ? '↑' : '↓')}
                        </button>
                        <button
                            className={`sort-btn ${sortField === 'totalAmount' ? 'active' : ''}`}
                            onClick={() => toggleSort('totalAmount')}
                        >
                            По сумме {sortField === 'totalAmount' && (sortOrder === 'asc' ? '↑' : '↓')}
                        </button>
                        <button
                            className={`sort-btn ${sortField === 'interestRate' ? 'active' : ''}`}
                            onClick={() => toggleSort('interestRate')}
                        >
                            По ставке {sortField === 'interestRate' && (sortOrder === 'asc' ? '↑' : '↓')}
                        </button>
                    </div>
                </div>

                <div className="vaults-grid">
                    {vaults.length === 0 ? (
                        <div className="empty-state">
                            {searchTerm ? 'Ничего не найдено' : 'Нет хранилищ в этом банке'}
                        </div>
                    ) : (
                        vaults.map((vault) => (
                            <div key={vault.id} className="vault-card">
                                <div className="vault-card-header">
                                    <h3
                                        className="vault-card-title"
                                        onClick={() => handleVaultClick(vault.id)}
                                    >
                                        {vault.name}
                                    </h3>
                                    <span className="vault-badge">
                                        {vault.accountType === 'SAVINGS' ? 'Накопительный' : 'Срочный'}
                                    </span>
                                </div>
                                <div className="vault-card-body">
                                    <div className="vault-stat">
                                        <span className="stat-name">💰 Сумма:</span>
                                        <span className="stat-value">{formatAmount(vault.totalAmount)} ₽</span>
                                    </div>
                                    <div className="vault-stat">
                                        <span className="stat-name">📈 Ставка:</span>
                                        <span className="stat-value">{vault.interestRate}%</span>
                                    </div>
                                    {vault.closeDate && vault.accountType === 'TERM' && (
                                        <div className="vault-stat">
                                            <span className="stat-name">📅 Закрытие:</span>
                                            <span className="stat-value">
                                                {new Date(vault.closeDate).toLocaleDateString('ru-RU')}
                                            </span>
                                        </div>
                                    )}
                                    {vault.description && (
                                        <div className="vault-card-description">
                                            {vault.description}
                                        </div>
                                    )}
                                </div>
                            </div>
                        ))
                    )}
                </div>

                {totalPages > 1 && (
                    <div className="pagination">
                        <button
                            onClick={() => setCurrentPage(Math.max(1, currentPage - 1))}
                            disabled={currentPage === 1}
                            className="page-btn"
                        >
                            ← Назад
                        </button>
                        <span className="page-info">
                            Страница {currentPage} из {totalPages} (всего {totalElements})
                        </span>
                        <button
                            onClick={() => setCurrentPage(Math.min(totalPages, currentPage + 1))}
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
            </div>
        </div>
    );
}

export default BankDetailPage;