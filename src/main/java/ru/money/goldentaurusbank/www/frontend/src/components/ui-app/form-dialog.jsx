import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { cn } from '@/lib/utils'

/**
 * Модалка с формой: заголовок, скроллящееся тело и футер «Отмена/Сохранить».
 * Заменяет восемь копий `div.modal-overlay > div.modal-content`.
 *
 * onSubmit получает событие и сам решает, закрывать ли окно, — как и раньше:
 * страницы закрывают модалку только после успешного ответа сервера.
 */
export function FormDialog({
    open,
    onOpenChange,
    title,
    description,
    onSubmit,
    saving = false,
    submitText = 'Сохранить',
    savingText = 'Сохранение...',
    cancelText = 'Отмена',
    submitDisabled = false,
    footer,
    className,
    children,
}) {
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className={cn('sm:max-w-lg', className)}>
                <DialogHeader>
                    <DialogTitle>{title}</DialogTitle>
                    {description ? (
                        <DialogDescription>{description}</DialogDescription>
                    ) : (
                        <DialogDescription className="sr-only">{title}</DialogDescription>
                    )}
                </DialogHeader>

                <form onSubmit={onSubmit} className="flex min-h-0 flex-col gap-6">
                    <div className="flex max-h-[60vh] flex-col gap-6 overflow-y-auto px-1">
                        {children}
                    </div>

                    <DialogFooter>
                        {footer}
                        <Button
                            type="button"
                            variant="outline"
                            onClick={() => onOpenChange(false)}
                            disabled={saving}
                        >
                            {cancelText}
                        </Button>
                        <Button type="submit" disabled={saving || submitDisabled}>
                            {saving && <Spinner />}
                            {saving ? savingText : submitText}
                        </Button>
                    </DialogFooter>
                </form>
            </DialogContent>
        </Dialog>
    )
}

/**
 * Модалка без формы — история операций и прочее «только посмотреть».
 */
export function InfoDialog({ open, onOpenChange, title, description, className, children }) {
    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className={cn('sm:max-w-2xl', className)}>
                <DialogHeader>
                    <DialogTitle>{title}</DialogTitle>
                    {description ? (
                        <DialogDescription>{description}</DialogDescription>
                    ) : (
                        <DialogDescription className="sr-only">{title}</DialogDescription>
                    )}
                </DialogHeader>
                <div className="max-h-[65vh] overflow-y-auto">{children}</div>
            </DialogContent>
        </Dialog>
    )
}
