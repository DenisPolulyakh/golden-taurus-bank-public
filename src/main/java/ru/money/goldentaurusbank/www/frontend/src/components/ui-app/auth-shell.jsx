import { Landmark } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

/** Общая рамка экранов входа, регистрации и подтверждения почты. */
export function AuthShell({ title, children, footer }) {
    return (
        <div className="relative flex min-h-screen items-center justify-center overflow-hidden p-4">
            {/* Тот самый фиолетовый градиент со старого экрана входа — но теперь
                он фон страницы, а не заливка самой карточки */}
            <div className="brand-gradient absolute inset-0 -z-10 opacity-90" />
            <div className="absolute inset-0 -z-10 bg-[radial-gradient(60rem_30rem_at_50%_-20%,rgba(255,255,255,0.35),transparent)]" />

            <Card className="w-full max-w-md shadow-xl">
                <CardHeader className="text-center">
                    <div className="flex flex-col items-center gap-2">
                        <span className="brand-gradient flex size-12 items-center justify-center rounded-xl text-white shadow-md">
                            <Landmark className="size-6" />
                        </span>
                        <span className="brand-text text-lg font-semibold">Твой личный банк</span>
                    </div>
                    <CardTitle className="pt-2 font-normal text-muted-foreground">
                        {title}
                    </CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-6">{children}</CardContent>
                {footer && (
                    <CardContent className="text-center text-sm text-muted-foreground">
                        {footer}
                    </CardContent>
                )}
            </Card>
        </div>
    )
}
