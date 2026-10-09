import { useState, useEffect } from 'react'
import api from '@/api/axios'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select'
import { Textarea } from '@/components/ui/textarea'
import { DatePicker } from '@/components/ui-app/date-picker'
import { FormDialog } from '@/components/ui-app/form-dialog'
import { ErrorMessage } from '@/components/ui-app/page-state'

// Radix Select не умеет пустую строку в качестве значения пункта,
// поэтому «банк не выбран» ездит отдельным сторожевым значением
const NO_BANK = 'none'

const ACCOUNT_TYPES = [
    { value: 'SAVINGS', label: 'Накопительный' },
    { value: 'TERM', label: 'Срочный' },
]

const ALLOW_FLAGS = [
    { key: 'expense', label: 'снять' },
    { key: 'income', label: 'внести' },
    { key: 'transfer', label: 'перевод' },
]

function VaultModal({
    isOpen, onClose, onSave,
    initialName, initialInterestRate, initialDescription, initialBankId,
    initialAccountType, initialCloseDate,
    initialAllowedIncome = true, initialAllowedExpense = true, initialAllowedTransfer = true,
    isEditing,
}) {
    const [name, setName] = useState('')
    const [interestRate, setInterestRate] = useState('')
    const [interestRateDisplay, setInterestRateDisplay] = useState('')
    const [description, setDescription] = useState('')
    const [banks, setBanks] = useState([])
    const [bankId, setBankId] = useState('')
    const [accountType, setAccountType] = useState('SAVINGS')
    const [closeDate, setCloseDate] = useState('')
    // Галочки разрешённых операций: бэк хранит их в vaults.allowed_*,
    // и только они решают, доступна ли операция.
    const [allowedIncome, setAllowedIncome] = useState(true)
    const [allowedExpense, setAllowedExpense] = useState(true)
    const [allowedTransfer, setAllowedTransfer] = useState(true)
    const [error, setError] = useState('')
    const [saving, setSaving] = useState(false)

    const allowSetters = {
        income: setAllowedIncome,
        expense: setAllowedExpense,
        transfer: setAllowedTransfer,
    }
    const allowValues = {
        income: allowedIncome,
        expense: allowedExpense,
        transfer: allowedTransfer,
    }

    useEffect(() => {
        if (isOpen) {
            fetchBanks()
            setName(initialName || '')
            setBankId(initialBankId || '')
            setAccountType(initialAccountType || 'SAVINGS')
            setCloseDate(initialCloseDate || '')
            setAllowedIncome(initialAllowedIncome !== false)
            setAllowedExpense(initialAllowedExpense !== false)
            setAllowedTransfer(initialAllowedTransfer !== false)

            if (initialInterestRate !== undefined && initialInterestRate !== null && initialInterestRate !== '') {
                const numRate = typeof initialInterestRate === 'string' ? parseFloat(initialInterestRate) : initialInterestRate
                if (!isNaN(numRate)) {
                    setInterestRateDisplay(formatInterestRate(numRate))
                    setInterestRate(numRate.toString())
                } else {
                    setInterestRateDisplay('')
                    setInterestRate('')
                }
            } else {
                setInterestRateDisplay('')
                setInterestRate('')
            }

            setDescription(initialDescription || '')
            setError('')
        }
    }, [isOpen, initialName, initialInterestRate, initialDescription, initialBankId, initialAccountType, initialCloseDate,
        initialAllowedIncome, initialAllowedExpense, initialAllowedTransfer])

    const fetchBanks = async () => {
        try {
            const response = await api.get('/banks')
            setBanks(response.data.data || [])
        } catch (err) {
            console.error('Ошибка загрузки банков:', err)
        }
    }

    function formatInterestRate(value) {
        if (!value && value !== 0) return '0'
        const num = typeof value === 'string' ? parseFloat(value) : value
        if (isNaN(num)) return '0'
        const parts = num.toFixed(2).split('.')
        const formattedInteger = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ' ')
        const trimmedDecimal = parts[1].replace(/0+$/, '')
        return trimmedDecimal ? `${formattedInteger}.${trimmedDecimal}` : formattedInteger
    }

    const handleInterestRateChange = (e) => {
        let value = e.target.value
        value = value.replace(/[^\d.,]/g, '')
        value = value.replace(',', '.')
        const parts = value.split('.')
        if (parts.length > 2) {
            value = parts[0] + '.' + parts.slice(1).join('')
        }
        if (parts.length === 2 && parts[1].length > 2) {
            value = parts[0] + '.' + parts[1].substring(0, 2)
        }
        setInterestRateDisplay(value)
        const number = parseFloat(value)
        if (!isNaN(number)) {
            setInterestRate(number.toString())
        } else if (value === '' || value === '.') {
            setInterestRate('')
        }
    }

    const handleInterestRateBlur = () => {
        if (interestRateDisplay && interestRateDisplay !== '.') {
            const number = parseFloat(interestRateDisplay)
            if (!isNaN(number)) {
                setInterestRateDisplay(formatInterestRate(number))
                setInterestRate(number.toString())
            }
        } else if (interestRateDisplay === '.') {
            setInterestRateDisplay('')
            setInterestRate('')
        }
    }

    const handleInterestRateFocus = () => {
        if (interestRate) {
            setInterestRateDisplay(interestRate)
        }
    }

    const handleSubmit = async (e) => {
        e.preventDefault()

        const trimmedName = name.trim()
        if (!trimmedName) {
            setError('Название хранилища обязательно')
            return
        }
        if (trimmedName.length < 2) {
            setError('Название должно содержать минимум 2 символа')
            return
        }
        if (trimmedName.length > 100) {
            setError('Название не должно превышать 100 символов')
            return
        }

        let rateNum = 0
        if (interestRate) {
            rateNum = parseFloat(interestRate)
            if (isNaN(rateNum)) rateNum = 0
        } else if (interestRateDisplay) {
            const cleanRate = interestRateDisplay.replace(/\s/g, '').replace(',', '.')
            rateNum = parseFloat(cleanRate)
            if (isNaN(rateNum)) rateNum = 0
        }

        if (rateNum < 0) {
            setError('Процентная ставка не может быть отрицательной')
            return
        }
        if (rateNum > 100) {
            setError('Процентная ставка не может превышать 100%')
            return
        }

        // Проверка даты закрытия для срочного вклада
        if (accountType === 'TERM' && !closeDate) {
            setError('Для срочного вклада укажите дату закрытия')
            return
        }

        setSaving(true)
        setError('')

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
                allowedTransfer,
            })
            onClose()
        } catch (err) {
            setError(err.response?.data?.message || 'Ошибка сохранения хранилища')
        } finally {
            setSaving(false)
        }
    }

    return (
        <FormDialog
            open={isOpen}
            onOpenChange={(open) => !open && onClose()}
            title={isEditing ? 'Редактирование хранилища' : 'Добавление хранилища'}
            onSubmit={handleSubmit}
            saving={saving}
        >
            <Field>
                <FieldLabel htmlFor="vault-name">Название хранилища *</FieldLabel>
                <Input
                    id="vault-name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="например: Сберегательный, Депозитный"
                    autoFocus
                />
                <FieldDescription>Минимум 2 символа, максимум 100</FieldDescription>
            </Field>

            <Field>
                <FieldLabel htmlFor="vault-bank">Банк</FieldLabel>
                <Select
                    value={bankId ? String(bankId) : NO_BANK}
                    onValueChange={(value) => setBankId(value === NO_BANK ? '' : value)}
                >
                    <SelectTrigger id="vault-bank">
                        <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                        <SelectItem value={NO_BANK}>Не выбран</SelectItem>
                        {banks.map((bank) => (
                            <SelectItem key={bank.id} value={String(bank.id)}>
                                {bank.name}
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
                <FieldDescription>Выберите банк из справочника</FieldDescription>
            </Field>

            <Field>
                <FieldLabel htmlFor="vault-account-type">Тип счета *</FieldLabel>
                <Select value={accountType} onValueChange={setAccountType}>
                    <SelectTrigger id="vault-account-type">
                        <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                        {ACCOUNT_TYPES.map((type) => (
                            <SelectItem key={type.value} value={type.value}>
                                {type.label}
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </Field>

            {accountType === 'TERM' && (
                <Field>
                    <FieldLabel htmlFor="vault-close-date">Дата закрытия *</FieldLabel>
                    <DatePicker id="vault-close-date" value={closeDate} onChange={setCloseDate} />
                    <FieldDescription>Дата, когда срочный вклад будет закрыт</FieldDescription>
                </Field>
            )}

            <Field>
                <FieldLabel>Можно:</FieldLabel>
                <div className="flex flex-wrap gap-6">
                    {ALLOW_FLAGS.map(({ key, label }) => (
                        <Label key={key} htmlFor={`vault-allow-${key}`} className="font-normal">
                            <Checkbox
                                id={`vault-allow-${key}`}
                                checked={allowValues[key]}
                                onCheckedChange={(checked) => allowSetters[key](checked === true)}
                            />
                            {label}
                        </Label>
                    ))}
                </div>
                <FieldDescription>
                    Снятая галочка гасит кнопку на всех экранах слитков
                </FieldDescription>
            </Field>

            <Field>
                <FieldLabel htmlFor="vault-rate">Процентная ставка (%)</FieldLabel>
                <Input
                    id="vault-rate"
                    type="text"
                    value={interestRateDisplay}
                    onChange={handleInterestRateChange}
                    onBlur={handleInterestRateBlur}
                    onFocus={handleInterestRateFocus}
                    placeholder="0.00"
                    inputMode="decimal"
                />
                <FieldDescription>Используйте точку или запятую. От 0 до 100%.</FieldDescription>
            </Field>

            <Field>
                <FieldLabel htmlFor="vault-description">Описание (необязательно)</FieldLabel>
                <Textarea
                    id="vault-description"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Краткое описание хранилища..."
                    rows={3}
                />
                <FieldDescription>Максимум 500 символов</FieldDescription>
            </Field>

            <ErrorMessage>{error}</ErrorMessage>
        </FormDialog>
    )
}

export default VaultModal
