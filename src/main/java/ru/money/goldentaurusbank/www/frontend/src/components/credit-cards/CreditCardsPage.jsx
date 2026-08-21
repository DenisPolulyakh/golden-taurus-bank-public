import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { toast } from 'sonner';
import api from '../../api/axios';
import CreditCardModal from './CreditCardModal';
import CreditCardOperationModal from './CreditCardOperationModal';
import CreditCardHistoryModal from './CreditCardHistoryModal';
import { formatAmount, formatSigned, formatDate, graceClass, graceText, limitUsage, usageClass, usageText } from './creditCardFormat';
import './CreditCards.css';

function CreditCardsPage() {
    const [cards, setCards] = useState([]);
    const [totalDebt, setTotalDebt] = useState(0);
    const [totalLimit, setTotalLimit] = useState(0);
    const [count, setCount] = useState(0);
    const [searchTerm, setSearchTerm] = useState('');
    // По умолчанию — ближайший конец льготного периода: сверху карта,
    // которую гасить раньше всех
    const [sortConfig, setSortConfig] = useState({ field: 'grace', order: 'asc' });
    const [loading, setLoading] = useState(true);
    const [actionLoading, setActionLoading] = useState(false);
    const [error, setError] = useState('');

    const [modalOpen, setModalOpen] = useState(false);
    const [editingCard, setEditingCard] = useState(null);
    const [operation, setOperation] = useState(null);
    const [historyCard, setHistoryCard] = useState(null);

    const navigate = useNavigate();
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
            limit: form.limit === '' ? 0 : Number(form.limit),
            debt: form.debt === '' ? 0 : Number(form.debt),
            bullionIds: form.bullionIds
        };

        if (editingCard) {
            await api.put(`/credit-cards/${editingCard.id}`, payload);
        } else {
            await api.post('/credit-cards', payload);
        }

        setModalOpen(false);
        setEditingCard(null);
        await reload();
    };

    const handleDeleteCard = async (card) => {
        if (!window.confirm(`Удалить карту "${card.name}"? История операций сохранится.`)) {
            return;
        }

        setActionLoading(true);
        try {
            await api.delete(`/credit-cards/${card.id}`);
            toast.success('Кредитная карта удалена');
            await reload();
        } catch (err) {
            console.error('Ошибка удаления карты:', err);
        } finally {
            setActionLoading(false);
        }
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

    if (loading) {
        return <div className="credit-cards-container">Загрузка...</div>;
    }

    const usage = limitUsage(totalDebt, totalLimit);

    return (
        <div className="credit-cards-container">
            <div className="credit-cards-content">
                <div className="bullions-header">
                    <h1>💳 Кредитные карты</h1>
                    <div className="header-actions">
                        <button onClick={() => navigate('/dashboard')} className="back-btn">
                            ← Назад
                        </button>
                        <button
                            onClick={() => { setEditingCard(null); setModalOpen(true); }}
                            className="add-bullion-btn"
                        >
                            + Добавить карту
                        </button>
                    </div>
                </div>

                <div className="stats-cards">
                    <div className="stat-card">
                        <div className="stat-label">💸 Текущий долг</div>
                        <div className="stat-value total-debt-value">{formatAmount(totalDebt)} ₽</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-label">💳 Общий лимит</div>
                        <div className="stat-value total-limit-value">{formatAmount(totalLimit)} ₽</div>
                        <div className="limit-usage">
                            Лимит использован на:{' '}
                            <span className={`limit-usage-value ${usageClass(usage)}`}>{usageText(usage)}</span>
                        </div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-label">💳 Количество кредитных карт</div>
                        <div className="stat-value">{count}</div>
                    </div>
                </div>

                <div className="controls-bar">
                    <div className="search-bar">
                        <input
                            type="text"
                            placeholder="🔍 Поиск по названию или последним 4 цифрам..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                        />
                    </div>
                    <div className="sort-buttons">
                        <SortButton label="По остатку дней" field="grace" sortConfig={sortConfig} onSort={handleSort} />
                        <SortButton label="По задолженности" field="debt" sortConfig={sortConfig} onSort={handleSort} />
                        <SortButton label="По лимиту" field="limit" sortConfig={sortConfig} onSort={handleSort} />
                        <SortButton label="По остатку" field="remainder" sortConfig={sortConfig} onSort={handleSort} />
                    </div>
                </div>

                {error && <div className="error-message">{error}</div>}

                <div className="credit-cards-grid">
                    {cards.length === 0 ? (
                        <div className="empty-state">
                            {searchTerm ? 'Ничего не найдено' : 'Нет кредитных карт. Добавьте первую!'}
                        </div>
                    ) : (
                        cards.map(card => (
                            <CreditCard
                                key={card.id}
                                card={card}
                                disabled={actionLoading}
                                onSpend={() => setOperation({ type: 'spend', card })}
                                onRepay={() => setOperation({ type: 'repay', card })}
                                onHistory={() => setHistoryCard(card)}
                                onEdit={() => { setEditingCard(card); setModalOpen(true); }}
                                onDelete={() => handleDeleteCard(card)}
                            />
                        ))
                    )}
                </div>

                {modalOpen && (
                    <CreditCardModal
                        isOpen={modalOpen}
                        card={editingCard}
                        onClose={() => { setModalOpen(false); setEditingCard(null); }}
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
            </div>
        </div>
    );
}

const SortButton = ({ label, field, sortConfig, onSort }) => {
    const isActive = sortConfig.field === field;
    const arrow = isActive ? (sortConfig.order === 'asc' ? '↑' : '↓') : '';

    return (
        <button className={`sort-btn ${isActive ? 'active' : ''}`} onClick={() => onSort(field)}>
            {label} {arrow}
        </button>
    );
};

const CreditCard = ({ card, disabled, onSpend, onRepay, onHistory, onEdit, onDelete }) => {
    const imbalance = Number(card.imbalance || 0);
    const accumulators = card.accumulators || [];

    return (
        <div className="bullion-card credit-card">
            <div className="card-header">
                <h3>💳 {card.name}</h3>
            </div>
            <div className="card-body">
                <div className="card-stats">
                    <div className="stat">
                        <span className="stat-name">Номер карты:</span>
                        <span className="stat-description card-number">{card.maskedNumber}</span>
                    </div>
                    <div className="stat">
                        <span className="stat-name">Льготный период до:</span>
                        <span className="stat-description">
                            {card.gracePeriodDate ? formatDate(card.gracePeriodDate) : '—'}
                        </span>
                    </div>
                    <div className="stat">
                        <span className="stat-name">Осталось:</span>
                        <span className={`stat-description grace-left ${graceClass(card.graceDaysLeft)}`}>
                            {graceText(card.graceDaysLeft)}
                        </span>
                    </div>
                    <div className="stat">
                        <span className="stat-name">Лимит:</span>
                        <span className="stat-description">{formatAmount(card.limit)} ₽</span>
                    </div>
                    <div className="stat">
                        <span className="stat-name">Задолженность:</span>
                        <span className="stat-description debt-value">{formatAmount(card.debt)} ₽</span>
                    </div>
                    <div className="stat">
                        <span className="stat-name">Остаток:</span>
                        <span className="stat-description">{formatAmount(card.remainder)} ₽</span>
                    </div>
                    <div className="stat">
                        <span className="stat-name">Дисбаланс:</span>
                        <span className={`stat-description imbalance ${imbalance < 0 ? 'imbalance-negative' : 'imbalance-positive'}`}>
                            {formatSigned(card.imbalance)} ₽
                        </span>
                    </div>

                    <div className="accumulators">
                        <span className="stat-name">Накопитель:</span>
                        {accumulators.length === 0 ? (
                            <div className="accumulator-empty">
                                Необходимо выбрать слиток для накопления
                            </div>
                        ) : (
                            <div className="accumulator-list">
                                {accumulators.map(item => (
                                    <span key={item.bullionId} className="accumulator-item">
                                        <span
                                            className="accumulator-bullion"
                                            style={item.bullionNameColor ? { color: item.bullionNameColor } : undefined}
                                        >
                                            {item.bullionNameTitle}
                                        </span>
                                        {' | '}
                                        <Link to={`/vaults/${item.vaultId}`} className="accumulator-vault">
                                            {item.vaultName}
                                        </Link>
                                    </span>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </div>
            <div className="card-actions-horizontal">
                <button className="withdraw-btn" onClick={onSpend} disabled={disabled}>
                    💸 Списание
                </button>
                <button className="refill-btn" onClick={onRepay} disabled={disabled || Number(card.debt) <= 0}>
                    💰 Погашение
                </button>
                <button className="history-btn" onClick={onHistory} disabled={disabled}>
                    📜 История
                </button>
                <button className="edit-vault-btn" onClick={onEdit} disabled={disabled}>
                    ✏️ Редактировать
                </button>
                <button className="delete-vault-btn" onClick={onDelete} disabled={disabled}>
                    🗑️ Удалить
                </button>
            </div>
        </div>
    );
};

export default CreditCardsPage;
