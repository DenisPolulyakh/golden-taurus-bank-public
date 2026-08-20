import { useEffect, useRef, useState, useCallback } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api, { clearAccessToken } from '../../api/axios';
import SavingsChart from './SavingsChart.jsx';
import PieChartComponent from './PieChartComponent.jsx';
import './Dashboard.css';

function Dashboard({ user, onLogout }) {
    const [userData, setUserData] = useState(user);
    const [loading, setLoading] = useState(true);
    const [totalAmount, setTotalAmount] = useState(0);
    const [countVaults, setCountVaults] = useState(0);
    const [averageRate, setAverageRate] = useState(0);
    const [countBullions, setCountBullions] = useState(0);
    const [bullionDistribution, setBullionDistribution] = useState([]);
    const [refreshKey, setRefreshKey] = useState(0);
    const [currentDateTime, setCurrentDateTime] = useState(new Date());
    // Справочники убраны под шестерёнку: в шапке им место только раз в месяц
    const [settingsOpen, setSettingsOpen] = useState(false);
    const settingsRef = useRef(null);
    const navigate = useNavigate();

    useEffect(() => {
        fetchUserData();
        fetchDashboardData();
    }, [refreshKey]);

    // Меню закрывается щелчком мимо и по Escape: иначе оно висит поверх
    // страницы, пока не ткнёшь в саму шестерёнку
    useEffect(() => {
        if (!settingsOpen) return;

        const onPointerDown = (event) => {
            if (!settingsRef.current?.contains(event.target)) {
                setSettingsOpen(false);
            }
        };
        const onKeyDown = (event) => {
            if (event.key === 'Escape') setSettingsOpen(false);
        };

        document.addEventListener('mousedown', onPointerDown);
        document.addEventListener('keydown', onKeyDown);
        return () => {
            document.removeEventListener('mousedown', onPointerDown);
            document.removeEventListener('keydown', onKeyDown);
        };
    }, [settingsOpen]);

    // Обновляем текущие дату и время каждую секунду
    useEffect(() => {
        const timerId = setInterval(() => {
            setCurrentDateTime(new Date());
        }, 1000);
        return () => clearInterval(timerId);
    }, []);

    const fetchUserData = async () => {
        try {
            const response = await api.get('/users/me');
            setUserData(response.data.data);
        } catch (err) {
            console.error('Ошибка загрузки данных:', err);
            if (err.response?.status === 401) {
                handleLogout();
            }
        }
    };

    const fetchDashboardData = async () => {
        try {
            const response = await api.get('/bullions/grouped');
            const data = response.data.data;

            setTotalAmount(data?.totalAmount || 0);
            setCountVaults(data?.countVaults || 0);
            setCountBullions(data?.countBullions || 0);
            setAverageRate(data?.averageRate || 0.0);

            if (data?.bullionNameBullionList && data.bullionNameBullionList.length > 0) {
                const distribution = data.bullionNameBullionList
                    .filter(bullionName => bullionName.bullionNameAmount > 0)
                    .map((bullionName) => ({
                        name: bullionName.bullionNameTitle,
                        amount: bullionName.bullionNameAmount,
                        bullionNameId: bullionName.bullionNameId,
                        color: bullionName.bullionNameColor,
                        bullionCount: bullionName.vaults?.reduce((sum, vault) => sum + 1, 0) || 0
                    }));
                setBullionDistribution(distribution);
            } else {
                setBullionDistribution([]);
            }
        } catch (err) {
            console.error('Ошибка загрузки данных дашборда:', err);
        } finally {
            setLoading(false);
        }
    };

    const refreshDashboard = useCallback(() => {
        setRefreshKey(prev => prev + 1);
    }, []);

    const handleLogout = async () => {
        try {
            await api.post('/auth/logout');
        } catch (err) {
            console.error('Logout error:', err);
        } finally {
            clearAccessToken();
            if (onLogout) onLogout();
            navigate('/login');
        }
    };

    const formatAmount = (amount) => {
        if (!amount && amount !== 0) return '0';
        const num = typeof amount === 'string' ? parseFloat(amount) : amount;
        if (isNaN(num)) return '0';
        const parts = num.toFixed(2).split('.');
        const integerPart = parts[0];
        const decimalPart = parts[1];
        const formattedInteger = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
        if (decimalPart && decimalPart !== '00') {
            const trimmedDecimal = decimalPart.replace(/0+$/, '');
            if (trimmedDecimal) {
                return `${formattedInteger}.${trimmedDecimal}`;
            }
        }
        return formattedInteger;
    };

    const formatDateTime = (date) => {
        const dateStr = date.toLocaleDateString('ru-RU', {
            weekday: 'long',
            day: 'numeric',
            month: 'long',
            year: 'numeric'
        });
        const timeStr = date.toLocaleTimeString('ru-RU', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        });
        return `${dateStr}, ${timeStr}`;
    };

    if (loading) {
        return <div className="dashboard-container">Загрузка...</div>;
    }

    const hasChartData = bullionDistribution.length > 0 &&
        bullionDistribution.some(item => item.amount > 0);

    return (
        <div className="dashboard-container">
            <div className="dashboard-header">
                <h1>🏦 Твой личный банк</h1>
                <div className="header-buttons">
                    <Link to="/bullions" className="dashboard-link">
                        💰 Мои слитки
                    </Link>
                    <Link to="/vaults" className="dashboard-link">
                        🏦 Хранилища
                    </Link>
                    <Link to="/credit-cards" className="dashboard-link">
                        💳 Кредитные карты
                    </Link>
                    <Link to="/transactions" className="dashboard-link">
                        📜 История
                    </Link>
                    <div className="settings-dropdown" ref={settingsRef}>
                        <button
                            type="button"
                            className={`settings-btn ${settingsOpen ? 'open' : ''}`}
                            onClick={() => setSettingsOpen(open => !open)}
                            aria-expanded={settingsOpen}
                            aria-haspopup="menu"
                            title="Справочники"
                        >
                            ⚙️
                        </button>
                        {settingsOpen && (
                            <div className="settings-menu" role="menu">
                                <Link
                                    to="/bullion-names"
                                    className="dashboard-link"
                                    role="menuitem"
                                    onClick={() => setSettingsOpen(false)}
                                >
                                    📁 Наименования
                                </Link>
                                <Link
                                    to="/banks"
                                    className="dashboard-link"
                                    role="menuitem"
                                    onClick={() => setSettingsOpen(false)}
                                >
                                    🏛️ Банки
                                </Link>
                            </div>
                        )}
                    </div>
                    <button onClick={handleLogout} className="logout-btn">
                        Выйти
                    </button>
                </div>
            </div>

            <div className="dashboard-content">
                <div className="user-info">
                    <h2>Добро пожаловать, {userData?.fullName}! 👋</h2>
                    <p>📧 Email: {userData?.email}</p>
                    <p className="current-datetime">🕐 {formatDateTime(currentDateTime)}</p>
                </div>

                <div className="stats-cards">
                    <div className="stat-card total-card">
                        <div className="stat-label">💰 Общая сумма всех слитков</div>
                        <div className="stat-value no-large">{formatAmount(totalAmount)} ₽</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-label">📊 Количество слитков</div>
                        <div className="stat-value">{countBullions}</div>
                    </div>
                    <div className="stat-card">
                        <div className="stat-label">🏦 Количество хранилищ</div>
                        <div className="stat-value">{countVaults}</div>
                    </div>
                    <div className="stat-card">
                        <span className="stat-name">📈 Средняя ставка:</span>
                        <div className="stat-value">{averageRate.toFixed(2)}%</div>
                    </div>
                </div>

                <div className="chart-section">
                    <SavingsChart refreshKey={refreshKey}/>
                </div>

                {hasChartData && (
                    <div className="chart-section">
                        <h3>📊 Распределение слитков по наименованиям</h3>
                        <div className="chart-container pie-chart-container">
                            <PieChartComponent
                                data={bullionDistribution}
                                formatAmount={formatAmount}
                            />
                        </div>
                    </div>
                )}

                <div className="quick-actions">
                    <h3>⚡ Быстрые действия</h3>
                    <div className="action-buttons">
                        <Link to="/bullions" className="action-btn">
                            💰 Добавить слиток
                        </Link>
                        <Link to="/vaults" className="action-btn">
                            🏦 Создать хранилище
                        </Link>
                        <Link to="/bullion-names" className="action-btn">
                            📁 Управлять наименованиями
                        </Link>
                        <Link to="/banks" className="action-btn">
                            🏛️ Управлять банками
                        </Link>
                        <Link to="/credit-cards" className="action-btn">
                            💳 Кредитные карты
                        </Link>
                        <Link to="/transactions" className="action-btn">
                            📜 История операций
                        </Link>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default Dashboard;