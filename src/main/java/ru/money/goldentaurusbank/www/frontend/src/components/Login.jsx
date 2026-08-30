import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '@/api/axios'
import { Button } from '@/components/ui/button'
import { Field, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { AuthShell } from '@/components/ui-app/auth-shell'
import { PasswordInput } from '@/components/ui-app/password-input'
import { ErrorMessage } from '@/components/ui-app/page-state'

function Login({ onLogin }) {
    const [email, setEmail] = useState('')
    const [password, setPassword] = useState('')
    const [error, setError] = useState('')
    const [loading, setLoading] = useState(false)
    const navigate = useNavigate()

    const handleSubmit = async (e) => {
        e.preventDefault()
        setError('')
        setLoading(true)

        try {
            const response = await api.post('/auth/login', { email, password }, { _skipErrorToast: true })

            if (response.data && response.data.code === 0 && response.data.data) {
                const { token, id, email: userEmail, fullName, role } = response.data.data

                // ✅ Только передаём токен в onLogin, НЕ сохраняем в localStorage
                if (onLogin) {
                    onLogin({ id, email: userEmail, fullName, role }, token)
                }

                navigate('/dashboard')
            } else {
                setError('Неожиданный формат ответа от сервера')
            }
        } catch (err) {
            console.error('Login error:', err)
            setError(err.response?.data?.message || 'Ошибка входа')
        } finally {
            setLoading(false)
        }
    }

    return (
        <AuthShell
            title="Вход в личный кабинет"
            footer={
                <>
                    Нет аккаунта?{' '}
                    <Link to="/register" className="text-primary hover:underline">
                        Зарегистрироваться
                    </Link>
                </>
            }
        >
            <ErrorMessage>{error}</ErrorMessage>

            <form onSubmit={handleSubmit} className="flex flex-col gap-6">
                <Field>
                    <FieldLabel htmlFor="login-email">Email</FieldLabel>
                    <Input
                        id="login-email"
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        required
                        placeholder="your@email.com"
                        autoComplete="email"
                    />
                </Field>

                <Field>
                    <FieldLabel htmlFor="login-password">Пароль</FieldLabel>
                    <PasswordInput
                        id="login-password"
                        value={password}
                        onChange={(e) => setPassword(e.target.value)}
                        required
                        placeholder="••••••"
                    />
                </Field>

                <Button type="submit" disabled={loading}>
                    {loading && <Spinner />}
                    {loading ? 'Вход...' : 'Войти'}
                </Button>
            </form>
        </AuthShell>
    )
}

export default Login
