import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { Copy, Link2, Trash2 } from 'lucide-react'
import api from '@/api/axios'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { PageContainer, PageHeader } from '@/components/ui-app/page-header'
import { EmptyState, PageLoading } from '@/components/ui-app/page-state'
import { useConfirm } from '@/components/ui-app/confirm-dialog'

function formatDateTime(value) {
    if (!value) return '—'
    const date = new Date(value)

    return date.toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'short' })
}

function SettingsPage() {
    const [links, setLinks] = useState([])
    const [loading, setLoading] = useState(true)
    const [code, setCode] = useState(null)
    const [secondsLeft, setSecondsLeft] = useState(0)

    const navigate = useNavigate()
    const { confirm, confirmDialog } = useConfirm()

    const reload = useCallback(async () => {
        try {
            const response = await api.get('/telegram/links')
            setLinks(response.data.data || [])
        } catch (err) {
            console.error('Ошибка загрузки привязок:', err)
        } finally {
            setLoading(false)
        }
    }, [])

    useEffect(() => {
        reload()
    }, [reload])

    useEffect(() => {
        if (!code) return undefined

        const tick = () => {
            const left = Math.max(0, Math.floor((new Date(code.expiresAt) - new Date()) / 1000))
            setSecondsLeft(left)
            if (left === 0) {
                setCode(null)
            }
        }

        tick()
        const timer = setInterval(tick, 1000)

        return () => clearInterval(timer)
    }, [code])

    useEffect(() => {
        if (!code) return undefined

        const timer = setInterval(reload, 5000)

        return () => clearInterval(timer)
    }, [code, reload])

    const requestCode = async () => {
        try {
            const response = await api.post('/telegram/link-code')
            setCode(response.data.data)
        } catch (err) {
            console.error('Ошибка получения кода:', err)
        }
    }

    const copyCommand = async () => {
        try {
            await navigator.clipboard.writeText(`/link ${code.code}`)
            toast.success('Команда скопирована')
        } catch (err) {
            toast.error('Браузер не дал доступ к буферу обмена')
        }
    }

    const removeLink = (link) => {
        confirm({
            title: 'Отвязать чат',
            description: `Чат ${link.chatId} перестанет получать уведомления.`,
            onConfirm: async () => {
                await api.delete(`/telegram/links/${link.id}`)
                toast.success('Чат отвязан')
                await reload()
            },
        })
    }

    if (loading) {
        return (
            <PageContainer>
                <PageLoading />
            </PageContainer>
        )
    }

    return (
        <PageContainer>
            <PageHeader title="Настройки" onBack={() => navigate('/dashboard')} />

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Телеграм</CardTitle>
                </CardHeader>
                <CardContent className="flex flex-col gap-4">
                    <p className="text-sm text-muted-foreground">
                        Бот присылает уведомления по картам и аварийный пакет. Реквизиты он не знает и
                        прислать не может — ключ есть только в вашем браузере.
                    </p>

                    {code ? (
                        <div className="flex flex-col gap-3 rounded-md border p-4">
                            <div className="text-sm text-muted-foreground">
                                Отправьте эту команду боту. Код действует ещё {secondsLeft} секунд
                            </div>
                            <div className="font-mono text-xl tracking-wider">/link {code.code}</div>
                            <div className="flex gap-2">
                                <Button variant="outline" size="sm" onClick={copyCommand}>
                                    <Copy />
                                    Скопировать
                                </Button>
                                <Button variant="ghost" size="sm" onClick={() => setCode(null)}>
                                    Скрыть
                                </Button>
                            </div>
                        </div>
                    ) : (
                        <div>
                            <Button onClick={requestCode}>
                                <Link2 />
                                Подключить телеграм
                            </Button>
                        </div>
                    )}

                    {links.length === 0 ? (
                        <EmptyState
                            title="Чатов пока нет"
                            description="Получите код и отправьте его боту, чтобы привязать чат"
                        />
                    ) : (
                        <div className="flex flex-col gap-2">
                            {links.map((link) => (
                                <div
                                    key={link.id}
                                    className="flex items-center justify-between gap-3 rounded-md border px-3 py-2"
                                >
                                    <div className="min-w-0">
                                        <div className="font-medium tabular-nums">Чат {link.chatId}</div>
                                        <div className="text-xs text-muted-foreground">
                                            привязан {formatDateTime(link.linkedAt)}
                                        </div>
                                    </div>
                                    <Button
                                        variant="ghost"
                                        size="icon"
                                        className="text-destructive hover:text-destructive"
                                        onClick={() => removeLink(link)}
                                        aria-label="Отвязать"
                                    >
                                        <Trash2 />
                                    </Button>
                                </div>
                            ))}
                        </div>
                    )}
                </CardContent>
            </Card>

            {confirmDialog}
        </PageContainer>
    )
}

export default SettingsPage
