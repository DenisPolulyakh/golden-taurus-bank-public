import { useState, useEffect } from 'react'
import { Plus, TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Textarea } from '@/components/ui/textarea'
import { Combobox } from '@/components/ui-app/combobox'
import { FormDialog } from '@/components/ui-app/form-dialog'
import { ErrorMessage } from '@/components/ui-app/page-state'
import { formatAmount } from '@/lib/format'

// Кнопки быстрого ввода: прибавляются к текущей сумме
const QUICK_AMOUNTS = [100, 500, 1000, 2000, 3000, 5000, 10000];

// Разделы списка получателей: существующие слитки и хранилища, где слитка
// выбранного наименования ещё нет — там он заведётся сам
const EXISTING_TARGETS_GROUP = 'Существующие слитки';
const NEW_TARGETS_GROUP = 'Слитка нет, создадим';

function BullionTransactionModal({
                                     isOpen,
                                     onClose,
                                     onSave,
                                     title,
                                     buttonText,
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
                                     availableAmount = null,
                                     showBudgetOperation = false,
                                     showDateOperation = true,
                                     notice = null,
                                     // Живые хранилища с галочками — из них берутся
                                     // получатели, у которых слитка ещё нет
                                     vaultsForNewBullion = [],
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
    // Корзина месячного бюджета. У наличных умолчание «трата»: из них и состоит
    // день. У перевода наоборот — перевод внутрь почти всегда финансирование,
    // и галочка на нём уводила бы день в глубокий минус
    const [budgetOperation, setBudgetOperation] = useState(true);

    const isDeleteModal = type === 'delete';
    const isTransferModal = type === 'transfer';

    // Куда ещё можно перевести: хранилища, где слитка отправителя нет. Наименование
    // берём у отправителя - оно и создастся, поэтому список пересчитывается при
    // смене слитка-отправителя (в хранилище их несколько, у каждого своё имя).
    const fromData = selectedFromOption?.data;
    const newTargetOptions = (!fromData?.bullionNameId || fromData.vaultId == null) ? [] : vaultsForNewBullion
        .filter(vault => vault.allowedTransferIn !== false
            && vault.id !== fromData.vaultId
            && !transferTargets.some(target => target.vaultId === vault.id
                && target.bullionNameId === fromData.bullionNameId))
        .map(vault => ({
            value: `new:${vault.id}`,
            label: `${fromData.bullionNameTitle} | ${vault.name} | создать`,
            group: NEW_TARGETS_GROUP,
            data: {
                isNew: true,
                vaultId: vault.id,
                vaultName: vault.name,
                bullionNameId: fromData.bullionNameId,
                bullionNameTitle: fromData.bullionNameTitle,
                amount: 0
            }
        }));

    // Заголовки разделов появляются только вместе со вторым разделом: пока
    // создавать нечего, список выглядит как раньше
    const useTargetGroups = newTargetOptions.length > 0;

    // Получатель должен разрешать и перевод, и внесение - это один флаг с бэка
    const existingTargetOptions = transferTargets
        .filter(target => target.allowedTransferIn !== false)
        .map(target => ({
            value: target.id,
            label: `${target.bullionNameTitle} | ${target.vaultName} | ${formatAmount(target.amount)} ₽`,
            group: useTargetGroups ? EXISTING_TARGETS_GROUP : undefined,
            data: target
        }));

    const transferTargetOptions = [...existingTargetOptions, ...newTargetOptions];

    // Отправитель - и перевод, и снятие: если снимать нельзя, то и переводить нельзя
    const fromBullionOptions = fromBullions
        .filter(b => b.allowedTransferOut !== false)
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
        // Получатель «создадим» привязан к наименованию отправителя: сменили
        // отправителя - прежний выбор больше не про то наименование
        if (selectedTargetOption?.data?.isNew) {
            setSelectedTargetOption(null);
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
            setBudgetOperation(type !== 'transfer');
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
    }, [isOpen, initialAmount, initialDescription, initialVaultId, initialDateOperation, type]);

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

    // Остаток, который подставляет кнопка "Всё":
    // при переводе - остаток слитка-отправителя, при снятии - остаток текущего слитка.
    const getAvailableBalance = () => {
        if (isTransferModal) return maxTransferAmount || 0;
        if (type !== 'withdraw') return 0;
        if (availableAmount !== null && availableAmount !== undefined) {
            const num = typeof availableAmount === 'string' ? parseFloat(availableAmount) : availableAmount;
            return isNaN(num) ? 0 : num;
        }
        const vault = getAvailableVaults().find(v => v.id === parseInt(selectedVaultId));
        return vault?.amount || 0;
    };

    // В пополнении кнопки "Всё" нет
    const showAllButton = (isTransferModal || type === 'withdraw') && getAvailableBalance() > 0;

    // Пока не выбран слиток-отправитель, поле суммы перевода заблокировано
    const quickAmountsDisabled = isTransferModal && !selectedFromOption;

    const applyAmount = (value) => {
        const rounded = Math.round(value * 100) / 100;
        setAmountDisplay(rounded.toLocaleString('ru-RU', {
            minimumFractionDigits: 0,
            maximumFractionDigits: 2
        }));
        setAmount(rounded.toString());
        validateAmount(rounded);
        setError('');
    };

    const handleQuickAdd = (value) => {
        const current = parseFloat(amount);
        applyAmount((isNaN(current) ? 0 : current) + value);
    };

    const renderQuickAmounts = () => (
        <div className="flex flex-wrap gap-1.5">
            {QUICK_AMOUNTS.map(preset => (
                <Button
                    key={preset}
                    type="button"
                    variant="outline"
                    size="xs"
                    onClick={() => handleQuickAdd(preset)}
                    disabled={quickAmountsDisabled}
                >
                    {formatAmount(preset)}
                </Button>
            ))}
            {showAllButton && (
                <Button
                    type="button"
                    variant="secondary"
                    size="xs"
                    onClick={() => applyAmount(getAvailableBalance())}
                    disabled={quickAmountsDisabled}
                >
                    Всё
                </Button>
            )}
        </div>
    );

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

            const targetIsNew = selectedTargetOption.data?.isNew === true;

            try {
                await onSave(
                    amountNum,
                    targetIsNew ? null : selectedTargetOption.value,
                    description.trim() || null,
                    dateOperation, // 👈 ПЕРЕДАЕМ ДАТУ ОПЕРАЦИИ
                    budgetOperation,
                    // Слитка в хранилище нет: перевод адресуется хранилищу,
                    // слиток заведёт бэк в той же транзакции
                    targetIsNew ? selectedTargetOption.data.vaultId : null
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
                await onSave(amountNum, description.trim() || null, selectedVaultId,
                    dateOperation ? dateOperation : null, budgetOperation);
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

    // Строка списка у слитков одна и та же: наименование, хранилище, сумма
    const renderBullionOption = (option) => (
        <span className="flex items-center justify-between gap-3">
            <span className="flex min-w-0 items-center gap-1.5 truncate">
                {option.data?.isNew && <Plus className="size-3.5 shrink-0 text-muted-foreground" />}
                {option.data?.bullionNameTitle}
            </span>
            <span className="truncate text-xs text-muted-foreground">
                {option.data?.vaultName}
            </span>
            {option.data?.isNew ? (
                <span className="text-xs text-muted-foreground">создать</span>
            ) : (
                <span className="font-medium tabular-nums">
                    {formatAmount(option.data?.amount)} ₽
                </span>
            )}
        </span>
    )

    return (
        <FormDialog
            open={isOpen}
            onOpenChange={(open) => !open && onClose()}
            title={title}
            onSubmit={handleSubmit}
            saving={saving}
            submitText={
                isTransferModal && selectedTargetOption?.data?.isNew
                    ? 'Перевести и создать слиток'
                    : buttonText
            }
            savingText="Обработка..."
            submitVariant={isDeleteModal ? 'destructive' : 'default'}
            submitDisabled={isSubmitDisabled()}
            className={isTransferModal ? 'sm:max-w-2xl' : undefined}
        >
            {bullionName && !isConfirm && !isTransferModal && (
                <div className="flex flex-wrap items-center gap-2 rounded-md bg-muted px-3 py-2 text-sm">
                    <span className="text-muted-foreground">Слиток:</span>
                    <span className="font-medium">{bullionName}</span>
                </div>
            )}

            {notice && (
                <p className="text-sm text-muted-foreground">{notice}</p>
            )}

            {isTransferModal && selectedFromOption && (
                <div className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-md bg-muted px-3 py-2 text-sm">
                    <span className="text-muted-foreground">Откуда:</span>
                    <span className="font-medium">
                        {selectedFromOption.data?.bullionNameTitle} | {selectedFromOption.data?.vaultName}
                    </span>
                    <span className="text-muted-foreground">
                        Доступно: {formatAmount(maxTransferAmount)} ₽
                    </span>
                </div>
            )}

            {isConfirm && confirmMessage && <p className="text-sm">{confirmMessage}</p>}

            {isTransferModal && (
                <>
                    {fromBullions && fromBullions.length > 0 && (
                        <Field>
                            <FieldLabel>Откуда переводим *</FieldLabel>
                            <Combobox
                                options={fromBullionOptions}
                                value={selectedFromOption?.value}
                                onChange={(value, option) => handleFromSelect(option)}
                                placeholder="Выберите слиток-отправитель..."
                                emptyText="Нет доступных слитков в этом наименовании"
                                clearable
                                renderOption={renderBullionOption}
                            />
                            <FieldDescription>
                                Выберите слиток, с которого будут переведены средства
                            </FieldDescription>
                        </Field>
                    )}

                    <Field>
                        <FieldLabel htmlFor="transfer-amount">Сумма перевода * (₽)</FieldLabel>
                        <Input
                            id="transfer-amount"
                            type="text"
                            value={amountDisplay}
                            onChange={handleAmountChange}
                            onBlur={handleAmountBlur}
                            onFocus={handleAmountFocus}
                            placeholder="0.00"
                            required
                            inputMode="decimal"
                            autoFocus
                            aria-invalid={!isAmountValid && !!amountError}
                            disabled={!selectedFromOption}
                        />
                        {renderQuickAmounts()}
                        {amountError && (
                            <FieldDescription className="flex items-center gap-1.5 text-destructive">
                                <TriangleAlert className="size-3.5 shrink-0" />
                                {amountError}
                            </FieldDescription>
                        )}
                        <FieldDescription>
                            Максимальная сумма: {formatAmount(maxTransferAmount)} ₽
                        </FieldDescription>
                    </Field>

                    <Field>
                        <FieldLabel>Куда перевести *</FieldLabel>
                        <Combobox
                            options={transferTargetOptions}
                            value={selectedTargetOption?.value}
                            onChange={(value, option) => handleTargetSelect(option)}
                            placeholder="Выберите слиток-получатель..."
                            emptyText="Нет доступных слитков для перевода"
                            clearable
                            renderOption={renderBullionOption}
                        />
                        <FieldDescription>
                            {selectedTargetOption?.data?.isNew
                                ? `В хранилище «${selectedTargetOption.data.vaultName}» слитка «${selectedTargetOption.data.bullionNameTitle}» нет — он будет создан пустым, и деньги придут в него переводом`
                                : 'Выберите слиток, на который будут переведены средства'}
                        </FieldDescription>
                    </Field>

                    {selectedTargetOption && selectedFromOption &&
                        selectedTargetOption.value !== selectedFromOption.value && (
                        <div className="flex flex-col gap-1.5 rounded-md border bg-muted/50 px-3 py-2 text-sm">
                            <div className="flex items-center justify-between gap-2">
                                <span className="font-medium">Выбран получатель</span>
                                {selectedTargetOption.data?.isNew && (
                                    <span className="rounded-full bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                                        Новый слиток
                                    </span>
                                )}
                            </div>
                            <div className="flex justify-between gap-2">
                                <span className="text-muted-foreground">Наименование</span>
                                <span>{selectedTargetOption.data?.bullionNameTitle}</span>
                            </div>
                            <div className="flex justify-between gap-2">
                                <span className="text-muted-foreground">Хранилище</span>
                                <span>{selectedTargetOption.data?.vaultName}</span>
                            </div>
                            <div className="flex justify-between gap-2">
                                <span className="text-muted-foreground">Текущий баланс</span>
                                <span className="tabular-nums">
                                    {formatAmount(selectedTargetOption.data?.amount)} ₽
                                </span>
                            </div>
                            <div className="flex justify-between gap-2 border-t pt-1.5">
                                <span className="text-muted-foreground">Будет после перевода</span>
                                <span className="font-semibold tabular-nums text-primary">
                                    {formatAmount(
                                        (selectedTargetOption.data?.amount || 0) + parseFloat(amount || 0)
                                    )}{' '}
                                    ₽
                                </span>
                            </div>
                        </div>
                    )}

                    {selectedTargetOption && selectedFromOption &&
                        selectedTargetOption.value === selectedFromOption.value && (
                        <ErrorMessage>Нельзя перевести в тот же слиток</ErrorMessage>
                    )}

                    <Field>
                        <FieldLabel htmlFor="transfer-comment">Комментарий</FieldLabel>
                        <Textarea
                            id="transfer-comment"
                            value={description}
                            onChange={(e) => setDescription(e.target.value)}
                            placeholder="Комментарий к переводу (необязательно)..."
                            rows={3}
                        />
                    </Field>
                </>
            )}

            {isDeleteModal && showVaultSelector && vaults.length > 0 && (
                <>
                    <Field>
                        <FieldLabel>Куда перенести остатки?</FieldLabel>
                        <RadioGroup
                            value={toLiquidityVault ? 'liquidity' : 'vault'}
                            onValueChange={(value) => setToLiquidityVault(value === 'liquidity')}
                        >
                            <Label htmlFor="transfer-liquidity" className="font-normal">
                                <RadioGroupItem id="transfer-liquidity" value="liquidity" />
                                Ликвидный резерв
                            </Label>
                            <Label htmlFor="transfer-vault" className="font-normal">
                                <RadioGroupItem id="transfer-vault" value="vault" />
                                Выбрать хранилище
                            </Label>
                        </RadioGroup>
                    </Field>

                    {!toLiquidityVault && (
                        <Field>
                            <FieldLabel>Хранилище для переноса *</FieldLabel>
                            <Combobox
                                options={vaultOptions}
                                value={selectedVaultId}
                                onChange={(value, option) => handleVaultSelect(option)}
                                placeholder="Поиск хранилища..."
                                emptyText="Хранилища не найдены"
                                clearable
                            />
                            <FieldDescription>
                                Начните вводить название для поиска
                            </FieldDescription>
                        </Field>
                    )}
                </>
            )}

            {!isDeleteModal && !isTransferModal && showVaultSelector && vaults.length > 0 && (
                <Field>
                    <FieldLabel>{vaultSelectorLabel} *</FieldLabel>
                    <Combobox
                        options={vaultOptions}
                        value={selectedVaultId}
                        onChange={(value, option) => handleVaultSelect(option)}
                        placeholder="Поиск хранилища..."
                        emptyText="Хранилища не найдены"
                        clearable
                    />
                    {vaultOptions.length === 0 && (
                        <FieldDescription className="flex items-center gap-1.5 text-destructive">
                            <TriangleAlert className="size-3.5 shrink-0" />
                            Нет доступных хранилищ
                        </FieldDescription>
                    )}
                </Field>
            )}

            {showAmount && !isConfirm && !isTransferModal && (
                <Field>
                    <FieldLabel htmlFor="transaction-amount">Сумма * (₽)</FieldLabel>
                    <Input
                        id="transaction-amount"
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
                    {renderQuickAmounts()}
                    <FieldDescription>Используйте точку или запятую для копеек</FieldDescription>
                </Field>
            )}

            {showDescription && !isConfirm && !isTransferModal && (
                <Field>
                    <FieldLabel htmlFor="transaction-description">{descriptionLabel}</FieldLabel>
                    <Textarea
                        id="transaction-description"
                        value={description}
                        onChange={(e) => setDescription(e.target.value)}
                        placeholder={descriptionPlaceholder}
                        rows={3}
                    />
                    <FieldDescription>Необязательное поле</FieldDescription>
                </Field>
            )}

            {/* Галочка появляется только у операций по бюджетному слитку —
                в остальных формах она бы только мозолила глаза */}
            {showBudgetOperation && (
                <Field>
                    <FieldLabel className="flex items-center gap-2 font-normal">
                        <Checkbox
                            checked={budgetOperation}
                            onCheckedChange={(checked) => setBudgetOperation(checked === true)}
                        />
                        Трата бюджета
                    </FieldLabel>
                    <FieldDescription>
                        {isTransferModal
                            ? 'Поставьте, если этот перевод гасит трату — например, вернули деньги за покупку по чужой категории. Без галочки сумма считается пополнением бюджета или переносом остатка.'
                            : 'Снимите, если это пополнение бюджета или перенос остатка, а не трата. Тогда сумма попадёт в «профинансировано», а не в траты дня.'}
                    </FieldDescription>
                </Field>
            )}

            {showDateOperation && (
                <Field>
                    <FieldLabel htmlFor="transaction-date">Дата и время операции *</FieldLabel>
                    <Input
                        id="transaction-date"
                        type="datetime-local"
                        value={dateOperation}
                        onChange={(e) => setDateOperation(e.target.value)}
                        max={`${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}-${String(new Date().getDate()).padStart(2, '0')}T${String(new Date().getHours()).padStart(2, '0')}:${String(new Date().getMinutes()).padStart(2, '0')}`}
                        required
                    />
                </Field>
            )}

            <ErrorMessage>{error}</ErrorMessage>
        </FormDialog>
    )
}

export default BullionTransactionModal