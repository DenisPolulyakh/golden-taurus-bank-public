import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useState, useEffect } from 'react';
import { Toaster } from '@/components/ui/sonner';
import { FullScreenLoading } from '@/components/ui-app/page-state';
import Login from './components/Login';
import Register from './components/Register';
import Dashboard from './components/dashboard/Dashboard.jsx';
import VerifyEmail from './components/VerifyEmail';
import BullionNamesPage from './components/bullion-names/BullionNamesPage';
import BanksPage from './components/banks/BanksPage';
import VaultsPage from './components/vaults/VaultsPage';
import BullionsPage from './components/bullions/BullionsPage';
import VaultBullionsPage from './components/bullions/VaultBullionsPage';
import CreditCardsPage from './components/credit-cards/CreditCardsPage';
import TransactionHistoryPage from './components/history/TransactionHistoryPage';
import ReportsPage from './components/reports/ReportsPage';
import api, { setAccessToken, clearAccessToken } from './api/axios';
import BankDetailPage from './components/banks/BankDetailPage';
import SettingsPage from './components/settings/SettingsPage';

function App() {
    const [isAuthenticated, setIsAuthenticated] = useState(false);
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        restoreSession();
    }, []);

    const restoreSession = async () => {
        try {
            console.log('Attempting to restore session...');
            // Гость без куки — штатная ситуация: не показываем тост и не уходим в перевыпуск токена
            const response = await api.post('/auth/refresh', null, {
                _skipAuthRefresh: true,
                _skipErrorToast: true,
            });

            console.log('Refresh response:', response.data);

            if (response.data && response.data.code === 0 && response.data.data) {
                const { token, id, email, fullName, role } = response.data.data;

                setAccessToken(token);
                setUser({ id, email, fullName, role });
                setIsAuthenticated(true);
                console.log('Session restored successfully');
            } else {
                throw new Error('Invalid refresh response');
            }
        } catch (err) {
            console.error('Session restore failed:', err);
            clearAccessToken();
            setIsAuthenticated(false);
            setUser(null);
        } finally {
            setLoading(false);
        }
    };

    const handleLogin = (userData, accessToken) => {
        setAccessToken(accessToken);
        setUser(userData);
        setIsAuthenticated(true);
    };

    const handleLogout = async () => {
        try {
            await api.post('/auth/logout');
        } catch (err) {
            console.error('Logout error:', err);
        } finally {
            clearAccessToken();
            setIsAuthenticated(false);
            setUser(null);
        }
    };

    if (loading) {
        return <FullScreenLoading />;
    }

    const ProtectedRoute = ({ children }) => {
        if (!isAuthenticated) {
            return <Navigate to="/login" replace />;
        }
        return children;
    };

    return (
        <BrowserRouter>
            <Toaster
                position="top-right"
                richColors
                closeButton
                expand
                duration={5000}
            />
            <Routes>
                <Route path="/login" element={<Login onLogin={handleLogin} />} />
                <Route path="/register" element={<Register />} />
                <Route path="/verify" element={<VerifyEmail />} />

                <Route
                    path="/dashboard"
                    element={
                        <ProtectedRoute>
                            <Dashboard user={user} onLogout={handleLogout} />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/settings"
                    element={
                        <ProtectedRoute>
                            <SettingsPage />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/bullion-names"
                    element={
                        <ProtectedRoute>
                            <BullionNamesPage />
                        </ProtectedRoute>
                    }
                />
                <Route
                    path="/banks"
                    element={
                        <ProtectedRoute>
                            <BanksPage />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/vaults"
                    element={
                        <ProtectedRoute>
                            <VaultsPage />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/vaults/:vaultId"
                    element={
                        <ProtectedRoute>
                            <VaultBullionsPage />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/bullions"
                    element={
                        <ProtectedRoute>
                            <BullionsPage />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/credit-cards"
                    element={
                        <ProtectedRoute>
                            <CreditCardsPage />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/transactions"
                    element={
                        <ProtectedRoute>
                            <TransactionHistoryPage />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/reports"
                    element={
                        <ProtectedRoute>
                            <ReportsPage />
                        </ProtectedRoute>
                    }
                />

                <Route
                    path="/"
                    element={<Navigate to={isAuthenticated ? "/dashboard" : "/login"} replace />}
                />

                <Route
                    path="/banks/:bankId"
                    element={
                        <ProtectedRoute>
                            <BankDetailPage />
                        </ProtectedRoute>
                    }
                />

                <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
        </BrowserRouter>
    );
}

export default App;