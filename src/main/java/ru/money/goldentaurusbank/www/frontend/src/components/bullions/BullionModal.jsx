import { useState, useEffect } from 'react'
import { TriangleAlert } from 'lucide-react'
import api from '@/api/axios'
import { Button } from '@/components/ui/button'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { FormDialog } from '@/components/ui-app/form-dialog'
import { ErrorMessage } from '@/components/ui-app/page-state'
import { formatAmount, toCents } from '@/lib/format'
import { cn } from '@/lib/utils'

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

    // Пустой слиток — нормальный случай: место под наименование в хранилище,
    // деньги придут потом переводом. Операции у такого создания нет, поэтому
    // ниже прячется и дата: писать её было бы некуда.
    const isEmptyCreate = !isEditing && (amountIsEmpty || enteredCents() === 0);

    const applyEmptyAmount = () => {
        setAmount('0');
        setAmountDisplay('0');
    };

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
                // Ноль подставляем как «0», а не пустотой: у правки пустое поле
                // означало бы «сумму не трогаем», а тут остаток именно нулевой
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
                isEmptyCreate ? null : (dateOperation || null),
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

    return (
        <FormDialog
            open={isOpen}
            onOpenChange={(open) => !open && onClose()}
            title={isEditing ? 'Редактирование слитка' : 'Добавление слитка'}
            onSubmit={handleSubmit}
            saving={saving}
        >
            {isEditing ? (
                <>
                    <Field>
                        <FieldLabel htmlFor="bullion-name-title">Наименование</FieldLabel>
                        <Input
                            id="bullion-name-title"
                            value={initialBullionNameTitle || ''}
                            disabled
                            readOnly
                        />
                        <FieldDescription>Наименование нельзя изменить</FieldDescription>
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="bullion-vault-name">Хранилище</FieldLabel>
                        <Input id="bullion-vault-name" value={getVaultName()} disabled readOnly />
                        <FieldDescription>Хранилище нельзя изменить</FieldDescription>
                    </Field>
                </>
            ) : (
                <>
                    <Field>
                        <FieldLabel htmlFor="bullion-name-select">Наименование *</FieldLabel>
                        <Select value={String(bullionNameId || '')} onValueChange={setBullionNameId}>
                            <SelectTrigger id="bullion-name-select">
                                <SelectValue placeholder="Выберите наименование" />
                            </SelectTrigger>
                            <SelectContent>
                                {bullionNames.map((bn) => (
                                    <SelectItem key={bn.id} value={String(bn.id)}>
                                        {bn.title}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </Field>

                    <Field>
                        <FieldLabel htmlFor="bullion-vault-select">Хранилище *</FieldLabel>
                        <Select value={String(vaultId || '')} onValueChange={setVaultId}>
                            <SelectTrigger id="bullion-vault-select">
                                <SelectValue placeholder="Выберите хранилище" />
                            </SelectTrigger>
                            <SelectContent>
                                {vaults.map((vault) => (
                                    <SelectItem key={vault.id} value={String(vault.id)}>
                                        {vault.name}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                    </Field>
                </>
            )}

            {/* Тип менять можно и при правке — в отличие от наименования и хранилища */}
            <Field>
                <FieldLabel htmlFor="bullion-type">Тип слитка *</FieldLabel>
                <Select value={bullionType} onValueChange={setBullionType}>
                    <SelectTrigger id="bullion-type">
                        <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                        <SelectItem value="DEBIT">Дебетовый</SelectItem>
                        <SelectItem value="CREDIT">Кредитный</SelectItem>
                    </SelectContent>
                </Select>
                <FieldDescription>
                    Дебетовый — свои накопления, кредитный — заёмные средства
                </FieldDescription>
            </Field>

            <Field>
                <FieldLabel htmlFor="bullion-amount">
                    {isEditing ? 'Сумма * (₽)' : 'Сумма (₽)'}
                </FieldLabel>
                <Input
                    id="bullion-amount"
                    type="text"
                    value={amountDisplay}
                    onChange={handleAmountChange}
                    onBlur={handleAmountBlur}
                    onFocus={handleAmountFocus}
                    placeholder="0.00"
                    inputMode="decimal"
                    disabled={amountLocked}
                />
                {!isEditing && (
                    <div className="flex flex-wrap gap-1.5">
                        <Button
                            type="button"
                            variant={isEmptyCreate ? 'secondary' : 'outline'}
                            size="xs"
                            onClick={applyEmptyAmount}
                        >
                            Пустой слиток
                        </Button>
                    </div>
                )}
                {amountLocked && (
                    <FieldDescription className="flex items-center gap-1.5 text-destructive">
                        <TriangleAlert className="size-3.5 shrink-0" />
                        У хранилища сняты галочки внесения и снятия — сумму не изменить
                    </FieldDescription>
                )}
                {!amountLocked && !isEditing && (
                    <FieldDescription>
                        {isEmptyCreate
                            ? 'Слиток заведётся пустым — операции в истории не будет'
                            : 'Используйте точку или запятую для копеек'}
                    </FieldDescription>
                )}
                {!amountLocked && isEditing && directionError() && (
                    <FieldDescription className="flex items-center gap-1.5 text-destructive">
                        <TriangleAlert className="size-3.5 shrink-0" />
                        {directionError()}
                    </FieldDescription>
                )}
                {!amountLocked && isEditing && !directionError() && !amountIsEmpty && (
                    <FieldDescription
                        className={cn(
                            deltaCents > 0 && 'text-success',
                            deltaCents < 0 && 'text-destructive'
                        )}
                    >
                        {deltaCents > 0 &&
                            `Будет проведено пополнение на ${formatAmount(deltaCents / 100)} ₽`}
                        {deltaCents < 0 &&
                            `Будет проведено снятие на ${formatAmount(-deltaCents / 100)} ₽`}
                        {deltaCents === 0 && 'Сумма не изменится — операция не создаётся'}
                    </FieldDescription>
                )}
            </Field>

            {isEditing && deltaCents !== 0 && (
                <Field>
                    <FieldLabel htmlFor="bullion-user-comment">Комментарий к операции</FieldLabel>
                    <Input
                        id="bullion-user-comment"
                        value={userComment}
                        onChange={(e) => setUserComment(e.target.value)}
                        placeholder={deltaCents > 0 ? 'Например: докупил' : 'Например: снял на ремонт'}
                        maxLength={500}
                    />
                    <FieldDescription>
                        Попадёт в историю операций. Если оставить пустым — подставится стандартный текст
                    </FieldDescription>
                </Field>
            )}

            {!isEmptyCreate && (
                <Field>
                    <FieldLabel htmlFor="bullion-date">Дата и время операции *</FieldLabel>
                    <Input
                        id="bullion-date"
                        type="datetime-local"
                        value={dateOperation}
                        onChange={(e) => setDateOperation(e.target.value)}
                        max={`${new Date().getFullYear()}-${String(new Date().getMonth() + 1).padStart(2, '0')}-${String(new Date().getDate()).padStart(2, '0')}T${String(new Date().getHours()).padStart(2, '0')}:${String(new Date().getMinutes()).padStart(2, '0')}`}
                        required
                    />
                </Field>
            )}

            <Field>
                <FieldLabel htmlFor="bullion-description">Описание</FieldLabel>
                <Textarea
                    id="bullion-description"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Дополнительная информация..."
                    rows={3}
                />
            </Field>

            <ErrorMessage>{error}</ErrorMessage>
        </FormDialog>
    )
}

export default BullionModal;