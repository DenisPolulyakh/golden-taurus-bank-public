import { useState, useEffect } from 'react'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { FormDialog } from '@/components/ui-app/form-dialog'
import { ErrorMessage } from '@/components/ui-app/page-state'

function BankModal({ isOpen, onClose, onSave, initialName, isEditing }) {
    const [name, setName] = useState('')
    const [error, setError] = useState('')
    const [saving, setSaving] = useState(false)

    useEffect(() => {
        if (isOpen) {
            setName(initialName)
            setError('')
        }
    }, [isOpen, initialName])

    const handleSubmit = async (e) => {
        e.preventDefault()

        const trimmedName = name.trim()
        if (!trimmedName) {
            setError('Название банка обязательно')
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

        setSaving(true)
        setError('')

        try {
            await onSave(trimmedName)
            onClose()
        } catch (err) {
            setError(err.response?.data?.message || 'Ошибка сохранения банка')
        } finally {
            setSaving(false)
        }
    }

    return (
        <FormDialog
            open={isOpen}
            onOpenChange={(open) => !open && onClose()}
            title={isEditing ? 'Редактирование банка' : 'Добавление банка'}
            onSubmit={handleSubmit}
            saving={saving}
        >
            <Field>
                <FieldLabel htmlFor="bank-name">Название банка</FieldLabel>
                <Input
                    id="bank-name"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="например: Сбербанк, Тинькофф, Альфа-Банк"
                    autoFocus
                    aria-invalid={!!error}
                />
                <FieldDescription>Минимум 2 символа, максимум 100</FieldDescription>
            </Field>

            <ErrorMessage>{error}</ErrorMessage>
        </FormDialog>
    )
}

export default BankModal
