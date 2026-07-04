import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api/axios';
import './Login.css';

function Register({ onRegister }) {
    const [formData, setFormData] = useState({
        email: '',
        password: '',
        fullName: ''
    });
    const [showPassword, setShowPassword] = useState(false);
    const [error, setError] = useState('');
    const [success, setSuccess] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleChange = (e) => {
        setFormData({
            ...formData,
            [e.target.name]: e.target.value
        });
    };

    // Валидация пароля
    const validatePassword = (password) => {
        const hasDigit = /\d/.test(password);
        const hasSpecialChar = /[%$*]/.test(password);

        if (!hasDigit) {
            return 'Пароль должен содержать хотя бы одну цифру';
        }
        if (!hasSpecialChar) {
            return 'Пароль должен содержать хотя бы один спецсимвол (% $ *)';
        }
        if (password.length < 6) {
            return 'Пароль должен содержать минимум 6 символов';
        }
        return null;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        // Валидация пароля перед отправкой
        const passwordError = validatePassword(formData.password);
        if (passwordError) {
            setError(passwordError);
            return;
        }

        setError('');
        setSuccess('');
        setLoading(true);

        try {
            await api.post('/auth/register', formData);
            setSuccess('Регистрация успешна! Проверьте почту для подтверждения.');
            setTimeout(() => {
                navigate('/login');
            }, 2000);
        } catch (err) {
            setError(err.response?.data?.message || 'Ошибка регистрации');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="login-container">
            <div className="login-card">
                <h1>Твой личный банк</h1>
                <h2>Регистрация</h2>

                {error && <div className="error-message">{error}</div>}
                {success && <div className="success-message">{success}</div>}

                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label>Email</label>
                        <input
                            type="email"
                            name="email"
                            value={formData.email}
                            onChange={handleChange}
                            required
                            placeholder="your@email.com"
                        />
                    </div>

                    <div className="form-group password-group">
                        <label>Пароль</label>
                        <div className="password-input-wrapper">
                            <input
                                type={showPassword ? 'text' : 'password'}
                                name="password"
                                value={formData.password}
                                onChange={handleChange}
                                required
                                minLength={6}
                                placeholder="минимум 6 символов"
                            />
                            <button
                                type="button"
                                className="eye-button"
                                onClick={() => setShowPassword(!showPassword)}
                            >
                                {showPassword ? '👁️' : '👁️‍🗨️'}
                            </button>
                        </div>
                        <small className="password-hint">
                            Пароль должен содержать цифру и спецсимвол (% $ *)
                        </small>
                    </div>

                    <div className="form-group">
                        <label>Полное имя</label>
                        <input
                            type="text"
                            name="fullName"
                            value={formData.fullName}
                            onChange={handleChange}
                            required
                            placeholder="Иван Иванов"
                        />
                    </div>

                    <button type="submit" disabled={loading}>
                        {loading ? 'Регистрация...' : 'Зарегистрироваться'}
                    </button>
                </form>

                <div className="register-link">
                    Уже есть аккаунт? <Link to="/login">Войти</Link>
                </div>
            </div>
        </div>
    );
}

export default Register;