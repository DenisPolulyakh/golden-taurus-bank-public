import { useState, useEffect } from 'react';
import { formatAmount } from './creditCardFormat';

/**
 * Списание и погашение по карте, а также погашение из накопителя (тип
 * {@code repay-bullion}) — форма у всех троих одна: сумма, дата, комментарий.
 */
function CreditCardOperationModal({ isOpen, card, type, bullion, onClose, onSave }) {
    const [amount, setAmount] = useState('');
    const [comment, setComment] = useState('');
    const [dateOperation, setDateOperation] = useState('');
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (!isOpen) return;
        setAmount('');
        setComment('');
        const now = new Date();
        now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
        setDateOperation(now.toISOString().slice(0, 16));
    }, [isOpen]);

    if (!isOpen) return null;

    const isSpend = type === 'spend';
    const fromBullion = type === 'repay-bullion';

    // Потолок суммы: списание упирается в остаток лимита, погашение — в долг,
    // погашение из накопителя — ещё и в сумму самого слитка
    const max = isSpend
        ? Number(card.remainder || 0)
        : Math.min(Number(card.debt || 0), fromBullion ? Number(bullion?.amount || 0) : Number.MAX_SAFE_INTEGER);

    const title = isSpend
        ? `Списание по карте «${card.name}»`
        : fromBullion
            ? `Погашение карты «${card.name}» из накопителя`
            : `Погашение по карте «${card.name}»`;

    const handleSubmit = async (e) => {
        e.preventDefault();
        setSaving(true);
        try {
            await onSave({ amount, comment, dateOperation: dateOperation ? `${dateOperation}:00` : null });
        } catch (err) {
            console.error('Ошибка операции по карте:', err);
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content transaction-modal" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>{isSpend ? '💸' : '💰'} {title}</h2>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        <div className="operation-summary">
                            <div className="stat">
                                <span className="stat-name">Задолженность:</span>
                                <span className="stat-description debt-value">{formatAmount(card.debt)} ₽</span>
                            </div>
                            <div className="stat">
                                <span className="stat-name">{isSpend ? 'Доступно по лимиту:' : 'Остаток лимита:'}</span>
                                <span className="stat-description">{formatAmount(card.remainder)} ₽</span>
                            </div>
                            {fromBullion && (
                                <div className="stat">
                                    <span className="stat-name">В слитке «{bullion?.bullionNameTitle}»:</span>
                                    <span className="stat-description">{formatAmount(bullion?.amount)} ₽</span>
                                </div>
                            )}
                        </div>

                        <div className="form-group">
                            <label>💰 Сумма</label>
                            <input
                                type="number"
                                step="0.01"
                                min="0.01"
                                max={max > 0 ? max : undefined}
                                value={amount}
                                onChange={(e) => setAmount(e.target.value)}
                                required
                                autoFocus
                            />
                            <div className="quick-amount-row">
                                <button
                                    type="button"
                                    className="quick-amount-btn quick-amount-btn-all"
                                    onClick={() => setAmount(String(max))}
                                    disabled={max <= 0}
                                >
                                    {isSpend ? 'Весь остаток' : 'Весь долг'}
                                </button>
                            </div>
                            {fromBullion && (
                                <span className="input-hint">
                                    Одной операцией: деньги уйдут со слитка и уменьшат задолженность карты
                                </span>
                            )}
                        </div>

                        <div className="form-group">
                            <label>📅 Дата операции</label>
                            <input
                                type="datetime-local"
                                value={dateOperation}
                                onChange={(e) => setDateOperation(e.target.value)}
                            />
                        </div>

                        <div className="form-group">
                            <label>📝 Комментарий</label>
                            <textarea
                                rows={2}
                                value={comment}
                                onChange={(e) => setComment(e.target.value)}
                                placeholder="Необязательно..."
                            />
                        </div>
                    </div>

                    <div className="modal-footer">
                        <button type="button" className="cancel-btn" onClick={onClose}>
                            Отмена
                        </button>
                        <button
                            type="submit"
                            className={isSpend ? 'withdraw-btn' : 'refill-btn'}
                            disabled={saving}
                        >
                            {saving ? 'Сохранение...' : (isSpend ? 'Списать' : 'Погасить')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

export default CreditCardOperationModal;
