import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { FormDialog } from '@/components/ui-app/form-dialog'
import { WRONG_PASSPHRASE } from '@/lib/cardVault'
import { NO_PIN, PIN_BLOCKED, PIN_LENGTH, WRONG_PIN } from '@/lib/vaultPin'
import { useCardVault } from './CardVaultProvider'

const MIN_WORDS = 4

function countWords(value) {
    return value.trim().split(/\s+/).filter(Boolean).length
}

function VaultUnlockDialog() {
    const {
        exists,
        busy,
        unlock,
        unlockWithPin,
        pinEnabled,
        syncPin,
        unlockOpen: open,
        setUnlockOpen: onOpenChange,
    } = useCardVault()

    const [usePin, setUsePin] = useState(false)
    const [pin, setPin] = useState('')
    const [passphrase, setPassphrase] = useState('')
    const [confirmation, setConfirmation] = useState('')
    const [error, setError] = useState('')

    useEffect(() => {
        if (!open) return

        setUsePin(syncPin() && exists)
        setPin('')
        setPassphrase('')
        setConfirmation('')
        setError('')
    }, [open, exists, syncPin])

    const submitPin = async () => {
        try {
            await unlockWithPin(pin)
            onOpenChange(false)
            toast.success('Реквизиты открыты')
        } catch (err) {
            if (err.code === WRONG_PIN) {
                setError(`Неверный пин, осталось попыток: ${err.attemptsLeft}`)
                return
            }
            if (err.code === PIN_BLOCKED) {
                setUsePin(false)
                setError('Пин стёрт после пяти неверных попыток. Введите фразу')
                return
            }
            if (err.code === NO_PIN) {
                setUsePin(false)
                setError('Пин на этом компьютере не сохранён. Введите фразу')
                return
            }
            if (err.code === WRONG_PASSPHRASE) {
                setUsePin(false)
                setError('Сохранённый пин больше не подходит — фраза или ключ менялись. Введите фразу')
                return
            }
            console.error('Ошибка открытия по пину:', err)
        }
    }

    const submitPassphrase = async () => {
        if (!exists) {
            if (countWords(passphrase) < MIN_WORDS) {
                setError(`Фраза должна состоять минимум из ${MIN_WORDS} слов`)
                return
            }
            if (passphrase !== confirmation) {
                setError('Фразы не совпадают')
                return
            }
        }

        try {
            await unlock(passphrase)
            onOpenChange(false)
            toast.success(exists ? 'Реквизиты открыты' : 'Сундук создан')
        } catch (err) {
            if (err.code === WRONG_PASSPHRASE) {
                setError('Фраза не подходит')
                return
            }
            console.error('Ошибка открытия реквизитов:', err)
        }
    }

    const handleSubmit = async (e) => {
        e.preventDefault()
        setError('')

        if (usePin) {
            await submitPin()
        } else {
            await submitPassphrase()
        }
    }

    return (
        <FormDialog
            open={open}
            onOpenChange={onOpenChange}
            title={exists ? 'Открыть реквизиты' : 'Создать сундук для реквизитов'}
            description={
                usePin
                    ? 'Пин действует только на этом компьютере'
                    : exists
                      ? 'Расшифровка идёт в браузере и занимает пару секунд'
                      : 'Фраза нигде не сохраняется. Забудете её — реквизиты не восстановить'
            }
            onSubmit={handleSubmit}
            saving={busy}
            savingText="Открываем..."
            submitText={exists ? 'Открыть' : 'Создать'}
            submitDisabled={usePin ? pin.length !== PIN_LENGTH : !passphrase}
            footer={
                pinEnabled && exists ? (
                    <Button
                        type="button"
                        variant="ghost"
                        className="mr-auto"
                        onClick={() => {
                            setUsePin((prev) => !prev)
                            setError('')
                        }}
                        disabled={busy}
                    >
                        {usePin ? 'Ввести фразу' : 'Ввести пин'}
                    </Button>
                ) : null
            }
        >
            {usePin ? (
                <Field>
                    <FieldLabel htmlFor="vault-pin">Пин</FieldLabel>
                    <Input
                        id="vault-pin"
                        type="password"
                        inputMode="numeric"
                        autoComplete="off"
                        value={pin}
                        onChange={(e) => setPin(e.target.value.replace(/\D/g, '').slice(0, PIN_LENGTH))}
                    />
                    <FieldDescription>{PIN_LENGTH} цифр</FieldDescription>
                </Field>
            ) : (
                <>
                    <Field>
                        <FieldLabel htmlFor="vault-passphrase">Фраза</FieldLabel>
                        <Input
                            id="vault-passphrase"
                            type="password"
                            autoComplete="off"
                            value={passphrase}
                            onChange={(e) => setPassphrase(e.target.value)}
                        />
                        {!exists && (
                            <FieldDescription>
                                Минимум {MIN_WORDS} случайных слова, например: тапок компот вулкан редиска
                            </FieldDescription>
                        )}
                    </Field>

                    {!exists && (
                        <Field>
                            <FieldLabel htmlFor="vault-passphrase-confirm">Повторите фразу</FieldLabel>
                            <Input
                                id="vault-passphrase-confirm"
                                type="password"
                                autoComplete="off"
                                value={confirmation}
                                onChange={(e) => setConfirmation(e.target.value)}
                            />
                        </Field>
                    )}
                </>
            )}

            {error && <p className="text-sm text-destructive">{error}</p>}
        </FormDialog>
    )
}

export default VaultUnlockDialog
