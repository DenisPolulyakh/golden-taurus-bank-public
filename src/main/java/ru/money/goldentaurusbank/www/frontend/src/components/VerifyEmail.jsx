import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import api from '@/api/axios'
import { Button } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { AuthShell } from '@/components/ui-app/auth-shell'
import { ErrorMessage } from '@/components/ui-app/page-state'

function VerifyEmail() {
    const [searchParams] = useSearchParams()
    const token = searchParams.get('token')
    const navigate = useNavigate()

    const [status, setStatus] = useState('loading')
    const [message, setMessage] = useState('')
    const hasVerified = useRef(false)

    useEffect(() => {
        if (!token) {
            setStatus('error')
            setMessage('Неверная ссылка подтверждения')
            return
        }

        // Защита от повторного вызова (React StrictMode вызывает эффект дважды)
        if (hasVerified.current) {
            return
        }
        hasVerified.current = true

        const verifyEmail = async () => {
            try {
                const response = await api.get(`/auth/verify?token=${token}`, { _skipErrorToast: true })
                setStatus('success')
                setMessage(response.data.message || 'Email успешно подтверждён!')

                setTimeout(() => {
                    navigate('/login')
                }, 3000)
            } catch (err) {
                setStatus('error')
                setMessage(err.response?.data?.message || 'Ошибка подтверждения email')
            }
        }

        verifyEmail()
    }, [token, navigate])

    return (
        <AuthShell title="Подтверждение email">
            {status === 'loading' && (
                <div className="flex items-center justify-center gap-2 text-muted-foreground">
                    <Spinner />
                    Подтверждение email...
                </div>
            )}

            {status === 'success' && (
                <div
                    role="status"
                    className="rounded-md border border-success/30 bg-success/10 px-3 py-2 text-sm text-success"
                >
                    <p>{message}</p>
                    <p>Перенаправление на страницу входа...</p>
                </div>
            )}

            {status === 'error' && (
                <>
                    <ErrorMessage>{message}</ErrorMessage>
                    <Button onClick={() => navigate('/login')}>Перейти к входу</Button>
                </>
            )}
        </AuthShell>
    )
}

export default VerifyEmail
