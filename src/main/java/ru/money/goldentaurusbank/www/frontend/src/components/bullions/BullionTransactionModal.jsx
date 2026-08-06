import { useState, useEffect } from 'react';
import Select from 'react-select';
import './Bullions.css';

const formatAmount = (amount) => {
    if (!amount && amount !== 0) return '0';
    const num = typeof amount === 'string' ? parseFloat(amount) : amount;
    if (isNaN(num)) return '0';
    const parts = num.toFixed(2).split('.');
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

function BullionTransactionModal({
                                     isOpen,
                                     onClose,
                                     onSave,
                                     title,
                                     buttonText,
                                     buttonClass,
                                     bullionName,
                                     showVaultSelector = false,
                                     vaults = [],
                                     initialVaultId = null,
                                     initialAmount = '',
                                     initialDescription = '',
                                     showAmount = true,
                                     showDescription = true,
                                     isConfirm = false,
                                     confirmMessage = '',
                                     descriptionPlaceholder = "Дополнительная информация...",
                                     descriptionLabel = "Комментарий",
                                     vaultSelectorLabel = "Хранилище",
                                     type = null,
                                     maxTransferAmount = 0,
                                     transferTargets = [],
                                     fromBullionId = null,
                                     fromBullions = [],
                                     onSelectFromBullion = null,
                                     selectedFromBullionId = null,
                                     initialDateOperation = null,
                                 }) {
    const [amount, setAmount] = useState('');
    const [amountDisplay, setAmountDisplay] = useState('');
    const [description, setDescription] = useState('');
    const [selectedVaultId, setSelectedVaultId] = useState('');
    const [selectedTargetOption, setSelectedTargetOption] = useState(null);
    const [selectedFromOption, setSelectedFromOption] = useState(null);
    const [error, setError] = useState('');
    const [saving, setSaving] = useState(false);
    const [toLiquidityVault, setToLiquidityVault] = useState(true);
    const [amountError, setAmountError] = useState('');
    const [isAmountValid, setIsAmountValid] = useState(true);
    const [dateOperation, setDateOperation] = useState(''); // 👈 ДОБАВИТЬ СОСТОЯНИЕ ДАТЫ

    const isDeleteModal = type === 'delete';
    const isTransferModal = type === 'transfer';

    // Фильтруем по флагу allowedTransfer
    const transferTargetOptions = transferTargets
        .filter(target => {
            if (target.allowedTransfer === false) return false;
            return true;
        })
        .map(target => ({
            value: target.id,
            label: `${target.bullionNameTitle} | ${target.vaultName} | ${formatAmount(target.amount)} ₽`,
            data: target
        }));

    const fromBullionOptions = fromBullions
        .filter(b => b.allowedTransfer !== false)
        .map(b => ({
            value: b.id,
            label: `${b.bullionNameTitle} | ${b.vaultName} | ${formatAmount(b.amount)} ₽`,
            data: b
        }));

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
            validateAmount(number);
        } else if (value === '' || value === '.') {
            setAmount('');
        }
    };

    const validateAmount = (value) => {
        if (isTransferModal && maxTransferAmount > 0) {
            if (value <= 0) {
                setAmountError('Сумма должна быть больше 0');
                setIsAmountValid(false);
            } else if (value > maxTransferAmount) {
                setAmountError(`Сумма не может превышать ${formatAmount(maxTransferAmount)} ₽`);
                setIsAmountValid(false);
            } else {
                setAmountError('');
                setIsAmountValid(true);
            }
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
                validateAmount(number);
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

    const handleTargetSelect = (option) => {
        setSelectedTargetOption(option);
        if (option && option.value === fromBullionId) {
            setError('Нельзя перевести в тот же слиток');
            setSelectedTargetOption(null);
        } else {
            setError('');
        }
    };

    const handleFromSelect = (option) => {
        setSelectedFromOption(option);
        if (onSelectFromBullion && option) {
            onSelectFromBullion(option.value, option.data.amount);
        }
        if (option && selectedTargetOption && option.value === selectedTargetOption.value) {
            setError('Нельзя перевести в тот же слиток');
            setSelectedTargetOption(null);
        } else {
            setError('');
        }
    };

    useEffect(() => {
        if (isOpen) {
            if (initialAmount !== undefined && initialAmount !== '' && initialAmount !== null) {
                const numAmount = typeof initialAmount === 'string' ? parseFloat(initialAmount) : initialAmount;
                if (!isNaN(numAmount) && numAmount > 0) {
                    const formatted = numAmount.toLocaleString('ru-RU', {
                        minimumFractionDigits: 0,
                        maximumFractionDigits: 2
                    });
                    setAmountDisplay(formatted);
                    setAmount(numAmount.toString());
                    setIsAmountValid(true);
                    setAmountError('');
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
            setSelectedVaultId(initialVaultId || '');
            setToLiquidityVault(true);
            setSelectedTargetOption(null);
            setSelectedFromOption(null);
            setError('');
            setSaving(false);
        }
    }, [isOpen, initialAmount, initialDescription, initialVaultId, initialDateOperation]);

    useEffect(() => {
        if (selectedFromBullionId && fromBullionOptions.length > 0) {
            const found = fromBullionOptions.find(b => b.value === selectedFromBullionId);
            if (found) {
                setSelectedFromOption(found);
            }
        }
    }, [selectedFromBullionId, fromBullionOptions]);

    // Фильтруем по флагам
    const getAvailableVaults = () => {
        return vaults.filter(vault => {
            if (type === 'refill' && vault.allowedIncome === false) return false;
            if (type === 'withdraw' && vault.allowedExpense === false) return false;
            if (type === 'delete' && vault.allowedDelete === false) return false;
            return true;
        });
    };

    const vaultOptions = getAvailableVaults().map(vault => ({
        value: vault.id,
        label: `${vault.name} (${formatAmount(vault.amount)} ₽)`
    }));

    const handleVaultSelect = (option) => {
        setSelectedVaultId(option?.value || '');
    };

    const isSubmitDisabled = () => {
        if (saving) return true;
        if (isTransferModal) {
            if (!selectedFromOption) return true;
            if (!selectedTargetOption) return true;
            if (!amount || parseFloat(amount) <= 0) return true;
            if (!isAmountValid) return true;
            if (parseFloat(amount) > maxTransferAmount) return true;
            return false;
        }
        return false;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (isConfirm) {
            setSaving(true);
            try {
                await onSave();
                onClose();
            } catch (err) {
                setError(err.response?.data?.message || `Ошибка при ${buttonText.toLowerCase()}`);
            } finally {
                setSaving(false);
            }
            return;
        }

        if (isTransferModal) {
            if (!selectedFromOption) {
                setError('Выберите слиток-отправитель');
                return;
            }
            if (!selectedTargetOption) {
                setError('Выберите слиток-получатель');
                return;
            }
            if (selectedFromOption.value === selectedTargetOption.value) {
                setError('Нельзя перевести в тот же слиток');
                return;
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

            if (amountNum <= 0) {
                setError('Сумма должна быть больше 0');
                return;
            }

            if (amountNum > maxTransferAmount) {
                setError(`Сумма перевода не может превышать ${formatAmount(maxTransferAmount)} ₽`);
                return;
            }

            amountNum = Math.round(amountNum * 100) / 100;

            setSaving(true);
            setError('');

            try {
                await onSave(
                    amountNum,
                    selectedTargetOption.value,
                    description.trim() || null,
                    dateOperation // 👈 ПЕРЕДАЕМ ДАТУ ОПЕРАЦИИ
                );
                onClose();
            } catch (err) {
                setError(err.response?.data?.message || 'Ошибка при переводе');
            } finally {
                setSaving(false);
            }
            return;
        }

        if (isDeleteModal && showVaultSelector) {
            if (!toLiquidityVault && !selectedVaultId) {
                setError('Выберите хранилище для переноса');
                return;
            }
        } else if (showVaultSelector && !selectedVaultId) {
            setError('Выберите хранилище');
            return;
        }

        let amountNum = 0;
        if (showAmount) {
            if (amount) {
                amountNum = parseFloat(amount);
                if (isNaN(amountNum)) amountNum = 0;
            } else if (amountDisplay) {
                const cleanAmount = amountDisplay.replace(/\s/g, '').replace(',', '.');
                amountNum = parseFloat(cleanAmount);
                if (isNaN(amountNum)) amountNum = 0;
            }

            if (amountNum <= 0) {
                setError('Сумма должна быть больше 0');
                return;
            }

            amountNum = Math.round(amountNum * 100) / 100;
        }

        setSaving(true);
        setError('');

        try {
            if (isDeleteModal) {
                await onSave(
                    toLiquidityVault ? null : selectedVaultId,
                    toLiquidityVault,
                    description.trim() || null,
                    dateOperation ? dateOperation : null
                );
            } else if (showAmount) {
                await onSave(amountNum, description.trim() || null, selectedVaultId, dateOperation ? dateOperation : null);
            } else {
                await onSave(selectedVaultId, description.trim() || null, dateOperation ? dateOperation : null);
            }
            onClose();
        } catch (err) {
            setError(err.response?.data?.message || `Ошибка при ${buttonText.toLowerCase()}`);
        } finally {
            setSaving(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className={`modal-content transaction-modal ${isTransferModal ? 'transfer-modal-wide' : ''}`} onClick={(e) => e.stopPropagation()}>
                <div className="modal-header">
                    <h2>{title}</h2>
                    <button className="modal-close" onClick={onClose}>×</button>
                </div>

                <form onSubmit={handleSubmit} className="modal-form">
                    <div className="modal-body-scrollable">
                        {bullionName && !isConfirm && !isTransferModal && (
                            <div className="transaction-bullion-info">
                                <span className="info-label">Слиток:</span>
                                <span className="info-value">{bullionName}</span>
                            </div>
                        )}

                        {isTransferModal && selectedFromOption && (
                            <div className="transaction-bullion-info">
                                <span className="info-label">📤 Откуда:</span>
                                <span className="info-value">{selectedFromOption.data?.bullionNameTitle} | {selectedFromOption.data?.vaultName}</span>
                                <span className="info-label" style={{ marginLeft: '16px' }}>
                                    Доступно: {formatAmount(maxTransferAmount)} ₽
                                </span>
                            </div>
                        )}

                        {isConfirm && confirmMessage && (
                            <div className="confirm-message">
                                <p>{confirmMessage}</p>
                            </div>
                        )}

                        {isTransferModal && (
                            <>
                                {fromBullions && fromBullions.length > 0 && (
                                    <div className="form-group">
                                        <label>📤 Откуда переводим *</label>
                                        <Select
                                            options={fromBullionOptions}
                                            value={selectedFromOption}
                                            onChange={handleFromSelect}
                                            placeholder="Выберите слиток-отправитель..."
                                            isClearable
                                            noOptionsMessage={() => "Нет доступных слитков в этом наименовании"}
                                            className="react-select-container"
                                            classNamePrefix="react-select"
                                            formatOptionLabel={(option) => (
                                                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                                    <span>{option.data?.bullionNameTitle}</span>
                                                    <span style={{ color: '#718096', fontSize: '13px' }}>
                                                        {option.data?.vaultName}
                                                    </span>
                                                    <span style={{ fontWeight: 600, color: '#2d3748' }}>
                                                        {formatAmount(option.data?.amount)} ₽
                                                    </span>
                                                </div>
                                            )}
                                        />
                                        <small className="input-hint">
                                            Выберите слиток, с которого будут переведены средства
                                        </small>
                                    </div>
                                )}

                                <div className="form-group">
                                    <label>Сумма перевода * (₽)</label>
                                    <input
                                        type="text"
                                        value={amountDisplay}
                                        onChange={handleAmountChange}
                                        onBlur={handleAmountBlur}
                                        onFocus={handleAmountFocus}
                                        placeholder="0.00"
                                        required
                                        inputMode="decimal"
                                        autoFocus
                                        className={!isAmountValid && amountError ? 'input-error' : ''}
                                        disabled={!selectedFromOption}
                                    />
                                    {amountError && (
                                        <div className="amount-warning">
                                            ⚠️ {amountError}
                                        </div>
                                    )}
                                    <small className="input-hint">
                                        Максимальная сумма: {formatAmount(maxTransferAmount)} ₽
                                    </small>
                                </div>

                                <div className="form-group">
                                    <label>📥 Куда перевести *</label>
                                    <Select
                                        options={transferTargetOptions}
                                        value={selectedTargetOption}
                                        onChange={handleTargetSelect}
                                        placeholder="Выберите слиток-получатель..."
                                        isClearable
                                        noOptionsMessage={() => "Нет доступных слитков для перевода"}
                                        className="react-select-container"
                                        classNamePrefix="react-select"
                                        formatOptionLabel={(option) => (
                                            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                                <span>{option.data?.bullionNameTitle}</span>
                                                <span style={{ color: '#718096', fontSize: '13px' }}>
                                                    {option.data?.vaultName}
                                                </span>
                                                <span style={{ fontWeight: 600, color: '#2d3748' }}>
                                                    {formatAmount(option.data?.amount)} ₽
                                                </span>
                                            </div>
                                        )}
                                    />
                                    <small className="input-hint">
                                        Выберите слиток, на который будут переведены средства
                                    </small>
                                </div>

                                {selectedTargetOption && selectedFromOption && selectedTargetOption.value !== selectedFromOption.value && (
                                    <div className="transfer-target-info">
                                        <div className="target-info-title">📥 Выбран получатель:</div>
                                        <div className="target-info-details">
                                            <div className="target-info-row">
                                                <span className="target-info-label">Наименование:</span>
                                                <span className="target-info-value">{selectedTargetOption.data?.bullionNameTitle}</span>
                                            </div>
                                            <div className="target-info-row">
                                                <span className="target-info-label">Хранилище:</span>
                                                <span className="target-info-value">{selectedTargetOption.data?.vaultName}</span>
                                            </div>
                                            <div className="target-info-row">
                                                <span className="target-info-label">Текущий баланс:</span>
                                                <span className="target-info-value">{formatAmount(selectedTargetOption.data?.amount)} ₽</span>
                                            </div>
                                            <div className="target-info-row highlight">
                                                <span className="target-info-label">Будет после перевода:</span>
                                                <span className="target-info-value highlight-amount">
                                                    {formatAmount((selectedTargetOption.data?.amount || 0) + parseFloat(amount || 0))} ₽
                                                </span>
                                            </div>
                                        </div>
                                    </div>
                                )}

                                {selectedTargetOption && selectedFromOption && selectedTargetOption.value === selectedFromOption.value && (
                                    <div className="error-message">Нельзя перевести в тот же слиток</div>
                                )}

                                <div className="form-group">
                                    <label>📝 Комментарий</label>
                                    <textarea
                                        value={description}
                                        onChange={(e) => setDescription(e.target.value)}
                                        placeholder="Комментарий к переводу (необязательно)..."
                                        rows={3}
                                        className="transaction-textarea"
                                    />
                                </div>
                            </>
                        )}

                        {isDeleteModal && showVaultSelector && vaults.length > 0 && (
                            <>
                                <div className="form-group">
                                    <label>Куда перенести остатки?</label>
                                    <div className="radio-group">
                                        <label className="radio-label">
                                            <input
                                                type="radio"
                                                name="transferOption"
                                                checked={toLiquidityVault === true}
                                                onChange={() => setToLiquidityVault(true)}
                                            />
                                            <span>💰 Ликвидный резерв</span>
                                        </label>
                                        <label className="radio-label">
                                            <input
                                                type="radio"
                                                name="transferOption"
                                                checked={toLiquidityVault === false}
                                                onChange={() => setToLiquidityVault(false)}
                                            />
                                            <span>🏦 Выбрать хранилище</span>
                                        </label>
                                    </div>
                                </div>

                                {!toLiquidityVault && (
                                    <div className="form-group">
                                        <label>Хранилище для переноса *</label>
                                        <Select
                                            options={vaultOptions}
                                            value={vaultOptions.find(v => v.value === parseInt(selectedVaultId)) || null}
                                            onChange={handleVaultSelect}
                                            placeholder="Поиск хранилища..."
                                            isClearable
                                            noOptionsMessage={() => "Хранилища не найдены"}
                                            className="react-select-container"
                                            classNamePrefix="react-select"
                                        />
                                        <small className="input-hint">
                                            Начните вводить название для поиска
                                        </small>
                                    </div>
                                )}
                            </>
                        )}

                        {!isDeleteModal && !isTransferModal && showVaultSelector && vaults.length > 0 && (
                            <div className="form-group">
                                <label>{vaultSelectorLabel} *</label>
                                <Select
                                    options={vaultOptions}
                                    value={vaultOptions.find(v => v.value === parseInt(selectedVaultId)) || null}
                                    onChange={handleVaultSelect}
                                    placeholder="Поиск хранилища..."
                                    isClearable
                                    noOptionsMessage={() => "Хранилища не найдены"}
                                    className="react-select-container"
                                    classNamePrefix="react-select"
                                />
                                {vaultOptions.length === 0 && (
                                    <small className="input-hint" style={{ color: '#e53e3e' }}>
                                        ⚠️ Нет доступных хранилищ
                                    </small>
                                )}
                            </div>
                        )}

                        {showAmount && !isConfirm && !isTransferModal && (
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
                                    autoFocus
                                />
                                <small className="input-hint">
                                    Используйте точку или запятую для копеек
                                </small>
                            </div>
                        )}

                        {showDescription && !isConfirm && !isTransferModal && (
                            <div className="form-group">
                                <label>{descriptionLabel}</label>
                                <textarea
                                    value={description}
                                    onChange={(e) => setDescription(e.target.value)}
                                    placeholder={descriptionPlaceholder}
                                    rows={3}
                                    className="transaction-textarea"
                                />
                                <small className="input-hint">
                                    Необязательное поле
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

                        {error && <div className="error-message">{error}</div>}
                    </div>

                    <div className="modal-footer">
                        <button type="button" onClick={onClose} className="cancel-btn">
                            Отмена
                        </button>
                        <button
                            type="submit"
                            disabled={isSubmitDisabled()}
                            className={`save-btn ${buttonClass} ${!isSubmitDisabled() ? '' : 'disabled-btn'}`}
                        >
                            {saving ? 'Обработка...' : buttonText}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

export default BullionTransactionModal;