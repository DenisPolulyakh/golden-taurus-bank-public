import { Coins, TrendingUp, Vault, Wallet } from 'lucide-react'
import { StatCard, StatGrid } from '@/components/ui-app/stat-card'
import { formatAmount } from '@/lib/format'

const EMPTY_VALUE = '—'

export function SavingsStats({ totalAmount, countBullions, countVaults, averageRate }) {
    return (
        <StatGrid>
            <StatCard
                icon={Wallet}
                label="Общая сумма"
                value={totalAmount == null ? EMPTY_VALUE : `${formatAmount(totalAmount)} ₽`}
                tone="brand"
            />
            <StatCard
                icon={Coins}
                label="Количество слитков"
                value={countBullions ?? EMPTY_VALUE}
                tone="info"
            />
            <StatCard
                icon={Vault}
                label="Количество хранилищ"
                value={countVaults ?? EMPTY_VALUE}
                tone="primary"
            />
            <StatCard
                icon={TrendingUp}
                label="Средняя ставка"
                value={averageRate == null ? EMPTY_VALUE : `${Number(averageRate).toFixed(2)}%`}
                tone="success"
            />
        </StatGrid>
    )
}
