import { useState, useEffect } from 'react'
import { KeyRound } from 'lucide-react'
import api from '@/api/axios'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { DatePicker } from '@/components/ui-app/date-picker'
import { FormDialog } from '@/components/ui-app/form-dialog'
import { formatAmount } from '@/lib/format'
import { useCardVault } from './CardVaultProvider'

const EMPTY_REQUISITES = { pan: '', account: '', bic: '', receiver: '', note: '' }

/**
 * Заведение и правка карты. Полный номер нигде не хранится и не спрашивается —
 * только последние 4 цифры. При правке поле приходит пустым: пустое означает
 * «оставить прежние».
 */
function CreditCardModal({ isOpen, card, onClose, onSave }) {
    const isEditing = !!card

    const [name, setName] = useState('')
    const [last4, setLast4] = useState('')
    const [gracePeriodDate, setGracePeriodDate] = useState('')
    const [paymentAmount, setPaymentAmount] = useState('')
    const [limit, setLimit] = useState('')
    const [debt, setDebt] = useState('')
    const [bullionIds, setBullionIds] = useState([])
    const [availableBullions, setAvailableBullions] = useState([])
    const [requisites, setRequisites] = useState(EMPTY_REQUISITES)
    const [saving, setSaving] = useState(false)

    const vault = useCardVault()

    useEffect(() => {
        if (!isOpen) return

        setName(card?.name || '')
        setLast4('')
        setGracePeriodDate(card?.gracePeriodDate || '')
        setPaymentAmount(card?.paymentAmount ?? '')
        setLimit(card?.limit ?? '')
        setDebt(card?.debt ?? '')
        setBullionIds((card?.accumulators || []).map((item) => item.bullionId))
        const saved = card && vault.isOpen ? vault.cardById(card.id) : null
        setRequisites(saved
            ? {
                pan: saved.pan || '',
                account: saved.account || '',
                bic: saved.bic || '',
                receiver: saved.receiver || '',
                note: saved.note || '',
            }
            : EMPTY_REQUISITES)

        const params = card ? `?cardId=${card.id}` : ''
        api.get(`/credit-cards/available-bullions${params}`)
            .then((response) => setAvailableBullions(response.data.data || []))
            .catch((err) => console.error('Ошибка загрузки слитков:', err))
    }, [isOpen, card, vault.isOpen])

    const changeRequisite = (field, value) => {
        setRequisites((prev) => ({ ...prev, [field]: value }))
    }

    const changePan = (value) => {
        const digits = value.replace(/\D/g, '').slice(0, 19)
        changeRequisite('pan', digits)
        if (digits.length >= 12) {
            setLast4(digits.slice(-4))
        }
    }

    const toggleBullion = (bullionId) => {
        setBullionIds((prev) =>
            prev.includes(bullionId)
                ? prev.filter((id) => id !== bullionId)
                : [...prev, bullionId]
        )
    }

    const handleSubmit = async (e) => {
        e.preventDefault()
        setSaving(true)
        try {
            await onSave({
                name,
                last4,
                gracePeriodDate,
                paymentAmount,
                limit,
                debt,
                bullionIds,
                requisites: vault.isOpen ? { ...requisites, last4: requisites.pan.slice(-4) || last4 } : null,
            })
        } catch (err) {
            // Текст ошибки показывает общий обработчик axios — форму не закрываем
            console.error('Ошибка сохранения карты:', err.response?.status, err.response?.data?.message)
        } finally {
            setSaving(false)
        }
    }

    return (
        <FormDialog
            open={isOpen}
            onOpenChange={(open) => !open && onClose()}
            title={isEditing ? 'Редактировать карту' : 'Новая кредитная карта'}
            onSubmit={handleSubmit}
            saving={saving}
            submitText={isEditing ? 'Сохранить' : 'Добавить'}
        >
            <Field>
                <FieldLabel htmlFor="card-name">Название карты</FieldLabel>
                <Input
                    id="card-name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Например, Тинькофф Платинум"
                    required
                    autoFocus
                />
            </Field>

            <Field>
                <FieldLabel htmlFor="card-last4">Последние 4 цифры карты</FieldLabel>
                <Input
                    id="card-last4"
                    type="text"
                    inputMode="numeric"
                    maxLength={4}
                    value={last4}
                    // Всё, кроме цифр, отсекаем на вводе: поле короткое,
                    // и ругаться на пробел после сохранения было бы глупо
                    onChange={(e) => setLast4(e.target.value.replace(/\D/g, '').slice(0, 4))}
                    placeholder={isEditing ? card.maskedNumber : '1234'}
                    required={!isEditing}
                />
                <FieldDescription>
                    {isEditing
                        ? 'Оставьте пустым, чтобы не менять цифры'
                        : 'Полный номер не хранится — только эти 4 цифры, для поиска и маски'}
                </FieldDescription>
            </Field>

            <Field>
                <FieldLabel htmlFor="card-grace-date">Ближайший платёж</FieldLabel>
                <DatePicker
                    id="card-grace-date"
                    value={gracePeriodDate}
                    onChange={setGracePeriodDate}
                    clearable
                />
            </Field>

            <Field>
                <FieldLabel htmlFor="card-payment-amount">К внесению</FieldLabel>
                <Input
                    id="card-payment-amount"
                    type="number"
                    step="0.01"
                    min="0"
                    value={paymentAmount}
                    onChange={(e) => setPaymentAmount(e.target.value)}
                    placeholder="—"
                />
                <FieldDescription>
                    Сколько внести к дате платежа. Необязательно: пусто — значит платёж не задан
                </FieldDescription>
            </Field>

            <Field>
                <FieldLabel htmlFor="card-limit">Лимит</FieldLabel>
                <Input
                    id="card-limit"
                    type="number"
                    step="0.01"
                    min="0"
                    value={limit}
                    onChange={(e) => setLimit(e.target.value)}
                    placeholder="0"
                />
            </Field>

            <Field>
                <FieldLabel htmlFor="card-debt">Задолженность</FieldLabel>
                <Input
                    id="card-debt"
                    type="number"
                    step="0.01"
                    min="0"
                    value={debt}
                    onChange={(e) => setDebt(e.target.value)}
                    placeholder="0"
                />
                <FieldDescription>
                    Изменение задолженности записывается операцией и попадает в историю карты
                </FieldDescription>
            </Field>

            <Field>
                <FieldLabel>Реквизиты для пополнения</FieldLabel>
                {vault.isOpen ? (
                    <div className="flex flex-col gap-3 rounded-md border p-3">
                        <Input
                            inputMode="numeric"
                            value={requisites.pan}
                            onChange={(e) => changePan(e.target.value)}
                            placeholder="Номер карты"
                        />
                        <Input
                            inputMode="numeric"
                            value={requisites.account}
                            onChange={(e) => changeRequisite('account', e.target.value.replace(/\D/g, '').slice(0, 20))}
                            placeholder="Номер счёта"
                        />
                        <Input
                            inputMode="numeric"
                            value={requisites.bic}
                            onChange={(e) => changeRequisite('bic', e.target.value.replace(/\D/g, '').slice(0, 9))}
                            placeholder="БИК"
                        />
                        <Input
                            value={requisites.receiver}
                            onChange={(e) => changeRequisite('receiver', e.target.value)}
                            placeholder="Получатель"
                        />
                        <Input
                            value={requisites.note}
                            onChange={(e) => changeRequisite('note', e.target.value)}
                            placeholder="Заметка"
                        />
                    </div>
                ) : (
                    <div className="flex flex-col items-start gap-2 rounded-md border border-dashed px-3 py-3">
                        <p className="text-sm text-muted-foreground">
                            Реквизиты заперты. Откройте сундук фразой, чтобы их заполнить
                        </p>
                        <Button type="button" variant="outline" onClick={vault.requestUnlock}>
                            <KeyRound />
                            Открыть реквизиты
                        </Button>
                    </div>
                )}
                <FieldDescription>
                    Шифруются в браузере, на сервер уходит только шифротекст
                </FieldDescription>
            </Field>

            <Field>
                <FieldLabel>Накопитель</FieldLabel>
                {availableBullions.length === 0 ? (
                    <p className="rounded-md border border-dashed px-3 py-2 text-sm text-muted-foreground">
                        Нет свободных кредитных слитков — заведите слиток с типом «Кредитный»
                    </p>
                ) : (
                    <div className="flex flex-col gap-1 rounded-md border p-2">
                        {availableBullions.map((bullion) => (
                            <Label
                                key={bullion.bullionId}
                                htmlFor={`bullion-${bullion.bullionId}`}
                                className="justify-between gap-3 rounded-sm px-2 py-1.5 font-normal hover:bg-accent"
                            >
                                <span className="flex min-w-0 items-center gap-2">
                                    <Checkbox
                                        id={`bullion-${bullion.bullionId}`}
                                        checked={bullionIds.includes(bullion.bullionId)}
                                        onCheckedChange={() => toggleBullion(bullion.bullionId)}
                                    />
                                    <span
                                        className="truncate font-medium"
                                        style={
                                            bullion.bullionNameColor
                                                ? { color: bullion.bullionNameColor }
                                                : undefined
                                        }
                                    >
                                        {bullion.bullionNameTitle}
                                    </span>
                                    <span className="truncate text-muted-foreground">
                                        | {bullion.vaultName}
                                    </span>
                                </span>
                                <span className="shrink-0 tabular-nums">
                                    {formatAmount(bullion.amount)} ₽
                                </span>
                            </Label>
                        ))}
                    </div>
                )}
                <FieldDescription>
                    В списке только кредитные слитки, не занятые другими картами
                </FieldDescription>
            </Field>
        </FormDialog>
    )
}

export default CreditCardModal
