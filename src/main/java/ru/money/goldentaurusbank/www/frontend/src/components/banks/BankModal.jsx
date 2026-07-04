import { useState, useEffect } from 'react';
import './Banks.css';

function BankModal({ isOpen, onClose, onSave, initialName, isEditing }) {
    const [name, setName] = useState('');
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (isOpen) {
            setName(initialName);
            setError('');
        }
    }, [isOpen, initialName]);

    const handleSubmit = async (e) => {
        e.preventDefault();

        const trimmedName = name.trim();
        if (!trimmedName) {
            setError('Название банка обязательно');
            return;
        }
        if (trimmedName.length < 2) {
            setError('Название должно содержать минимум 2 символа');
            return;
        }
        if (trimmedName.length > 100) {
            setError('Название не должно превышать 100 символов');
            return;
        }

        setSaving(true);
        setError('');

        try {
            await onSave(trimmedName);
            onClose();
        } catch (err) {
            setError(err.response?.data?.message || 'Ошибка сохранения банка');
        } finally {
            setSaving(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>{isEditing ? 'Редактирование банка' : 'Добавление банка'}</h2>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        <div className="form-group">
                            <label>Название банка</label>
                            <input
                                type="text"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                placeholder="например: Сбербанк, Тинькофф, Альфа-Банк"
                                autoFocus
                            />
                            <small className="input-hint">
                                Минимум 2 символа, максимум 100
                            </small>
                        </div>
                        {error && <div className="error-message">{error}</div>}
                    </div>

                    <div className="modal-footer">
                        <button type="button" onClick={onClose} className="cancel-btn">
                            Отмена
                        </button>
                        <button type="submit" disabled={saving} className="save-btn">
                            {saving ? 'Сохранение...' : 'Сохранить'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

export default BankModal;