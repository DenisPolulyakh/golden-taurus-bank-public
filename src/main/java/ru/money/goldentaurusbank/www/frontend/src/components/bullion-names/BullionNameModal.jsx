import { useState, useEffect } from 'react';
import api from '../../api/axios';
import './BullionNames.css';

function BullionNameModal({ isOpen, onClose, onSave, initialTitle, initialColor, isEditing }) {
    const [title, setTitle] = useState('');
    const [selectedColor, setSelectedColor] = useState('');
    const [availableColors, setAvailableColors] = useState([]);
    const [loadingColors, setLoadingColors] = useState(false);
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        if (isOpen) {
            setTitle(initialTitle || '');
            setSelectedColor(initialColor || '');
            setError('');
            fetchAvailableColors();
        }
    }, [isOpen, initialTitle, initialColor]);

    const fetchAvailableColors = async () => {
        setLoadingColors(true);
        try {
            const response = await api.get('/bullion-names/colors/available');
            const colors = response.data.data || [];
            setAvailableColors(colors);

            // Если нет выбранного цвета и есть доступные - выбираем первый
            if (!selectedColor && colors.length > 0) {
                setSelectedColor(colors[0]);
            }

            // Если выбранный цвет недоступен (был занят) - выбираем первый доступный
            if (selectedColor && colors.length > 0 && !colors.includes(selectedColor)) {
                setSelectedColor(colors[0]);
            }
        } catch (err) {
            console.error('Ошибка загрузки цветов:', err);
            setError('Не удалось загрузить доступные цвета');
        } finally {
            setLoadingColors(false);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        const trimmedTitle = title.trim();
        if (!trimmedTitle) {
            setError('Название наименования обязательно');
            return;
        }
        if (trimmedTitle.length < 2) {
            setError('Название должно содержать минимум 2 символа');
            return;
        }
        if (trimmedTitle.length > 100) {
            setError('Название не должно превышать 100 символов');
            return;
        }

        if (!selectedColor) {
            setError('Пожалуйста, выберите цвет');
            return;
        }

        setSaving(true);
        setError('');

        try {
            await onSave(trimmedTitle, selectedColor);
            onClose();
        } catch (err) {
            setError(err.response?.data?.message || 'Ошибка сохранения наименования');
        } finally {
            setSaving(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>{isEditing ? 'Редактирование наименования' : 'Добавление наименования'}</h2>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        <div className="form-group">
                            <label>Название наименования</label>
                            <input
                                type="text"
                                value={title}
                                onChange={(e) => setTitle(e.target.value)}
                                placeholder="например: Продукты, Транспорт, Зарплата"
                                autoFocus
                            />
                            <small className="input-hint">
                                Минимум 2 символа, максимум 100
                            </small>
                        </div>

                        <div className="form-group">
                            <label>Цвет наименования</label>
                            {loadingColors ? (
                                <div className="colors-loading">Загрузка цветов...</div>
                            ) : (
                                <>
                                    <div className="color-picker-grid">
                                        {availableColors.map((color) => (
                                            <button
                                                key={color}
                                                type="button"
                                                className={`color-option ${selectedColor === color ? 'selected' : ''}`}
                                                style={{ backgroundColor: color }}
                                                onClick={() => setSelectedColor(color)}
                                                title={color}
                                            />
                                        ))}
                                    </div>
                                    {availableColors.length === 0 && (
                                        <div className="color-warning">
                                            ⚠️ Все цвета заняты! Удалите или измените существующие наименования.
                                        </div>
                                    )}
                                    <div className="color-info">
                                        <span>Доступно цветов: {availableColors.length}</span>
                                        {selectedColor && (
                                            <span className="selected-color-info">
                                                Выбран: <span className="color-preview" style={{ backgroundColor: selectedColor }} />
                                                {selectedColor}
                                            </span>
                                        )}
                                    </div>
                                </>
                            )}
                        </div>

                        {error && <div className="error-message">{error}</div>}
                    </div>

                    <div className="modal-footer">
                        <button type="button" onClick={onClose} className="cancel-btn">
                            Отмена
                        </button>
                        <button
                            type="submit"
                            disabled={saving || loadingColors || availableColors.length === 0}
                            className="save-btn"
                        >
                            {saving ? 'Сохранение...' : 'Сохранить'}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

export default BullionNameModal;
