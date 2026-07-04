import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../api/axios';
import './Login.css';

function VerifyEmail() {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');
    const navigate = useNavigate();

    const [status, setStatus] = useState('loading');
    const [message, setMessage] = useState('');

    useEffect(() => {
        if (!token) {
            setStatus('error');
            setMessage('Неверная ссылка подтверждения');
            return;
        }

        const verifyEmail = async () => {
            try {
                const response = await api.get(`/auth/verify?token=${token}`);
                setStatus('success');
                setMessage(response.data.message || 'Email успешно подтверждён!');

                setTimeout(() => {
                    navigate('/login');
                }, 3000);
            } catch (err) {
                setStatus('error');
                setMessage(err.response?.data?.message || 'Ошибка подтверждения email');
            }
        };

        verifyEmail();
    }, [token, navigate]);

    return (
        <div className="login-container">
            <div className="login-card">
                <h1>Твой личный банк</h1>
                <h2>Подтверждение email</h2>

                {status === 'loading' && (
                    <div className="loading-message">
                        <p>Подтверждение email...</p>
                    </div>
                )}

                {status === 'success' && (
                    <div className="success-message">
                        <p>{message}</p>
                        <p>Перенаправление на страницу входа...</p>
                    </div>
                )}

                {status === 'error' && (
                    <div className="error-message">
                        <p>{message}</p>
                        <button onClick={() => navigate('/login')}>
                            Перейти к входу
                        </button>
                    </div>
                )}
            </div>
        </div>
    );
}

export default VerifyEmail;