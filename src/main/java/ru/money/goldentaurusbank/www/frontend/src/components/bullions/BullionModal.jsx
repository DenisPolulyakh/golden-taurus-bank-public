import { useState, useEffect } from 'react';
import api from '../../api/axios';
import { formatAmount, toCents } from './bullionAmount';
import './Bullions.css';

function BullionModal({
                          isOpen,
                          onClose,
                          onSave,
                          initialBullionNameId,
                          initialVaultId,
                          initialAmount,
                          initialDescription,
                          isEditing,
                          initialBullionNameTitle,
                          initialDateOperation,
                          // Дебетовый или кредитный. На расчёты пока не влияет,
                          // но менять его можно и у существующего слитка
                          initialBullionType,
                          // Правка суммы - это операция: вверх внесение, вниз снятие.
                          // Флаги считает бэк, здесь только гасим поле и подсказываем
                          allowedChangeAmount = true,
                          allowedIncome = true,
                          allowedExpense = true
                      }) {
    const [bullionNames, setBullionNames] = useState([]);
    const [vaults, setVaults] = useState([]);
    const [bullionNameId, setBullionNameId] = useState('');
    const [vaultId, setVaultId] = useState('');
    const [amount, setAmount] = useState('');
    const [amountDisplay, setAmountDisplay] = useState('');
    const [description, setDescription] = useState('');
    const [bullionType, setBullionType] = useState('DEBIT');
    const [userComment, setUserComment] = useState('');
    const [dateOperation, setDateOperation] = useState('');
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);

    // Введённая сумма живёт в двух состояниях: amount — чистое число, amountDisplay —
    // то же с разделителями разрядов, пока поле не в фокусе.
    const enteredCents = () => {
        if (amount) return toCents(amount);
        if (amountDisplay) return toCents(amountDisplay.replace(/\s/g, '').replace(',', '.'));
        return 0;
    };

    // Пока поле пустое, предпросмотр молчит: иначе на стёртой сумме он обещал бы
    // снятие на весь остаток.
    const amountIsEmpty = !amount && !amountDisplay;

    // Разницу считаем на клиенте: старую сумму слитка знает страница, а ответ PUT
    // о проведённой операции пока не рассказывает.
    const deltaCents = isEditing && !amountIsEmpty ? enteredCents() - toCents(initialAmount) : 0;

    // Создание слитка галочки не трогают: это регистрация уже накопленного,
    // а не операция. Гасим поле только при правке существующего.
    const amountLocked = isEditing && !allowedChangeAmount;

    // Направление правки: вверх упирается в «Можно вносить», вниз - в «Можно снимать»
    const directionError = () => {
        if (!isEditing) return '';
        if (deltaCents > 0 && !allowedIncome) {
            return 'В это хранилище вносить нельзя — сумму можно только уменьшить';
        }
        if (deltaCents < 0 && !allowedExpense) {
            return 'Из этого хранилища снимать нельзя — сумму можно только увеличить';
        }
        return '';
    };

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
                fetchBullionNames();
                fetchVaults();
            } else {
                fetchVaults();
            }

            if (isEditing) {
                setBullionNameId(initialBullionNameId || '');
                setVaultId(initialVaultId || '');
            } else {
                setBullionNameId(initialBullionNameId || '');
                setVaultId(initialVaultId || '');
            }

            if (initialAmount !== undefined && initialAmount !== '' && initialAmount !== null) {
                const numAmount = typeof initialAmount === 'string' ? parseFloat(initialAmount) : initialAmount;
                // Ноль подставляем как «0», а не пустотой: пустое поле не проходит
                // required, и слиток с нулевым остатком было не сохранить
                if (!isNaN(numAmount) && numAmount >= 0) {
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

            // Устанавливаем начальную дату и время операции
            if (initialDateOperation) {
                // Check if it's a date-only string (yyyy-MM-dd)
                if (/^\d{4}-\d{2}-\d{2}$/.test(initialDateOperation)) {
                    // Parse as date-only and set time to 00:00 in local time
                    const [year, month, day] = initialDateOperation.split('-').map(Number);
                    const localDate = new Date(year, month - 1, day, 0, 0, 0, 0);
                    const yearLocal = localDate.getFullYear();
                    const monthLocal = String(localDate.getMonth() + 1).padStart(2, '0');
                    const dayLocal = String(localDate.getDate()).padStart(2, '0');
                    const hoursLocal = String(localDate.getHours()).padStart(2, '0');
                    const minutesLocal = String(localDate.getMinutes()).padStart(2, '0');
                    setDateOperation(`${yearLocal}-${monthLocal}-${dayLocal}T${hoursLocal}:${minutesLocal}`);
                } else {
                    // Try to parse as a date string (with time) or ISO string
                    const date = new Date(initialDateOperation);
                    if (!isNaN(date.getTime())) {
                        const year = date.getFullYear();
                        const month = String(date.getMonth() + 1).padStart(2, '0');
                        const day = String(date.getDate()).padStart(2, '0');
                        const hours = String(date.getHours()).padStart(2, '0');
                        const minutes = String(date.getMinutes()).padStart(2, '0');
                        setDateOperation(`${year}-${month}-${day}T${hours}:${minutes}`);
                    } else {
                        // If invalid, fallback to current datetime
                        const now = new Date();
                        const year = now.getFullYear();
                        const month = String(now.getMonth() + 1).padStart(2, '0');
                        const day = String(now.getDate()).padStart(2, '0');
                        const hours = String(now.getHours()).padStart(2, '0');
                        const minutes = String(now.getMinutes()).padStart(2, '0');
                        setDateOperation(`${year}-${month}-${day}T${hours}:${minutes}`);
                    }
                }
            } else {
                // Если дата не указана, устанавливаем текущую дату и время в локальном часовом поясе пользователя
                const now = new Date();
                const year = now.getFullYear();
                const month = String(now.getMonth() + 1).padStart(2, '0');
                const day = String(now.getDate()).padStart(2, '0');
                const hours = String(now.getHours()).padStart(2, '0');
                const minutes = String(now.getMinutes()).padStart(2, '0');
                setDateOperation(`${year}-${month}-${day}T${hours}:${minutes}`);
            }

            setDescription(initialDescription || '');
            setBullionType(initialBullionType || 'DEBIT');
            setUserComment('');
            setError('');
        }
    }, [isOpen, initialBullionNameId, initialVaultId, initialAmount, initialDescription, isEditing, initialDateOperation, initialBullionType]);

    const fetchBullionNames = async () => {
        try {
            const response = await api.get('/bullion-names?sortBy=title&sortOrder=asc&size=100');
            setBullionNames(response.data.data || []);
        } catch (err) {
            console.error('Ошибка загрузки наименований:', err);
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
            if (!bullionNameId) {
                setError('Выберите наименование');
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

        const forbiddenDirection = directionError();
        if (forbiddenDirection) {
            setError(forbiddenDirection);
            return;
        }

        amountNum = Math.round(amountNum * 100) / 100;

        setSaving(true);
        setError('');

        try {
            await onSave(
                isEditing ? initialBullionNameId : parseInt(bullionNameId),
                isEditing ? parseInt(initialVaultId) : parseInt(vaultId),
                amountNum,
                description.trim() || null,
                dateOperation ? dateOperation : null,
                // Без дельты операции не будет, комментарию некуда попасть
                deltaCents !== 0 ? (userComment.trim() || null) : null,
                bullionType
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
                                    <label>Наименование</label>
                                    <input
                                        type="text"
                                        value={initialBullionNameTitle || ''}
                                        disabled
                                        className="disabled-input"
                                    />
                                    <small className="input-hint">Наименование нельзя изменить</small>
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
                                    <label>Наименование *</label>
                                    <select
                                        value={bullionNameId}
                                        onChange={(e) => setBullionNameId(e.target.value)}
                                        required
                                    >
                                        <option value="">Выберите наименование</option>
                                        {bullionNames.map(bn => (
                                            <option key={bn.id} value={bn.id}>{bn.title}</option>
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

                        {/* Тип менять можно и при правке — в отличие от наименования и хранилища */}
                        <div className="form-group">
                            <label>Тип слитка *</label>
                            <select
                                value={bullionType}
                                onChange={(e) => setBullionType(e.target.value)}
                                required
                            >
                                <option value="DEBIT">Дебетовый</option>
                                <option value="CREDIT">Кредитный</option>
                            </select>
                            <small className="input-hint">
                                Дебетовый — свои накопления, кредитный — заёмные средства
                            </small>
                        </div>

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
                                disabled={amountLocked}
                            />
                            {amountLocked && (
                                <small className="input-hint" style={{ color: '#e53e3e' }}>
                                    ⚠️ У хранилища сняты галочки внесения и снятия — сумму не изменить
                                </small>
                            )}
                            {!amountLocked && !isEditing && (
                                <small className="input-hint">
                                    Используйте точку или запятую для копеек
                                </small>
                            )}
                            {!amountLocked && isEditing && directionError() && (
                                <small className="input-hint" style={{ color: '#e53e3e' }}>
                                    ⚠️ {directionError()}
                                </small>
                            )}
                            {!amountLocked && isEditing && !directionError() && !amountIsEmpty && (
                                <small className={`input-hint amount-delta${
                                    deltaCents > 0 ? ' amount-delta-deposit' : deltaCents < 0 ? ' amount-delta-withdrawal' : ''
                                }`}>
                                    {deltaCents > 0 && `Будет проведено пополнение на ${formatAmount(deltaCents / 100)} ₽`}
                                    {deltaCents < 0 && `Будет проведено снятие на ${formatAmount(-deltaCents / 100)} ₽`}
                                    {deltaCents === 0 && 'Сумма не изменится — операция не создаётся'}
                                </small>
                            )}
                        </div>

                        {isEditing && deltaCents !== 0 && (
                            <div className="form-group">
                                <label>Комментарий к операции</label>
                                <input
                                    type="text"
                                    value={userComment}
                                    onChange={(e) => setUserComment(e.target.value)}
                                    placeholder={deltaCents > 0 ? 'Например: докупил' : 'Например: снял на ремонт'}
                                    maxLength={500}
                                />
                                <small className="input-hint">
                                    Попадёт в историю операций. Если оставить пустым — подставится стандартный текст
                                </small>
                            </div>
                        )}

                        <div className="form-group">
                            <label>Дата и время операции *</label>
                            <input
                                type="datetime-local"
                                value={dateOperation}
                                onChange={(e) => setDateOperation(e.target.value)}
                                max={`${new Date().getFullYear()}-${String(new Date().getMonth()+1).padStart(2,'0')}-${String(new Date().getDate()).padStart(2,'0')}T${String(new Date().getHours()).padStart(2,'0')}:${String(new Date().getMinutes()).padStart(2,'0')}`}
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