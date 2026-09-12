import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { InfoDialog } from '@/components/ui-app/form-dialog'
import { PIN_LENGTH } from '@/lib/vaultPin'
import { useCardVault } from './CardVaultProvider'

const MIN_WORDS = 4

function countWords(value) {
    return value.trim().split(/\s+/).filter(Boolean).length
}

function VaultSettingsDialog({ open, onOpenChange }) {
    const { busy, pinEnabled, enablePin, disablePin, rotateVaultKey, replacePassphrase } = useCardVault()

    const [pin, setPin] = useState('')
    const [pinConfirm, setPinConfirm] = useState('')
    const [passphrase, setPassphrase] = useState('')
    const [passphraseConfirm, setPassphraseConfirm] = useState('')
    const [error, setError] = useState('')

    useEffect(() => {
        if (!open) return

        setPin('')
        setPinConfirm('')
        setPassphrase('')
        setPassphraseConfirm('')
        setError('')
    }, [open])

    const handleEnablePin = async () => {
        setError('')
        if (pin.length !== PIN_LENGTH) {
            setError(`Пин — ровно ${PIN_LENGTH} цифр`)
            return
        }
        if (pin !== pinConfirm) {
            setError('Пины не совпадают')
            return
        }

        await enablePin(pin)
        setPin('')
        setPinConfirm('')
        toast.success('Пин сохранён на этом компьютере')
    }

    const handleDisablePin = () => {
        disablePin()
        toast.success('Пин удалён, вход только по фразе')
    }

    const handleChangePassphrase = async () => {
        setError('')
        if (countWords(passphrase) < MIN_WORDS) {
            setError(`Фраза должна состоять минимум из ${MIN_WORDS} слов`)
            return
        }
        if (passphrase !== passphraseConfirm) {
            setError('Фразы не совпадают')
            return
        }

        try {
            await replacePassphrase(passphrase)
            setPassphrase('')
            setPassphraseConfirm('')
            toast.success('Фраза изменена. Запишите новую в конверт')
        } catch (err) {
            console.error('Ошибка смены фразы:', err)
        }
    }

    const handleRotate = async () => {
        setError('')
        try {
            await rotateVaultKey()
            toast.success('Ключ сундука сменён')
        } catch (err) {
            console.error('Ошибка смены ключа:', err)
        }
    }

    return (
        <InfoDialog
            open={open}
            onOpenChange={onOpenChange}
            title="Настройки сундука"
            description="Пин действует только в этом браузере, фраза и ключ — везде"
        >
            <div className="flex flex-col gap-6 px-1 pb-1">
                <div className="flex flex-col gap-3 rounded-md border p-3">
                    <div className="font-medium">Вход по пину на этом компьютере</div>
                    {pinEnabled ? (
                        <>
                            <p className="text-sm text-muted-foreground">
                                Пин сохранён. Пять неверных попыток — и он сотрётся сам
                            </p>
                            <Button type="button" variant="outline" onClick={handleDisablePin} disabled={busy}>
                                Удалить пин
                            </Button>
                        </>
                    ) : (
                        <>
                            <Field>
                                <FieldLabel htmlFor="vault-new-pin">Новый пин</FieldLabel>
                                <Input
                                    id="vault-new-pin"
                                    type="password"
                                    inputMode="numeric"
                                    autoComplete="off"
                                    value={pin}
                                    onChange={(e) => setPin(e.target.value.replace(/\D/g, '').slice(0, PIN_LENGTH))}
                                />
                                <FieldDescription>
                                    {PIN_LENGTH} цифр. На чужом компьютере пин не ставим
                                </FieldDescription>
                            </Field>
                            <Field>
                                <FieldLabel htmlFor="vault-new-pin-confirm">Повторите пин</FieldLabel>
                                <Input
                                    id="vault-new-pin-confirm"
                                    type="password"
                                    inputMode="numeric"
                                    autoComplete="off"
                                    value={pinConfirm}
                                    onChange={(e) => setPinConfirm(e.target.value.replace(/\D/g, '').slice(0, PIN_LENGTH))}
                                />
                            </Field>
                            <Button type="button" onClick={handleEnablePin} disabled={busy}>
                                Сохранить пин
                            </Button>
                        </>
                    )}
                </div>

                <div className="flex flex-col gap-3 rounded-md border p-3">
                    <div className="font-medium">Сменить фразу</div>
                    <Field>
                        <FieldLabel htmlFor="vault-new-phrase">Новая фраза</FieldLabel>
                        <Input
                            id="vault-new-phrase"
                            type="password"
                            autoComplete="off"
                            value={passphrase}
                            onChange={(e) => setPassphrase(e.target.value)}
                        />
                        <FieldDescription>Минимум {MIN_WORDS} слова. Карты не перешифровываются</FieldDescription>
                    </Field>
                    <Field>
                        <FieldLabel htmlFor="vault-new-phrase-confirm">Повторите фразу</FieldLabel>
                        <Input
                            id="vault-new-phrase-confirm"
                            type="password"
                            autoComplete="off"
                            value={passphraseConfirm}
                            onChange={(e) => setPassphraseConfirm(e.target.value)}
                        />
                    </Field>
                    <Button type="button" variant="outline" onClick={handleChangePassphrase} disabled={busy}>
                        Сменить фразу
                    </Button>
                </div>

                <div className="flex flex-col gap-3 rounded-md border border-destructive/40 p-3">
                    <div className="font-medium">Сменить ключ сундука</div>
                    <p className="text-sm text-muted-foreground">
                        Нужно после потери устройства: новые версии сундука станут нечитаемыми для того,
                        кто утащил старый ключ. Уже скачанные кем-то копии это не защитит
                    </p>
                    <Button
                        type="button"
                        variant="outline"
                        className="text-destructive hover:text-destructive"
                        onClick={handleRotate}
                        disabled={busy}
                    >
                        Сменить ключ
                    </Button>
                </div>

                {error && <p className="text-sm text-destructive">{error}</p>}
            </div>
        </InfoDialog>
    )
}

export default VaultSettingsDialog
