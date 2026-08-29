import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '@/api/axios'
import { Button } from '@/components/ui/button'
import { Field, FieldDescription, FieldLabel } from '@/components/ui/field'
import { Input } from '@/components/ui/input'
import { Spinner } from '@/components/ui/spinner'
import { AuthShell } from '@/components/ui-app/auth-shell'
import { PasswordInput } from '@/components/ui-app/password-input'
import { ErrorMessage } from '@/components/ui-app/page-state'

function Register() {
    const [formData, setFormData] = useState({
        email: '',
        password: '',
        fullName: '',
    })
    const [error, setError] = useState('')
    const [success, setSuccess] = useState('')
    const [loading, setLoading] = useState(false)
    const navigate = useNavigate()

    const handleChange = (e) => {
        setFormData({
            ...formData,
            [e.target.name]: e.target.value,
        })
    }

    // Валидация пароля
    const validatePassword = (password) => {
        const hasDigit = /\d/.test(password)
        const hasSpecialChar = /[%$*]/.test(password)

        if (!hasDigit) {
            return 'Пароль должен содержать хотя бы одну цифру'
        }
        if (!hasSpecialChar) {
            return 'Пароль должен содержать хотя бы один спецсимвол (% $ *)'
        }
        if (password.length < 6) {
            return 'Пароль должен содержать минимум 6 символов'
        }
        return null
    }

    const handleSubmit = async (e) => {
        e.preventDefault()

        // Валидация пароля перед отправкой
        const passwordError = validatePassword(formData.password)
        if (passwordError) {
            setError(passwordError)
            return
        }

        setError('')
        setSuccess('')
        setLoading(true)

        try {
            await api.post('/auth/register', formData, { _skipErrorToast: true })
            setSuccess('Регистрация успешна! Проверьте почту для подтверждения.')
            setTimeout(() => {
                navigate('/login')
            }, 2000)
        } catch (err) {
            setError(err.response?.data?.message || 'Ошибка регистрации')
        } finally {
            setLoading(false)
        }
    }

    return (
        <AuthShell
            title="Регистрация"
            footer={
                <>
                    Уже есть аккаунт?{' '}
                    <Link to="/login" className="text-primary hover:underline">
                        Войти
                    </Link>
                </>
            }
        >
            <ErrorMessage>{error}</ErrorMessage>
            {success && (
                <div
                    role="status"
                    className="rounded-md border border-success/30 bg-success/10 px-3 py-2 text-sm text-success"
                >
                    {success}
                </div>
            )}

            <form onSubmit={handleSubmit} className="flex flex-col gap-6">
                <Field>
                    <FieldLabel htmlFor="register-email">Email</FieldLabel>
                    <Input
                        id="register-email"
                        type="email"
                        name="email"
                        value={formData.email}
                        onChange={handleChange}
                        required
                        placeholder="your@email.com"
                        autoComplete="email"
                    />
                </Field>

                <Field>
                    <FieldLabel htmlFor="register-password">Пароль</FieldLabel>
                    <PasswordInput
                        id="register-password"
                        name="password"
                        value={formData.password}
                        onChange={handleChange}
                        required
                        minLength={6}
                        placeholder="минимум 6 символов"
                    />
                    <FieldDescription>
                        Пароль должен содержать цифру и спецсимвол (% $ *)
                    </FieldDescription>
                </Field>

                <Field>
                    <FieldLabel htmlFor="register-full-name">Полное имя</FieldLabel>
                    <Input
                        id="register-full-name"
                        type="text"
                        name="fullName"
                        value={formData.fullName}
                        onChange={handleChange}
                        required
                        placeholder="Иван Иванов"
                    />
                </Field>

                <Button type="submit" disabled={loading}>
                    {loading && <Spinner />}
                    {loading ? 'Регистрация...' : 'Зарегистрироваться'}
                </Button>
            </form>
        </AuthShell>
    )
}

export default Register
