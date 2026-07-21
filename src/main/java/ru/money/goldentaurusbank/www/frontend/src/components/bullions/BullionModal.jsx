import { useState, useEffect } from 'react';
import api from '../../api/axios';
import './Bullions.css';

function BullionModal({
                          isOpen,
                          onClose,
                          onSave,
                          initialCategoryId,
                          initialVaultId,
                          initialAmount,
                          initialDescription,
                          isEditing,
                          initialCategoryName,
                          initialDateOperation,
                          allowedChangeAmount = true // 👈 ДОБАВИТЬ ЭТОТ ПРОПС
                      }) {
    const [categories, setCategories] = useState([]);
    const [vaults, setVaults] = useState([]);
    const [categoryId, setCategoryId] = useState('');
    const [vaultId, setVaultId] = useState('');
    const [amount, setAmount] = useState('');
    const [amountDisplay, setAmountDisplay] = useState('');
    const [description, setDescription] = useState('');
    const [dateOperation, setDateOperation] = useState('');
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);

    const handleAmountChange = (e) => {
        let value = e.target.value;
        value = value.replace(/[^\d.,]/g, '');
        value = value.replace(',', '.');
        const parts = value.split('.');
        if (parts.length > 2) {
            value = parts[0] + '.' + parts.slice(1).join('');
        }
        if (parts.length === 2 && parts[1].length > 2) {
            value = parts[0] + '.' + parts[1].substring(0, 2);
        }
        setAmountDisplay(value);
        const number = parseFloat(value);
        if (!isNaN(number)) {
            setAmount(number.toString());
        } else if (value === '' || value === '.') {
            setAmount('');
        }
    };

    const handleAmountBlur = () => {
        if (amountDisplay && amountDisplay !== '.') {
            const number = parseFloat(amountDisplay);
            if (!isNaN(number)) {
                const formatted = number.toLocaleString('ru-RU', {
                    minimumFractionDigits: 0,
                    maximumFractionDigits: 2
                });
                setAmountDisplay(formatted);
                setAmount(number.toString());
            }
        } else if (amountDisplay === '.') {
            setAmountDisplay('');
            setAmount('');
        }
    };

    const handleAmountFocus = () => {
        if (amount) {
            setAmountDisplay(amount);
        }
    };

    useEffect(() => {
        if (isOpen) {
            if (!isEditing) {
                fetchCategories();
                fetchVaults();
            } else {
                fetchVaults();
            }

            if (isEditing) {
                setCategoryId(initialCategoryId || '');
                setVaultId(initialVaultId || '');
            } else {
                setCategoryId(initialCategoryId || '');
                setVaultId(initialVaultId || '');
            }

            if (initialAmount !== undefined && initialAmount !== '' && initialAmount !== null) {
                const numAmount = typeof initialAmount === 'string' ? parseFloat(initialAmount) : initialAmount;
                if (!isNaN(numAmount) && numAmount > 0) {
                    const formatted = numAmount.toLocaleString('ru-RU', {
                        minimumFractionDigits: 0,
                        maximumFractionDigits: 2
                    });
                    setAmountDisplay(formatted);
                    setAmount(numAmount.toString());
                } else {
                    setAmountDisplay('');
                    setAmount('');
                }
            } else {
                setAmountDisplay('');
                setAmount('');
            }

            // Устанавливаем начальную дату операции
            if (isEditing) {
                // При редактировании используем переданную дату или пустую строку (если дата не задана)
                setDateOperation(initialDateOperation || '');
            } else {
                // При создании новой записи: если дата передана, используем её, иначе сегодня
                if (initialDateOperation) {
                    setDateOperation(initialDateOperation);
                } else {
                    const today = new Date();
                    const year = today.getFullYear();
                    const month = String(today.getMonth() + 1).padStart(2, '0');
                    const day = String(today.getDate()).padStart(2, '0');
                    const formattedDate = `${year}-${month}-${day}`;
                    setDateOperation(formattedDate);
                }
            }

            setDescription(initialDescription || '');
            setError('');
        }
    }, [isOpen, initialCategoryId, initialVaultId, initialAmount, initialDescription, isEditing, initialDateOperation]);

    const fetchCategories = async () => {
        try {
            const response = await api.get('/categories?sortBy=name&sortOrder=asc&size=100');
            setCategories(response.data.data || []);
        } catch (err) {
            console.error('Ошибка загрузки категорий:', err);
        }
    };

    const fetchVaults = async () => {
        try {
            const response = await api.get('/vaults?sortBy=name&sortOrder=asc&size=100');
            setVaults(response.data.data.content || []);
        } catch (err) {
            console.error('Ошибка загрузки хранилищ:', err);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!isEditing) {
            if (!categoryId) {
                setError('Выберите категорию');
                return;
            }
            if (!vaultId) {
                setError('Выберите хранилище');
                return;
            }
        }

        let amountNum = 0;
        if (amount) {
            amountNum = parseFloat(amount);
            if (isNaN(amountNum)) amountNum = 0;
        } else if (amountDisplay) {
            const cleanAmount = amountDisplay.replace(/\s/g, '').replace(',', '.');
            amountNum = parseFloat(cleanAmount);
            if (isNaN(amountNum)) amountNum = 0;
        }

        if (amountNum < 0) {
            setError('Сумма не может быть отрицательной');
            return;
        }

        amountNum = Math.round(amountNum * 100) / 100;

        setSaving(true);
        setError('');

        try {
            await onSave(
                isEditing ? initialCategoryId : parseInt(categoryId),
                isEditing ? parseInt(initialVaultId) : parseInt(vaultId),
                amountNum,
                description.trim() || null,
                dateOperation ? dateOperation : null
            );
            onClose();
        } catch (err) {
            setError(err.response?.data?.message || 'Ошибка сохранения слитка');
        } finally {
            setSaving(false);
        }
    };

    const getVaultName = () => {
        const vault = vaults.find(v => v.id === parseInt(initialVaultId));
        return vault?.name || 'Загрузка...';
    };

    if (!isOpen) return null;

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>{isEditing ? 'Редактирование слитка' : 'Добавление слитка'}</h2>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        {isEditing ? (
                            <>
                                <div className="form-group">
                                    <label>Категория</label>
                                    <input
                                        type="text"
                                        value={initialCategoryName || ''}
                                        disabled
                                        className="disabled-input"
                                    />
                                    <small className="input-hint">Категорию нельзя изменить</small>
                                </div>

                                <div className="form-group">
                                    <label>Хранилище</label>
                                    <input
                                        type="text"
                                        value={getVaultName()}
                                        disabled
                                        className="disabled-input"
                                    />
                                    <small className="input-hint">Хранилище нельзя изменить</small>
                                </div>
                            </>
                        ) : (
                            <>
                                <div className="form-group">
                                    <label>Категория *</label>
                                    <select
                                        value={categoryId}
                                        onChange={(e) => setCategoryId(e.target.value)}
                                        required
                                    >
                                        <option value="">Выберите категорию</option>
                                        {categories.map(cat => (
                                            <option key={cat.id} value={cat.id}>{cat.name}</option>
                                        ))}
                                    </select>
                                </div>

                                <div className="form-group">
                                    <label>Хранилище *</label>
                                    <select
                                        value={vaultId}
                                        onChange={(e) => setVaultId(e.target.value)}
                                        required
                                    >
                                        <option value="">Выберите хранилище</option>
                                        {vaults.map(vault => (
                                            <option key={vault.id} value={vault.id}>{vault.name}</option>
                                        ))}
                                    </select>
                                </div>
                            </>
                        )}

                        <div className="form-group">
                            <label>Сумма * (₽)</label>
                            <input
                                type="text"
                                value={amountDisplay}
                                onChange={handleAmountChange}
                                onBlur={handleAmountBlur}
                                onFocus={handleAmountFocus}
                                placeholder="0.00"
                                required
                                inputMode="decimal"
                                disabled={!allowedChangeAmount}
                            />
                            {!allowedChangeAmount && (
                                <small className="input-hint" style={{ color: '#e53e3e' }}>
                                    ⚠️ Изменение суммы запрещено для этого хранилища
                                </small>
                            )}
                            {allowedChangeAmount && (
                                <small className="input-hint">
                                    Используйте точку или запятую для копеек
                                </small>
                            )}
                        </div>

                        <div className="form-group">
                            <label>Дата операции *</label>
                            <input
                                type="date"
                                value={dateOperation}
                                onChange={(e) => setDateOperation(e.target.value)}
                                max={`${new Date().getFullYear()}-${String(new Date().getMonth()+1).padStart(2,'0')}-${String(new Date().getDate()).padStart(2,'0')}`}
                                required
                            />
                        </div>

                        <div className="form-group">
                            <label>Описание</label>
                            <textarea
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                placeholder="Дополнительная информация..."
                                rows={3}
                            />
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

export default BullionModal;