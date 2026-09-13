import { Component } from 'react'
import { TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyMedia, EmptyTitle } from '@/components/ui/empty'

/**
 * Страховка от белого экрана: если страница упала при отрисовке, React
 * размонтирует всё дерево. Ловим падение и показываем, что делать дальше.
 */
export class ErrorBoundary extends Component {
    state = { error: null }

    static getDerivedStateFromError(error) {
        return { error }
    }

    componentDidCatch(error, info) {
        console.error('Страница упала:', error, info.componentStack)
    }

    render() {
        if (!this.state.error) return this.props.children

        return (
            <div className="flex min-h-screen items-center justify-center p-4">
                <Empty className="max-w-md border">
                    <EmptyHeader>
                        <EmptyMedia variant="icon" className="bg-destructive/10 text-destructive">
                            <TriangleAlert />
                        </EmptyMedia>
                        <EmptyTitle>Что-то пошло не так</EmptyTitle>
                        <EmptyDescription>
                            Страница не смогла отобразиться. Обычно помогает перезагрузка.
                        </EmptyDescription>
                    </EmptyHeader>
                    <EmptyContent className="flex-row justify-center">
                        <Button onClick={() => window.location.reload()}>Перезагрузить</Button>
                        <Button variant="outline" onClick={() => window.location.assign('/')}>
                            На главную
                        </Button>
                    </EmptyContent>
                </Empty>
            </div>
        )
    }
}
