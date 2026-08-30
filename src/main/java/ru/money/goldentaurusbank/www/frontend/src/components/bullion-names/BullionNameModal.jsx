import { useState, useEffect } from 'react'
import { Check, TriangleAlert } from 'lucide-react'
import api from '@/api/axios'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { FormDialog } from '@/components/ui-app/form-dialog'
import { ErrorMessage } from '@/components/ui-app/page-state'
import { cn } from '@/lib/utils'

function BullionNameModal({ isOpen, onClose, onSave, initialTitle, initialColor, isEditing }) {
    const [title, setTitle] = useState('')
    const [selectedColor, setSelectedColor] = useState('')
    const [availableColors, setAvailableColors] = useState([])
    const [loadingColors, setLoadingColors] = useState(false)
    const [error, setError] = useState('')
    const [saving, setSaving] = useState(false)

    useEffect(() => {
        if (isOpen) {
            setTitle(initialTitle || '')
            setSelectedColor(initialColor || '')
            setError('')
            fetchAvailableColors()
        }
    }, [isOpen, initialTitle, initialColor])

    const fetchAvailableColors = async () => {
        setLoadingColors(true)
        try {
            const response = await api.get('/bullion-names/colors/available')
            const colors = response.data.data || []
            setAvailableColors(colors)

            // Если нет выбранного цвета и есть доступные - выбираем первый
            if (!selectedColor && colors.length > 0) {
                setSelectedColor(colors[0])
            }

            // Если выбранный цвет недоступен (был занят) - выбираем первый доступный
            if (selectedColor && colors.length > 0 && !colors.includes(selectedColor)) {
                setSelectedColor(colors[0])
            }
        } catch (err) {
            console.error('Ошибка загрузки цветов:', err)
            setError('Не удалось загрузить доступные цвета')
        } finally {
            setLoadingColors(false)
        }
    }

    const handleSubmit = async (e) => {
        e.preventDefault()

        const trimmedTitle = title.trim()
        if (!trimmedTitle) {
            setError('Название наименования обязательно')
            return
        }
        if (trimmedTitle.length < 2) {
            setError('Название должно содержать минимум 2 символа')
            return
        }
        if (trimmedTitle.length > 100) {
            setError('Название не должно превышать 100 символов')
            return
        }

        if (!selectedColor) {
            setError('Пожалуйста, выберите цвет')
            return
        }

        setSaving(true)
        setError('')

        try {
            await onSave(trimmedTitle, selectedColor)
            onClose()
        } catch (err) {
            setError(err.response?.data?.message || 'Ошибка сохранения наименования')
        } finally {
            setSaving(false)
        }
    }

    return (
        <FormDialog
            open={isOpen}
            onOpenChange={(open) => !open && onClose()}
            title={isEditing ? 'Редактирование наименования' : 'Добавление наименования'}
            onSubmit={handleSubmit}
            saving={saving}
            submitDisabled={loadingColors || availableColors.length === 0}
        >
            <Field>
                <FieldLabel htmlFor="bullion-name-title">Название наименования</FieldLabel>
                <Input
                    id="bullion-name-title"
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="например: Продукты, Транспорт, Зарплата"
                    autoFocus
                />
                <FieldDescription>Минимум 2 символа, максимум 100</FieldDescription>
            </Field>

            <Field>
                <FieldLabel>Цвет наименования</FieldLabel>
                {loadingColors ? (
                    <div className="flex flex-wrap gap-2">
                        {Array.from({ length: 8 }).map((_, i) => (
                            <Skeleton key={i} className="size-9 rounded-full" />
                        ))}
                    </div>
                ) : (
                    <>
                        <div className="flex flex-wrap gap-2">
                            {availableColors.map((color) => (
                                <button
                                    key={color}
                                    type="button"
                                    onClick={() => setSelectedColor(color)}
                                    title={color}
                                    aria-label={color}
                                    aria-pressed={selectedColor === color}
                                    className={cn(
                                        'flex size-9 items-center justify-center rounded-full ring-offset-2 ring-offset-background transition-shadow',
                                        selectedColor === color
                                            ? 'ring-2 ring-foreground'
                                            : 'hover:ring-2 hover:ring-border'
                                    )}
                                    style={{ backgroundColor: color }}
                                >
                                    {selectedColor === color && (
                                        <Check className="size-4 text-white drop-shadow" />
                                    )}
                                </button>
                            ))}
                        </div>

                        {availableColors.length === 0 && (
                            <ErrorMessage>
                                <span className="flex items-center gap-2">
                                    <TriangleAlert className="size-4 shrink-0" />
                                    Все цвета заняты! Удалите или измените существующие наименования.
                                </span>
                            </ErrorMessage>
                        )}

                        <FieldDescription className="flex flex-wrap items-center gap-x-4 gap-y-1">
                            <span>Доступно цветов: {availableColors.length}</span>
                            {selectedColor && (
                                <span className="flex items-center gap-1.5">
                                    Выбран:
                                    <span
                                        className="inline-block size-3 rounded-full border"
                                        style={{ backgroundColor: selectedColor }}
                                    />
                                    {selectedColor}
                                </span>
                            )}
                        </FieldDescription>
                    </>
                )}
            </Field>

            <ErrorMessage>{error}</ErrorMessage>
        </FormDialog>
    )
}

export default BullionNameModal
