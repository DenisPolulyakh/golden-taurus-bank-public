import { useState, useEffect, useCallback } from 'react';
import { toast } from 'sonner';
import api from '../../api/axios';
import { formatAmount, formatDateTime } from './creditCardFormat';

const OPERATION_LABELS = {
    SPEND: '💸 Списание',
    REPAY: '💰 Погашение',
    OPENING_DEBT: '📌 Начальный долг'
};

function CreditCardHistoryModal({ isOpen, card, onClose, onRolledBack }) {
    const [operations, setOperations] = useState([]);
    const [loading, setLoading] = useState(true);
    const [rollbackId, setRollbackId] = useState(null);

    const fetchHistory = useCallback(async () => {
        setLoading(true);
        try {
            const response = await api.get(`/credit-cards/${card.id}/history`);
            setOperations(response.data.data || []);
        } catch (err) {
            console.error('Ошибка загрузки истории карты:', err);
        } finally {
            setLoading(false);
        }
    }, [card.id]);

    useEffect(() => {
        if (isOpen) fetchHistory();
    }, [isOpen, fetchHistory]);

    if (!isOpen) return null;

    const handleRollback = async (operation) => {
        const extra = operation.bullionTransactionId
            ? ' Деньги вернутся в слиток-накопитель.'
            : '';
        if (!window.confirm(`Откатить операцию на ${formatAmount(operation.amount)} ₽?${extra}`)) {
            return;
        }

        setRollbackId(operation.id);
        try {
            await api.post(`/credit-cards/history/${operation.id}/rollback`);
            toast.success('Операция откачена');
            await fetchHistory();
            if (onRolledBack) onRolledBack();
        } catch (err) {
            console.error('Ошибка отката операции:', err);
        } finally {
            setRollbackId(null);
        }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content history-modal" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>📜 История карты «{card.name}»</h2>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>

                <div className="modal-body modal-body-scrollable">
                    {loading ? (
                        <div className="empty-state">Загрузка...</div>
                    ) : operations.length === 0 ? (
                        <div className="empty-state">По этой карте ещё не было операций</div>
                    ) : (
                        <table className="card-history-table">
                            <thead>
                            <tr>
                                <th>Дата</th>
                                <th>Операция</th>
                                <th>Сумма</th>
                                <th>Долг после</th>
                                <th></th>
                            </tr>
                            </thead>
                            <tbody>
                            {operations.map(operation => (
                                <tr key={operation.id} className={operation.reversalOfId ? 'reversal-row' : undefined}>
                                    <td>{formatDateTime(operation.dateOperation)}</td>
                                    <td>
                                        <div>{OPERATION_LABELS[operation.operation] || operation.operation}</div>
                                        {operation.comment && (
                                            <div className="operation-comment">{operation.comment}</div>
                                        )}
                                    </td>
                                    <td className={Number(operation.signedAmount) > 0 ? 'debt-up' : 'debt-down'}>
                                        {Number(operation.signedAmount) > 0 ? '+' : '−'}
                                        {formatAmount(operation.amount)} ₽
                                    </td>
                                    <td>{formatAmount(operation.debtAfter)} ₽</td>
                                    <td>
                                        {operation.canRollback ? (
                                            <button
                                                className="rollback-btn"
                                                onClick={() => handleRollback(operation)}
                                                disabled={rollbackId === operation.id}
                                            >
                                                ↩ Откатить
                                            </button>
                                        ) : (
                                            <span className="rollback-done">
                                                {operation.reversalOfId ? 'откат' : 'откачена'}
                                            </span>
                                        )}
                                    </td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    )}
                </div>

                <div className="modal-footer">
                    <button type="button" className="cancel-btn" onClick={onClose}>
                        Закрыть
                    </button>
                </div>
            </div>
        </div>
    );
}

export default CreditCardHistoryModal;
