import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../../api/axios';
import './TransactionHistoryPage.css';

const TransactionHistoryPage = () => {
    const [transactions, setTransactions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [filters, setFilters] = useState({
        operationType: '',
        status: '',
        fromDate: '',
        toDate: ''
    });
    const [showFilters, setShowFilters] = useState(false);

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

            if (filters.operationType) params.operationType = filters.operationType;
            if (filters.status) params.status = filters.status;
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

    const handleRollback = async (id) => {
        if (!window.confirm('Вы уверены, что хотите откатить эту транзакцию?')) return;
        try {
            await api.post(`/transactions/${id}/rollback`);
            fetchTransactions();
        } catch (err) {
            console.error('Rollback failed:', err);
            alert('Ошибка при откате: ' + (err.response?.data?.message || err.message));
        }
    };

    const getOperationTypeLabel = (type) => {
        const labels = {
            'REFILL_BULLION': 'Пополнение',
            'WITHDRAW_BULLION': 'Списание',
            'TRANSFER_AMOUNT': 'Перевод средств',
            'TRANSFER_BULLION': 'Перемещение слитка'
        };
        return labels[type] || type;
    };

    const formatDate = (date) => {
        if (!date) return '-';
        return new Date(date).toLocaleDateString('ru-RU', {
            day: '2-digit', month: '2-digit', year: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    };

    const formatAmount = (amount) => {
        if (!amount) return '0 ₽';
        return new Intl.NumberFormat('ru-RU', {
            style: 'currency',
            currency: 'RUB',
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        }).format(amount);
    };

    if (loading && page === 0) {
        return <div className="history-loading">Загрузка истории...</div>;
    }

    return (
        <div className="history-page">
            <div className="history-header">
                <div className="history-header-left">
                    <Link to="/dashboard" className="back-btn">
                        ← Назад
                    </Link>
                    <h1>📜 История операций</h1>
                </div>
                <div className="history-header-right">
                    <button
                        onClick={() => setShowFilters(!showFilters)}
                        className="filter-toggle-btn"
                    >
                        {showFilters ? '🔽 Скрыть фильтры' : '🔼 Показать фильтры'}
                    </button>
                    <button
                        onClick={() => {
                            setFilters({ operationType: '', status: '', fromDate: '', toDate: '' });
                            setPage(0);
                        }}
                        className="reset-btn"
                    >
                        Сбросить
                    </button>
                    <button
                        onClick={fetchTransactions}
                        className="refresh-btn"
                    >
                        🔄 Обновить
                    </button>
                </div>
            </div>

            {showFilters && (
                <div className="filters-panel">
                    <select
                        value={filters.operationType}
                        onChange={(e) => setFilters({ ...filters, operationType: e.target.value })}
                        className="filter-select"
                    >
                        <option value="">Все типы</option>
                        <option value="REFILL_BULLION">Пополнение</option>
                        <option value="WITHDRAW_BULLION">Списание</option>
                        <option value="TRANSFER_AMOUNT">Перевод средств</option>
                        <option value="TRANSFER_BULLION">Перемещение слитка</option>
                    </select>

                    <select
                        value={filters.status}
                        onChange={(e) => setFilters({ ...filters, status: e.target.value })}
                        className="filter-select"
                    >
                        <option value="">Все статусы</option>
                        <option value="SUCCESS">Успешно</option>
                        <option value="FAILED">Ошибка</option>
                        <option value="ROLLED_BACK">Откатано</option>
                    </select>

                    <input
                        type="date"
                        value={filters.fromDate}
                        onChange={(e) => setFilters({ ...filters, fromDate: e.target.value })}
                        className="filter-input"
                    />
                    <input
                        type="date"
                        value={filters.toDate}
                        onChange={(e) => setFilters({ ...filters, toDate: e.target.value })}
                        className="filter-input"
                    />
                </div>
            )}

            <div className="history-table-wrapper">
                <table className="history-table">
                    <thead>
                    <tr>
                        <th>Дата</th>
                        <th>Тип</th>
                        <th>Сумма</th>
                        <th>Описание</th>
                        <th>Действия</th>
                    </tr>
                    </thead>
                    <tbody>
                    {transactions.map((tx, index) => {
                        // Показываем кнопку отката только если транзакция успешна
                        const canRollback = tx.status === 'SUCCESS' && !tx.canRollback === false;

                        return (
                            <tr key={tx.id} className={index % 2 === 0 ? 'even-row' : 'odd-row'}>
                                <td>{formatDate(tx.createdAt)}</td>
                                <td>
                    <span className={`type-badge type-${tx.operationType.toLowerCase()}`}>
                      {getOperationTypeLabel(tx.operationType)}
                    </span>
                                </td>
                                <td className={`amount-cell ${tx.operationType === 'REFILL_BULLION' ? 'income' : tx.operationType === 'WITHDRAW_BULLION' ? 'expense' : 'transfer'}`}>
                                    {formatAmount(tx.amount)}
                                </td>
                                <td className="description-cell">{tx.description || tx.userComment || '-'}</td>
                                <td className="actions-cell">
                                    {tx.status === 'SUCCESS' && (
                                        <button
                                            onClick={() => handleRollback(tx.id)}
                                            className="rollback-btn"
                                            title="Откатить"
                                        >
                                            ↩️ Откатить
                                        </button>
                                    )}
                                    {tx.status === 'ROLLED_BACK' && (
                                        <span className="rolled-back-label">Откатано</span>
                                    )}
                                </td>
                            </tr>
                        );
                    })}
                    </tbody>
                </table>
            </div>

            {transactions.length === 0 && (
                <div className="no-data">Нет транзакций</div>
            )}

            {totalPages > 1 && (
                <div className="pagination">
                    <button
                        onClick={() => setPage(Math.max(0, page - 1))}
                        disabled={page === 0}
                        className="page-btn"
                    >
                        ← Назад
                    </button>
                    <span className="page-info">
            {page + 1} / {totalPages}
          </span>
                    <button
                        onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                        disabled={page === totalPages - 1}
                        className="page-btn"
                    >
                        Вперед →
                    </button>
                </div>
            )}

            <div className="total-info">
                Всего: {totalElements} транзакций
            </div>
        </div>
    );
};

export default TransactionHistoryPage;