import { Landmark } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

/** Общая рамка экранов входа, регистрации и подтверждения почты. */
export function AuthShell({ title, children, footer }) {
    return (
        <div className="flex min-h-screen items-center justify-center bg-muted/40 p-4">
            <Card className="w-full max-w-md">
                <CardHeader className="text-center">
                    <div className="flex flex-col items-center gap-2">
                        <Landmark className="size-8 text-primary" />
                        <span className="text-lg font-semibold">Твой личный банк</span>
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
