import { useState, useEffect } from 'react'
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
import { formatAmount } from '@/lib/format'

/**
 * Списание и погашение по карте, а также погашение из накопителя (тип
 * {@code repay-bullion}) — форма у всех троих одна: сумма, дата, комментарий.
 *
 * На экране конкретного хранилища накопитель известен заранее, а на «Моих
 * слитках» их у наименования может быть несколько — тогда сверху появляется
 * выпадающий список {@code bullionOptions}, и выбор решает, какую карту гасим.
 */
function CreditCardOperationModal({ isOpen, card, type, bullion, bullionOptions, onSelectBullion, onClose, onSave, initialAmount = '' }) {
    const [amount, setAmount] = useState('')
    const [comment, setComment] = useState('')
    const [dateOperation, setDateOperation] = useState('')
    const [saving, setSaving] = useState(false)

    useEffect(() => {
        if (!isOpen) return
        setAmount(initialAmount)
        setComment('')
        const now = new Date()
        now.setMinutes(now.getMinutes() - now.getTimezoneOffset())
        setDateOperation(now.toISOString().slice(0, 16))
    }, [isOpen, initialAmount])

    // Смена накопителя меняет и карту, и потолок суммы — введённое до этого
    // число к новому слитку отношения не имеет
    useEffect(() => {
        setAmount(initialAmount)
    }, [bullion?.id, initialAmount])

    if (!isOpen || !card) return null

    const isSpend = type === 'spend'
    const fromBullion = type === 'repay-bullion'

    // Потолок суммы: списание упирается в остаток лимита, погашение — в долг,
    // погашение из накопителя — ещё и в сумму самого слитка
    const max = isSpend
        ? Number(card.remainder || 0)
        : Math.min(Number(card.debt || 0), fromBullion ? Number(bullion?.amount || 0) : Number.MAX_SAFE_INTEGER)

    const title = isSpend
        ? `Списание по карте «${card.name}»`
        : fromBullion
            ? `Погашение карты «${card.name}» из накопителя`
            : `Погашение по карте «${card.name}»`

    const handleSubmit = async (e) => {
        e.preventDefault()
        setSaving(true)
        try {
            await onSave({ amount, comment, dateOperation: dateOperation ? `${dateOperation}:00` : null })
        } catch (err) {
            console.error('Ошибка операции по карте:', err)
        } finally {
            setSaving(false)
        }
    }

    return (
        <FormDialog
            open={isOpen}
            onOpenChange={(open) => !open && onClose()}
            title={title}
            onSubmit={handleSubmit}
            saving={saving}
            submitText={isSpend ? 'Списать' : 'Погасить'}
            submitVariant={isSpend ? 'destructive' : 'default'}
        >
            {fromBullion && bullionOptions?.length > 0 && (
                <Field>
                    <FieldLabel htmlFor="repay-bullion">Гасим со слитка</FieldLabel>
                    <Select
                        value={bullion?.id != null ? String(bullion.id) : ''}
                        onValueChange={(value) => onSelectBullion(Number(value))}
                    >
                        <SelectTrigger id="repay-bullion">
                            <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                            {bullionOptions.map((option) => (
                                <SelectItem key={option.id} value={String(option.id)}>
                                    {option.vaultName} — {formatAmount(option.amount)} ₽ · карта{' '}
                                    {option.creditCardMasked}
                                </SelectItem>
                            ))}
                        </SelectContent>
                    </Select>
                </Field>
            )}

            <div className="flex flex-col gap-1.5 rounded-md bg-muted px-3 py-2 text-sm">
                <div className="flex justify-between gap-3">
                    <span className="text-muted-foreground">Задолженность</span>
                    <span className="font-medium tabular-nums">{formatAmount(card.debt)} ₽</span>
                </div>
                <div className="flex justify-between gap-3">
                    <span className="text-muted-foreground">
                        {isSpend ? 'Доступно по лимиту' : 'Остаток лимита'}
                    </span>
                    <span className="font-medium tabular-nums">{formatAmount(card.remainder)} ₽</span>
                </div>
                {fromBullion && (
                    <div className="flex justify-between gap-3">
                        <span className="text-muted-foreground">
                            В слитке «{bullion?.bullionNameTitle}»
                        </span>
                        <span className="font-medium tabular-nums">
                            {formatAmount(bullion?.amount)} ₽
                        </span>
                    </div>
                )}
            </div>

            <Field>
                <FieldLabel htmlFor="operation-amount">Сумма</FieldLabel>
                <Input
                    id="operation-amount"
                    type="number"
                    step="0.01"
                    min="0.01"
                    max={max > 0 ? max : undefined}
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    required
                    autoFocus
                />
                <div>
                    <Button
                        type="button"
                        variant="secondary"
                        size="xs"
                        onClick={() => setAmount(String(max))}
                        disabled={max <= 0}
                    >
                        {isSpend ? 'Весь остаток' : 'Весь долг'}
                    </Button>
                </div>
                {fromBullion && (
                    <FieldDescription>
                        Одной операцией: деньги уйдут со слитка и уменьшат задолженность карты
                    </FieldDescription>
                )}
            </Field>

            <Field>
                <FieldLabel htmlFor="operation-date">Дата операции</FieldLabel>
                <Input
                    id="operation-date"
                    type="datetime-local"
                    value={dateOperation}
                    onChange={(e) => setDateOperation(e.target.value)}
                />
            </Field>

            <Field>
                <FieldLabel htmlFor="operation-comment">Комментарий</FieldLabel>
                <Textarea
                    id="operation-comment"
                    rows={2}
                    value={comment}
                    onChange={(e) => setComment(e.target.value)}
                    placeholder="Необязательно..."
                />
            </Field>
        </FormDialog>
    )
}

export default CreditCardOperationModal
