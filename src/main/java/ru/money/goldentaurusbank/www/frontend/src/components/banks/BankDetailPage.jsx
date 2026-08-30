// components/banks/BankDetailPage.jsx
import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Vault, Wallet } from 'lucide-react'
import api from '@/api/axios'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardAction, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select'
import { PageContainer, PageHeader } from '@/components/ui-app/page-header'
import { SearchInput } from '@/components/ui-app/search-input'
import { SortButton, TablePager } from '@/components/ui-app/data-table'
import { EmptyState, ErrorMessage, PageLoading } from '@/components/ui-app/page-state'
import { StatCard, StatGrid } from '@/components/ui-app/stat-card'
import { formatAmount, formatShortDate } from '@/lib/format'

const PAGE_SIZES = [5, 10, 20, 50]

function BankDetailPage() {
    const { bankId } = useParams()
    const navigate = useNavigate()

    const [bankData, setBankData] = useState(null)
    const [vaults, setVaults] = useState([])
    const [searchTerm, setSearchTerm] = useState('')
    const [sortField, setSortField] = useState('name')
    const [sortOrder, setSortOrder] = useState('asc')
    const [currentPage, setCurrentPage] = useState(1)
    const [pageSize, setPageSize] = useState(10)
    const [totalPages, setTotalPages] = useState(0)
    const [totalElements, setTotalElements] = useState(0)
    const [totalAmount, setTotalAmount] = useState(0)
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')

    useEffect(() => {
        fetchBankData()
    }, [bankId, searchTerm, sortField, sortOrder, currentPage, pageSize])

    const fetchBankData = async () => {
        setLoading(true)
        try {
            const response = await api.get(`/banks/${bankId}`, {
                params: {
                    search: searchTerm || undefined,
                    sortBy: sortField,
                    sortOrder: sortOrder,
                    page: currentPage,
                    size: pageSize,
                },
            })

            const data = response.data.data
            setBankData(data)
            setVaults(data.vaults?.content || [])
            setTotalAmount(data.totalAmount || 0)
            setTotalPages(data.vaults?.totalPages || 0)
            setTotalElements(data.vaults?.totalElements || 0)
        } catch (err) {
            console.error('Ошибка загрузки данных банка:', err)
            setError('Не удалось загрузить данные банка')
        } finally {
            setLoading(false)
        }
    }

    const handleVaultClick = (vaultId) => {
        navigate(`/vaults/${vaultId}`, { state: { from: 'banks' } })
    }

    const handleSort = (field) => {
        if (sortField === field) {
            setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')
        } else {
            setSortField(field)
            setSortOrder('asc')
        }
        setCurrentPage(1)
    }

    if (loading) {
        return (
            <PageContainer>
                <PageLoading />
            </PageContainer>
        )
    }

    if (error || !bankData) {
        return (
            <PageContainer>
                <ErrorMessage>{error || 'Банк не найден'}</ErrorMessage>
                <div>
                    <Button variant="outline" onClick={() => navigate('/vaults')}>
                        Назад к хранилищам
                    </Button>
                </div>
            </PageContainer>
        )
    }

    return (
        <PageContainer>
            <PageHeader title={bankData.bankName} onBack={() => navigate('/vaults')} />

            <StatGrid className="lg:grid-cols-2">
                <StatCard
                    icon={Wallet}
                    label="Общая сумма в банке"
                    value={`${formatAmount(totalAmount)} ₽`}
                    tone="brand"
                />
                <StatCard
                    icon={Vault}
                    label="Количество хранилищ"
                    value={totalElements}
                    tone="primary"
                />
            </StatGrid>

            <div className="flex flex-wrap items-center justify-between gap-3">
                <SearchInput
                    value={searchTerm}
                    onChange={setSearchTerm}
                    placeholder="Поиск хранилищ в банке..."
                />
                <div className="flex flex-wrap items-center gap-2">
                    <SortButton
                        label="По названию"
                        field="name"
                        sortField={sortField}
                        sortOrder={sortOrder}
                        onSort={handleSort}
                    />
                    <SortButton
                        label="По сумме"
                        field="totalAmount"
                        sortField={sortField}
                        sortOrder={sortOrder}
                        onSort={handleSort}
                    />
                    <SortButton
                        label="По ставке"
                        field="interestRate"
                        sortField={sortField}
                        sortOrder={sortOrder}
                        onSort={handleSort}
                    />
                </div>
            </div>

            {vaults.length === 0 ? (
                <EmptyState
                    icon={Vault}
                    title={searchTerm ? 'Ничего не найдено' : 'Нет хранилищ в этом банке'}
                    description={searchTerm ? 'Попробуйте изменить запрос' : undefined}
                />
            ) : (
                <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                    {vaults.map((vault) => (
                        <Card key={vault.id} className="gap-3 py-4">
                            <CardHeader className="px-4">
                                <CardTitle>
                                    <button
                                        type="button"
                                        onClick={() => handleVaultClick(vault.id)}
                                        className="text-left text-primary hover:underline"
                                    >
                                        {vault.name}
                                    </button>
                                </CardTitle>
                                <CardAction>
                                    <Badge
                                        variant={
                                            vault.accountType === 'SAVINGS' ? 'success' : 'info'
                                        }
                                    >
                                        {vault.accountType === 'SAVINGS'
                                            ? 'Накопительный'
                                            : 'Срочный'}
                                    </Badge>
                                </CardAction>
                            </CardHeader>
                            <CardContent className="flex flex-col gap-1.5 px-4 text-sm">
                                <div className="flex justify-between gap-2">
                                    <span className="text-muted-foreground">Сумма</span>
                                    <span className="font-medium tabular-nums">
                                        {formatAmount(vault.totalAmount)} ₽
                                    </span>
                                </div>
                                <div className="flex justify-between gap-2">
                                    <span className="text-muted-foreground">Ставка</span>
                                    <span className="font-medium tabular-nums">
                                        {vault.interestRate}%
                                    </span>
                                </div>
                                {vault.closeDate && vault.accountType === 'TERM' && (
                                    <div className="flex justify-between gap-2">
                                        <span className="text-muted-foreground">Закрытие</span>
                                        <span className="font-medium">
                                            {formatShortDate(vault.closeDate)}
                                        </span>
                                    </div>
                                )}
                                {vault.description && (
                                    <p className="pt-1 text-muted-foreground">
                                        {vault.description}
                                    </p>
                                )}
                            </CardContent>
                        </Card>
                    ))}
                </div>
            )}

            <TablePager
                page={currentPage}
                totalPages={totalPages}
                onPageChange={setCurrentPage}
                total={totalElements}
                totalLabel="Всего хранилищ:"
            />

            <div className="flex items-center gap-2">
                <Label htmlFor="page-size" className="text-muted-foreground">
                    Показывать на странице
                </Label>
                <Select
                    value={String(pageSize)}
                    onValueChange={(value) => {
                        setPageSize(Number(value))
                        setCurrentPage(1)
                    }}
                >
                    <SelectTrigger id="page-size" size="sm" className="w-20">
                        <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                        {PAGE_SIZES.map((size) => (
                            <SelectItem key={size} value={String(size)}>
                                {size}
                            </SelectItem>
                        ))}
                    </SelectContent>
                </Select>
            </div>
        </PageContainer>
    )
}

export default BankDetailPage
