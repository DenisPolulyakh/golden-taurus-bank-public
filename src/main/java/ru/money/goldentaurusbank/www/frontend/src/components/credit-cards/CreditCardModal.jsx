import { useState, useEffect } from 'react';
import api from '../../api/axios';
import { formatAmount } from './creditCardFormat';

/**
 * Заведение и правка карты. Номер при правке приходит пустым: наружу его не
 * отдают, и пустое поле означает «оставить прежний».
 */
function CreditCardModal({ isOpen, card, onClose, onSave }) {
    const isEditing = !!card;

    const [name, setName] = useState('');
    const [cardNumber, setCardNumber] = useState('');
    const [gracePeriodDate, setGracePeriodDate] = useState('');
    const [limit, setLimit] = useState('');
    const [debt, setDebt] = useState('');
    const [bullionIds, setBullionIds] = useState([]);
    const [availableBullions, setAvailableBullions] = useState([]);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (!isOpen) return;

        setName(card?.name || '');
        setCardNumber('');
        setGracePeriodDate(card?.gracePeriodDate || '');
        setLimit(card?.limit ?? '');
        setDebt(card?.debt ?? '');
        setBullionIds((card?.accumulators || []).map(item => item.bullionId));

        const params = card ? `?cardId=${card.id}` : '';
        api.get(`/credit-cards/available-bullions${params}`)
            .then(response => setAvailableBullions(response.data.data || []))
            .catch(err => console.error('Ошибка загрузки слитков:', err));
    }, [isOpen, card]);

    if (!isOpen) return null;

    const toggleBullion = (bullionId) => {
        setBullionIds(prev => prev.includes(bullionId)
            ? prev.filter(id => id !== bullionId)
            : [...prev, bullionId]);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setSaving(true);
        try {
            await onSave({ name, cardNumber, gracePeriodDate, limit, debt, bullionIds });
        } catch (err) {
            // Текст ошибки показывает общий обработчик axios — форму не закрываем
            console.error('Ошибка сохранения карты:', err);
        } finally {
            setSaving(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>{isEditing ? '✏️ Редактировать карту' : '💳 Новая кредитная карта'}</h2>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className="modal-body modal-body-scrollable">
                        <div className="form-group">
                            <label>Название карты</label>
                            <input
                                type="text"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                placeholder="Например, Тинькофф Платинум"
                                required
                                autoFocus
                            />
                        </div>

                        <div className="form-group">
                            <label>Номер карты</label>
                            <input
                                type="text"
                                inputMode="numeric"
                                value={cardNumber}
                                onChange={(e) => setCardNumber(e.target.value)}
                                placeholder={isEditing ? card.maskedNumber : '4276 1600 1234 5678'}
                                required={!isEditing}
                            />
                            <span className="input-hint">
                                {isEditing
                                    ? 'Оставьте пустым, чтобы не менять номер'
                                    : 'В базе хранится зашифрованным, на экране — маской'}
                            </span>
                        </div>

                        <div className="form-group">
                            <label>Льготный период до</label>
                            <input
                                type="date"
                                value={gracePeriodDate}
                                onChange={(e) => setGracePeriodDate(e.target.value)}
                            />
                        </div>

                        <div className="form-group">
                            <label>Лимит</label>
                            <input
                                type="number"
                                step="0.01"
                                min="0"
                                value={limit}
                                onChange={(e) => setLimit(e.target.value)}
                                placeholder="0"
                            />
                        </div>

                        <div className="form-group">
                            <label>Задолженность</label>
                            <input
                                type="number"
                                step="0.01"
                                min="0"
                                value={debt}
                                onChange={(e) => setDebt(e.target.value)}
                                placeholder="0"
                            />
                            <span className="input-hint">
                                Изменение задолженности записывается операцией и попадает в историю карты
                            </span>
                        </div>

                        <div className="form-group">
                            <label>Накопитель</label>
                            {availableBullions.length === 0 ? (
                                <div className="accumulator-empty">
                                    Нет свободных кредитных слитков — заведите слиток с типом «Кредитный»
                                </div>
                            ) : (
                                <div className="bullion-picker">
                                    {availableBullions.map(bullion => (
                                        <label key={bullion.bullionId} className="bullion-option">
                                            <input
                                                type="checkbox"
                                                checked={bullionIds.includes(bullion.bullionId)}
                                                onChange={() => toggleBullion(bullion.bullionId)}
                                            />
                                            <span
                                                className="accumulator-bullion"
                                                style={bullion.bullionNameColor ? { color: bullion.bullionNameColor } : undefined}
                                            >
                                                {bullion.bullionNameTitle}
                                            </span>
                                            <span className="bullion-option-vault">| {bullion.vaultName}</span>
                                            <span className="bullion-option-amount">{formatAmount(bullion.amount)} ₽</span>
                                        </label>
                                    ))}
                                </div>
                            )}
                            <span className="input-hint">
                                В списке только кредитные слитки, не занятые другими картами
                            </span>
                        </div>
                    </div>

                    <div className="modal-footer">
                        <button type="button" className="cancel-btn" onClick={onClose}>
                            Отмена
                        </button>
                        <button type="submit" className="add-bullion-btn" disabled={saving}>
                            {saving ? 'Сохранение...' : (isEditing ? 'Сохранить' : 'Добавить')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

export default CreditCardModal;
