import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { toast } from 'sonner';
import api from '../../api/axios';
import './TransactionHistoryPage.css';

const TransactionHistoryPage = () => {
    const [transactions, setTransactions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [totalElements, setTotalElements] = useState(0);
    const [filters, setFilters] = useState({
        kind: '',
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

            if (filters.kind) params.kind = filters.kind;
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
            toast.success('Транзакция откачена');
            fetchTransactions();
        } catch (err) {
            console.error('Rollback failed:', err);
        }
    };

    // Вид операции выводится бэкендом из того, какие ноги заполнены.
    const KIND_LABELS = {
        DEPOSIT: 'Пополнение',
        WITHDRAWAL: 'Списание',
        TRANSFER: 'Перевод',
        OPENING_BALANCE: 'Начальный остаток'
    };

    // Перевод и начальный остаток накопления не двигают — красим их нейтрально.
    const KIND_AMOUNT_CLASS = {
        DEPOSIT: 'income',
        WITHDRAWAL: 'expense',
        TRANSFER: 'transfer',
        OPENING_BALANCE: 'neutral'
    };

    const getKindLabel = (kind) => KIND_LABELS[kind] || kind || '—';

    const getKindClass = (kind) => (kind ? `type-${kind.toLowerCase()}` : 'type-unknown');

    const formatDate = (date) => {
        if (!date) return '-';
        const d = new Date(date);
        const day = String(d.getDate()).padStart(2, '0');
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const year = d.getFullYear();
        const hour = String(d.getHours()).padStart(2, '0');
        const minute = String(d.getMinutes()).padStart(2, '0');
        return `${day}.${month}.${year} ${hour}:${minute}`;
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

    const renderDescription = (tx) => {
        const segments = tx.descriptionSegments;
        if (Array.isArray(segments) && segments.length > 0) {
            // seg.archived — хранилища больше нет: цвет остаётся своим, но гаснет
            return segments.map((seg, i) => (
                <span
                    key={i}
                    className={seg.archived ? 'segment-archived' : undefined}
                    title={seg.archived ? 'Хранилище удалено' : undefined}
                    style={seg.color ? { color: seg.color, fontWeight: 600 } : undefined}
                >
                    {seg.text}
                </span>
            ));
        }
        return tx.description || tx.comment || '-';
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
                            setFilters({ kind: '', fromDate: '', toDate: '' });
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
                        value={filters.kind}
                        onChange={(e) => setFilters({ ...filters, kind: e.target.value })}
                        className="filter-select"
                    >
                        <option value="">Все типы</option>
                        <option value="DEPOSIT">Пополнение</option>
                        <option value="WITHDRAWAL">Списание</option>
                        <option value="TRANSFER">Перевод</option>
                        <option value="OPENING_BALANCE">Начальный остаток</option>
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
                    {transactions.map((tx, index) => (
                        <tr key={tx.id} className={index % 2 === 0 ? 'even-row' : 'odd-row'}>
                            <td>{formatDate(tx.dateOperation)}</td>
                            <td>
                                <span className={`type-badge ${getKindClass(tx.kind)}`}>
                                    {getKindLabel(tx.kind)}
                                </span>
                                {tx.reversalOfId && (
                                    <span className="reversal-label" title={`Откат операции #${tx.reversalOfId}`}>
                                        откат
                                    </span>
                                )}
                            </td>
                            <td className={`amount-cell ${KIND_AMOUNT_CLASS[tx.kind] || ''}`}>
                                {formatAmount(tx.amount)}
                            </td>
                            <td className="description-cell">{renderDescription(tx)}</td>
                            <td className="actions-cell">
                                {tx.canRollback ? (
                                    <button
                                        onClick={() => handleRollback(tx.id)}
                                        className="rollback-btn"
                                        title="Откатить"
                                    >
                                        ↩️ Откатить
                                    </button>
                                ) : (
                                    <span className="rolled-back-label">Откачена</span>
                                )}
                            </td>
                        </tr>
                    ))}
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