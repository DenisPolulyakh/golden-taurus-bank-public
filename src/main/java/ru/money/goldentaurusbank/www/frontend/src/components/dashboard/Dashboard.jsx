import { useEffect, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Coins, CreditCard, FolderTree, History, Landmark, Vault } from 'lucide-react'
import api, { clearAccessToken } from '@/api/axios'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { AppHeader } from '@/components/ui-app/app-header'
import { PageContainer } from '@/components/ui-app/page-header'
import { PageLoading } from '@/components/ui-app/page-state'
import { SavingsStats } from '@/components/ui-app/savings-stats'
import { formatAmount } from '@/lib/format'
import SavingsChart from './SavingsChart.jsx'
import PieChartComponent from './PieChartComponent.jsx'

const QUICK_ACTIONS = [
    { to: '/bullions', label: 'Добавить слиток', icon: Coins },
    { to: '/vaults', label: 'Создать хранилище', icon: Vault },
    { to: '/bullion-names', label: 'Управлять наименованиями', icon: FolderTree },
    { to: '/banks', label: 'Управлять банками', icon: Landmark },
    { to: '/credit-cards', label: 'Кредитные карты', icon: CreditCard },
    { to: '/transactions', label: 'История операций', icon: History },
]

const formatDateTime = (date) => {
    const dateStr = date.toLocaleDateString('ru-RU', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric'
    })
    const timeStr = date.toLocaleTimeString('ru-RU', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    })
    return `${dateStr}, ${timeStr}`
}

/**
 * Часы живут отдельным компонентом. Пока они сидели в состоянии дашборда, тик
 * раз в секунду перерисовывал заодно и графики, а recharts на каждой перерисовке
 * заново запускает вступительную анимацию — её id завязан на объект пропсов.
 * Анимация не доигрывала до конца, и подписи на диаграмме не появлялись вовсе.
 */
function CurrentDateTime() {
    const [now, setNow] = useState(new Date())

    useEffect(() => {
        const timerId = setInterval(() => setNow(new Date()), 1000)
        return () => clearInterval(timerId)
    }, [])

    return (
        <p className="text-sm text-muted-foreground first-letter:uppercase">
            {formatDateTime(now)}
        </p>
    )
}

function Dashboard({ user, onLogout }) {
    const [userData, setUserData] = useState(user)
    const [loading, setLoading] = useState(true)
    const [totalAmount, setTotalAmount] = useState(0)
    const [countVaults, setCountVaults] = useState(0)
    const [averageRate, setAverageRate] = useState(0)
    const [countBullions, setCountBullions] = useState(0)
    const [bullionDistribution, setBullionDistribution] = useState([])
    const [refreshKey] = useState(0)
    const navigate = useNavigate()

    useEffect(() => {
        fetchUserData()
        fetchDashboardData()
    }, [refreshKey])

    const fetchUserData = async () => {
        try {
            const response = await api.get('/users/me')
            setUserData(response.data.data)
        } catch (err) {
            console.error('Ошибка загрузки данных:', err)
        }
    }

    const fetchDashboardData = async () => {
        try {
            const response = await api.get('/bullions/grouped')
            const data = response.data.data

            setTotalAmount(data?.totalAmount || 0)
            setCountVaults(data?.countVaults || 0)
            setCountBullions(data?.countBullions || 0)
            setAverageRate(data?.averageRate || 0.0)

            if (data?.bullionNameBullionList && data.bullionNameBullionList.length > 0) {
                const distribution = data.bullionNameBullionList
                    .filter(bullionName => bullionName.bullionNameAmount > 0)
                    .map((bullionName) => ({
                        name: bullionName.bullionNameTitle,
                        amount: bullionName.bullionNameAmount,
                        bullionNameId: bullionName.bullionNameId,
                        color: bullionName.bullionNameColor,
                        bullionCount: bullionName.vaults?.length || 0
                    }))
                setBullionDistribution(distribution)
            } else {
                setBullionDistribution([])
            }
        } catch (err) {
            console.error('Ошибка загрузки данных дашборда:', err)
        } finally {
            setLoading(false)
        }
    }

    const handleLogout = async () => {
        try {
            await api.post('/auth/logout')
        } catch (err) {
            console.error('Logout error:', err)
        } finally {
            clearAccessToken()
            if (onLogout) onLogout()
            navigate('/login')
        }
    }

    const hasChartData = bullionDistribution.length > 0 &&
        bullionDistribution.some(item => item.amount > 0)

    return (
        <div className="min-h-screen">
            <AppHeader onLogout={handleLogout} />

            {loading ? (
                <PageContainer>
                    <PageLoading />
                </PageContainer>
            ) : (
                <PageContainer>
                    <div>
                        <h1 className="text-2xl font-semibold tracking-tight">
                            Добро пожаловать, {userData?.fullName}!
                        </h1>
                        <p className="text-sm text-muted-foreground">{userData?.email}</p>
                        <CurrentDateTime />
                    </div>

                    <SavingsStats
                        totalAmount={totalAmount}
                        countBullions={countBullions}
                        countVaults={countVaults}
                        averageRate={averageRate}
                    />

                    <Card>
                        <CardContent>
                            <SavingsChart refreshKey={refreshKey} />
                        </CardContent>
                    </Card>

                    {hasChartData && (
                        <Card>
                            <CardHeader>
                                <CardTitle>Распределение слитков по наименованиям</CardTitle>
                            </CardHeader>
                            <CardContent>
                                <PieChartComponent
                                    data={bullionDistribution}
                                    formatAmount={formatAmount}
                                />
                            </CardContent>
                        </Card>
                    )}

                    <Card>
                        <CardHeader>
                            <CardTitle>Быстрые действия</CardTitle>
                        </CardHeader>
                        <CardContent className="flex flex-wrap gap-2">
                            {QUICK_ACTIONS.map(({ to, label, icon: Icon }) => (
                                <Button key={label} variant="outline" asChild>
                                    <Link to={to}>
                                        <Icon />
                                        {label}
                                    </Link>
                                </Button>
                            ))}
                        </CardContent>
                    </Card>
                </PageContainer>
            )}
        </div>
    )
}

export default Dashboard
