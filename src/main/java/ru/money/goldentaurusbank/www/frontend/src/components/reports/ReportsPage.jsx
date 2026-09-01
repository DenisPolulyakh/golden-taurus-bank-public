import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { CalendarRange, Table2 } from 'lucide-react'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { PageContainer, PageHeader } from '@/components/ui-app/page-header'
import BudgetMonthTab from './BudgetMonthTab'
import BudgetYearTab from './BudgetYearTab'

/**
 * Экран отчётов. Пока их два, оба про бюджет, но каркас рассчитан на то, что
 * отчётов станет больше: список лежит массивом, а не разметкой, — новый отчёт
 * добавляется строкой.
 */
const REPORTS = [
    { value: 'budget-month', label: 'Бюджет на месяц', icon: Table2, Component: BudgetMonthTab },
    { value: 'budget-year', label: 'Бюджет за год', icon: CalendarRange, Component: BudgetYearTab },
]

const ReportsPage = () => {
    const navigate = useNavigate()
    const [tab, setTab] = useState(REPORTS[0].value)

    return (
        <PageContainer>
            <PageHeader
                title="Отчёты"
                description="Сколько потрачено из месячного бюджета в каждый день"
                onBack={() => navigate('/dashboard')}
            />

            <Tabs value={tab} onValueChange={setTab}>
                <TabsList>
                    {REPORTS.map(({ value, label, icon: Icon }) => (
                        <TabsTrigger key={value} value={value}>
                            <Icon className="size-4" />
                            {label}
                        </TabsTrigger>
                    ))}
                </TabsList>

                {REPORTS.map(({ value, Component }) => (
                    <TabsContent key={value} value={value} className="mt-6">
                        {/* Вкладка монтируется только когда открыта: иначе обе
                            ходят за данными на каждый пересчёт периода */}
                        {tab === value && <Component />}
                    </TabsContent>
                ))}
            </Tabs>
        </PageContainer>
    )
}

export default ReportsPage
