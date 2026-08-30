import { useCallback, useState } from 'react'
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
} from '@/components/ui/alert-dialog'

/**
 * Замена `window.confirm`. Использование повторяет прежний код один в один:
 *
 *   const { confirm, confirmDialog } = useConfirm()
 *   ...
 *   confirm({ description: `Удалить банк "${name}"?`, onConfirm: () => remove(id) })
 *   ...
 *   return (<>{confirmDialog}</>)
 */
export function useConfirm() {
    const [request, setRequest] = useState(null)
    const [busy, setBusy] = useState(false)

    const confirm = useCallback((options) => setRequest(options), [])

    const close = useCallback(() => {
        setRequest(null)
        setBusy(false)
    }, [])

    const handleConfirm = useCallback(async () => {
        if (!request) return
        try {
            setBusy(true)
            await request.onConfirm?.()
        } finally {
            close()
        }
    }, [request, close])

    const confirmDialog = (
        <AlertDialog open={!!request} onOpenChange={(open) => !open && close()}>
            <AlertDialogContent>
                <AlertDialogHeader>
                    <AlertDialogTitle>{request?.title || 'Подтвердите действие'}</AlertDialogTitle>
                    <AlertDialogDescription>{request?.description}</AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                    <AlertDialogCancel disabled={busy}>
                        {request?.cancelText || 'Отмена'}
                    </AlertDialogCancel>
                    <AlertDialogAction
                        variant={request?.variant || 'destructive'}
                        disabled={busy}
                        onClick={(event) => {
                            // Закрываем сами — после того, как действие отработает
                            event.preventDefault()
                            handleConfirm()
                        }}
                    >
                        {request?.confirmText || 'Удалить'}
                    </AlertDialogAction>
                </AlertDialogFooter>
            </AlertDialogContent>
        </AlertDialog>
    )

    return { confirm, confirmDialog }
}
