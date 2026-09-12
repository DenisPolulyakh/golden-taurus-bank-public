import { useCallback, useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Copy, Eye, EyeOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { InfoDialog } from '@/components/ui-app/form-dialog'
import { useCardVault } from './CardVaultProvider'

const HIDE_AFTER_MS = 30 * 1000
const CLIPBOARD_CLEAR_MS = 30 * 1000

function groupDigits(value) {
    return value.replace(/(.{4})/g, '$1 ').trim()
}

function mask(value) {
    return value ? `•••• ${value.slice(-4)}` : '—'
}

function CardRequisitesDialog({ card, onClose }) {
    const vault = useCardVault()

    const [revealed, setRevealed] = useState({})
    const timersRef = useRef({})

    const entry = card ? vault.cardById(card.id) : null

    const clearTimers = useCallback(() => {
        Object.values(timersRef.current).forEach((timer) => clearTimeout(timer))
        timersRef.current = {}
    }, [])

    useEffect(() => {
        setRevealed({})
        clearTimers()

        return clearTimers
    }, [card, clearTimers])

    useEffect(() => {
        if (card && !vault.isOpen) {
            onClose()
        }
    }, [card, vault.isOpen, onClose])

    const toggle = (field) => {
        setRevealed((prev) => {
            const next = { ...prev, [field]: !prev[field] }
            if (timersRef.current[field]) {
                clearTimeout(timersRef.current[field])
            }
            if (next[field]) {
                timersRef.current[field] = setTimeout(
                    () => setRevealed((state) => ({ ...state, [field]: false })),
                    HIDE_AFTER_MS
                )
            }
            return next
        })
    }

    const copy = async (value) => {
        try {
            await navigator.clipboard.writeText(value)
            toast.success('Скопировано, буфер очистится через 30 секунд')
            setTimeout(() => {
                navigator.clipboard.writeText('').catch(() => {})
            }, CLIPBOARD_CLEAR_MS)
        } catch (error) {
            toast.error('Браузер не дал доступ к буферу обмена')
        }
    }

    return (
        <InfoDialog
            open={!!card && !!entry}
            onOpenChange={(open) => !open && onClose()}
            title={`Реквизиты: ${card?.name || ''}`}
            description="Расшифровано в браузере. Прячется само через 30 секунд"
            className="sm:max-w-lg"
        >
            <div className="flex flex-col gap-3 px-1">
                <SecretRow
                    label="Номер карты"
                    value={entry?.pan}
                    display={revealed.pan ? groupDigits(entry?.pan || '') : mask(entry?.pan)}
                    revealed={!!revealed.pan}
                    onToggle={() => toggle('pan')}
                    onCopy={() => copy(entry?.pan || '')}
                />
                <SecretRow
                    label="Номер счёта"
                    value={entry?.account}
                    display={revealed.account ? entry?.account : mask(entry?.account)}
                    revealed={!!revealed.account}
                    onToggle={() => toggle('account')}
                    onCopy={() => copy(entry?.account || '')}
                />
                <PlainRow label="БИК" value={entry?.bic} onCopy={() => copy(entry?.bic || '')} />
                <PlainRow
                    label="Получатель"
                    value={entry?.receiver}
                    onCopy={() => copy(entry?.receiver || '')}
                />
                {entry?.note && <PlainRow label="Заметка" value={entry.note} />}
            </div>
        </InfoDialog>
    )
}

const SecretRow = ({ label, value, display, revealed, onToggle, onCopy }) => (
    <div className="flex items-center justify-between gap-3 rounded-md border px-3 py-2">
        <div className="min-w-0">
            <div className="text-xs text-muted-foreground">{label}</div>
            <div className="truncate font-medium tabular-nums">{display || '—'}</div>
        </div>
        <div className="flex shrink-0 gap-1">
            <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={onToggle}
                disabled={!value}
                aria-label={revealed ? 'Скрыть' : 'Показать'}
            >
                {revealed ? <EyeOff /> : <Eye />}
            </Button>
            <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={onCopy}
                disabled={!value}
                aria-label="Скопировать"
            >
                <Copy />
            </Button>
        </div>
    </div>
)

const PlainRow = ({ label, value, onCopy }) => (
    <div className="flex items-center justify-between gap-3 rounded-md border px-3 py-2">
        <div className="min-w-0">
            <div className="text-xs text-muted-foreground">{label}</div>
            <div className="truncate font-medium">{value || '—'}</div>
        </div>
        {onCopy && (
            <Button
                type="button"
                variant="ghost"
                size="icon"
                onClick={onCopy}
                disabled={!value}
                aria-label="Скопировать"
            >
                <Copy />
            </Button>
        )}
    </div>
)

export default CardRequisitesDialog
