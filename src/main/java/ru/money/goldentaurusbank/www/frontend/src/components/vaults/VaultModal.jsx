import { useState, useEffect } from 'react';
import api from '../../api/axios';
import './Vaults.css';

function VaultModal({
    isOpen, onClose, onSave,
    initialName, initialInterestRate, initialDescription, initialBankId,
    initialAccountType, initialCloseDate,
    initialAllowedIncome = true, initialAllowedExpense = true, initialAllowedTransfer = true,
    isEditing
}) {
    const [name, setName] = useState('');
    const [interestRate, setInterestRate] = useState('');
    const [interestRateDisplay, setInterestRateDisplay] = useState('');
    const [description, setDescription] = useState('');
    const [banks, setBanks] = useState([]);
    const [bankId, setBankId] = useState('');
    const [accountType, setAccountType] = useState('SAVINGS');
    const [closeDate, setCloseDate] = useState('');
    // Галочки разрешённых операций: бэк хранит их в vaults.allowed_*,
    // и только они решают, доступна ли операция (см. plans/PLAN_VAULT_ALLOW.md).
    const [allowedIncome, setAllowedIncome] = useState(true);
    const [allowedExpense, setAllowedExpense] = useState(true);
    const [allowedTransfer, setAllowedTransfer] = useState(true);
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);

    const accountTypes = [
        { value: 'SAVINGS', label: 'Накопительный' },
        { value: 'TERM', label: 'Срочный' }
    ];

    useEffect(() => {
        if (isOpen) {
            fetchBanks();
            setName(initialName || '');
            setBankId(initialBankId || '');
            setAccountType(initialAccountType || 'SAVINGS');
            setCloseDate(initialCloseDate || '');
            setAllowedIncome(initialAllowedIncome !== false);
            setAllowedExpense(initialAllowedExpense !== false);
            setAllowedTransfer(initialAllowedTransfer !== false);

            if (initialInterestRate !== undefined && initialInterestRate !== null && initialInterestRate !== '') {
                const numRate = typeof initialInterestRate === 'string' ? parseFloat(initialInterestRate) : initialInterestRate;
                if (!isNaN(numRate)) {
                    const formatted = formatInterestRate(numRate);
                    setInterestRateDisplay(formatted);
                    setInterestRate(numRate.toString());
                } else {
                    setInterestRateDisplay('');
                    setInterestRate('');
                }
            } else {
                setInterestRateDisplay('');
                setInterestRate('');
            }

            setDescription(initialDescription || '');
            setError('');
        }
    }, [isOpen, initialName, initialInterestRate, initialDescription, initialBankId, initialAccountType, initialCloseDate,
        initialAllowedIncome, initialAllowedExpense, initialAllowedTransfer]);

    const fetchBanks = async () => {
        try {
            const response = await api.get('/banks');
            setBanks(response.data.data || []);
        } catch (err) {
            console.error('Ошибка загрузки банков:', err);
        }
    };

    const formatInterestRate = (value) => {
        if (!value && value !== 0) return '0';
        const num = typeof value === 'string' ? parseFloat(value) : value;
        if (isNaN(num)) return '0';
        const rounded = num.toFixed(2);
        const parts = rounded.split('.');
        const integerPart = parts[0];
        const decimalPart = parts[1];
        const formattedInteger = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
        if (decimalPart && decimalPart !== '00') {
            const trimmedDecimal = decimalPart.replace(/0+$/, '');
            if (trimmedDecimal) {
                return `${formattedInteger}.${trimmedDecimal}`;
            }
        }
        return formattedInteger;
    };

    const handleInterestRateChange = (e) => {
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
        setInterestRateDisplay(value);
        const number = parseFloat(value);
        if (!isNaN(number)) {
            setInterestRate(number.toString());
        } else if (value === '' || value === '.') {
            setInterestRate('');
        }
    };

    const handleInterestRateBlur = () => {
        if (interestRateDisplay && interestRateDisplay !== '.') {
            const number = parseFloat(interestRateDisplay);
            if (!isNaN(number)) {
                const formatted = formatInterestRate(number);
                setInterestRateDisplay(formatted);
                setInterestRate(number.toString());
            }
        } else if (interestRateDisplay === '.') {
            setInterestRateDisplay('');
            setInterestRate('');
        }
    };

    const handleInterestRateFocus = () => {
        if (interestRate) {
            setInterestRateDisplay(interestRate);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        const trimmedName = name.trim();
        if (!trimmedName) {
            setError('Название хранилища обязательно');
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

        let rateNum = 0;
        if (interestRate) {
            rateNum = parseFloat(interestRate);
            if (isNaN(rateNum)) rateNum = 0;
        } else if (interestRateDisplay) {
            const cleanRate = interestRateDisplay.replace(/\s/g, '').replace(',', '.');
            rateNum = parseFloat(cleanRate);
            if (isNaN(rateNum)) rateNum = 0;
        }

        if (rateNum < 0) {
            setError('Процентная ставка не может быть отрицательной');
            return;
        }
        if (rateNum > 100) {
            setError('Процентная ставка не может превышать 100%');
            return;
        }

        // Проверка даты закрытия для срочного вклада
        if (accountType === 'TERM' && !closeDate) {
            setError('Для срочного вклада укажите дату закрытия');
            return;
        }

        setSaving(true);
        setError('');

        try {
            await onSave({
                name: trimmedName,
                interestRate: rateNum,
                description: description.trim() || null,
                bankId: bankId || null,
                accountType,
                closeDate: closeDate || null,
                allowedIncome,
                allowedExpense,
                allowedTransfer
            });
            onClose();
        } catch (err) {
            setError(err.response?.data?.message || 'Ошибка сохранения хранилища');
        } finally {
            setSaving(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal-content" onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>{isEditing ? 'Редактирование хранилища' : 'Добавление хранилища'}</h2>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>

                <form onSubmit={handleSubmit}>
                    <div className="modal-body">
                        <div className="form-group">
                            <label>Название хранилища *</label>
                            <input
                                type="text"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                placeholder="например: Сберегательный, Депозитный"
                                autoFocus
                            />
                            <small className="input-hint">
                                Минимум 2 символа, максимум 100
                            </small>
                        </div>

                        <div className="form-group">
                            <label>Банк</label>
                            <select
                                value={bankId}
                                onChange={(e) => setBankId(e.target.value)}
                            >
                                <option value="">Не выбран</option>
                                {banks.map(bank => (
                                    <option key={bank.id} value={bank.id}>{bank.name}</option>
                                ))}
                            </select>
                            <small className="input-hint">
                                Выберите банк из справочника
                            </small>
                        </div>

                        <div className="form-group">
                            <label>Тип счета *</label>
                            <select
                                value={accountType}
                                onChange={(e) => setAccountType(e.target.value)}
                                required
                            >
                                {accountTypes.map(type => (
                                    <option key={type.value} value={type.value}>{type.label}</option>
                                ))}
                            </select>
                        </div>

                        {accountType === 'TERM' && (
                            <div className="form-group">
                                <label>Дата закрытия *</label>
                                <input
                                    type="date"
                                    value={closeDate}
                                    onChange={(e) => setCloseDate(e.target.value)}
                                    required
                                />
                                <small className="input-hint">
                                    Дата, когда срочный вклад будет закрыт
                                </small>
                            </div>
                        )}

                        <div className="form-group">
                            <label>Можно:</label>
                            <div className="checkbox-group">
                                <label className="checkbox-label">
                                    снять
                                    <input
                                        type="checkbox"
                                        checked={allowedExpense}
                                        onChange={(e) => setAllowedExpense(e.target.checked)}
                                    />
                                </label>
                                <label className="checkbox-label">
                                    внести
                                    <input
                                        type="checkbox"
                                        checked={allowedIncome}
                                        onChange={(e) => setAllowedIncome(e.target.checked)}
                                    />
                                </label>
                                <label className="checkbox-label">
                                    перевод
                                    <input
                                        type="checkbox"
                                        checked={allowedTransfer}
                                        onChange={(e) => setAllowedTransfer(e.target.checked)}
                                    />
                                </label>
                            </div>
                            <small className="input-hint">
                                Снятая галочка гасит кнопку на всех экранах слитков
                            </small>
                        </div>

                        <div className="form-group">
                            <label>Процентная ставка (%)</label>
                            <input
                                type="text"
                                value={interestRateDisplay}
                                onChange={handleInterestRateChange}
                                onBlur={handleInterestRateBlur}
                                onFocus={handleInterestRateFocus}
                                placeholder="0.00"
                                inputMode="decimal"
                            />
                            <small className="input-hint">
                                Используйте точку или запятую. От 0 до 100%.
                            </small>
                        </div>

                        <div className="form-group">
                            <label>Описание (необязательно)</label>
                            <textarea
                                value={description}
                                onChange={(e) => setDescription(e.target.value)}
                                placeholder="Краткое описание хранилища..."
                                rows={3}
                            />
                            <small className="input-hint">
                                Максимум 500 символов
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

export default VaultModal;