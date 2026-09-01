import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Pencil, Plus, Trash2, Vault } from 'lucide-react'
import api from '@/api/axios'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { PageContainer, PageHeader } from '@/components/ui-app/page-header'
import { SearchInput } from '@/components/ui-app/search-input'
import { SortableHead, TablePager } from '@/components/ui-app/data-table'
import { EmptyState, ErrorMessage, PageLoading } from '@/components/ui-app/page-state'
import { useConfirm } from '@/components/ui-app/confirm-dialog'
import { formatAmount, formatShortDate } from '@/lib/format'
import VaultModal from './VaultModal'

const PAGE_SIZES = [5, 10, 20, 50]

function VaultsPage() {
    const [vaults, setVaults] = useState([])
    const [searchTerm, setSearchTerm] = useState('')
    const [loading, setLoading] = useState(false)
    const [tableLoading, setTableLoading] = useState(false)
    const [error, setError] = useState('')
    const [modalOpen, setModalOpen] = useState(false)
    const [editingVault, setEditingVault] = useState(null)

    const [currentPage, setCurrentPage] = useState(1)
    const [pageSize, setPageSize] = useState(10)
    const [totalPages, setTotalPages] = useState(0)
    const [totalElements, setTotalElements] = useState(0)

    const [sortField, setSortField] = useState('name')
    const [sortOrder, setSortOrder] = useState('asc')

    const navigate = useNavigate()
    const timeoutRef = useRef(null)
    const isInitialMount = useRef(true)
    const { confirm, confirmDialog } = useConfirm()

    const fetchVaults = useCallback(async (search, sortBy, sortOrderParam, page, size, showTableLoader = true) => {
        if (showTableLoader) {
            setTableLoading(true)
        } else {
            setLoading(true)
        }

        try {
            const params = new URLSearchParams()
            if (search) params.append('search', search)
            if (sortBy) params.append('sortBy', sortBy)
            if (sortOrderParam) params.append('sortOrder', sortOrderParam)
            params.append('page', page)
            params.append('size', size)

            const response = await api.get(`/vaults?${params.toString()}`)
            const pageData = response.data.data
            setVaults(pageData.content || [])
            setCurrentPage(pageData.pageNumber)
            setTotalPages(pageData.totalPages)
            setTotalElements(pageData.totalElements)
        } catch (err) {
            console.error('Ошибка загрузки хранилищ:', err)
            setError('Не удалось загрузить хранилища')
        } finally {
            setLoading(false)
            setTableLoading(false)
        }
    }, [])

    const debouncedFetch = useCallback((search, sortBy, sortOrderParam, page, size) => {
        if (timeoutRef.current) {
            clearTimeout(timeoutRef.current)
        }
        timeoutRef.current = setTimeout(() => {
            fetchVaults(search, sortBy, sortOrderParam, page, size, true)
        }, 300)
    }, [fetchVaults])

    useEffect(() => {
        if (isInitialMount.current) {
            isInitialMount.current = false
            fetchVaults(searchTerm, sortField, sortOrder, currentPage, pageSize, false)
            return
        }

        debouncedFetch(searchTerm, sortField, sortOrder, currentPage, pageSize)

        return () => {
            if (timeoutRef.current) {
                clearTimeout(timeoutRef.current)
            }
        }
    }, [searchTerm, sortField, sortOrder, currentPage, pageSize, debouncedFetch, fetchVaults])

    const handleAddVault = () => {
        setEditingVault(null)
        setModalOpen(true)
    }

    const handleEditVault = (vault) => {
        setEditingVault(vault)
        setModalOpen(true)
    }

    const handleSaveVault = async (form) => {
        try {
            let savedVault

            const requestData = {
                name: form.name,
                interestRate: form.interestRate,
                description: form.description,
                bankId: form.bankId || null,
                accountType: form.accountType || 'SAVINGS',
                closeDate: form.closeDate || null,
                allowedIncome: form.allowedIncome,
                allowedExpense: form.allowedExpense,
                allowedTransfer: form.allowedTransfer,
            }

            if (editingVault) {
                const response = await api.put(`/vaults/${editingVault.id}`, requestData)
                savedVault = response.data.data
                setVaults((prev) => prev.map((v) => (v.id === editingVault.id ? savedVault : v)))
            } else {
                const response = await api.post('/vaults', requestData)
                savedVault = response.data.data
                setVaults((prev) => [savedVault, ...prev])
                if (vaults.length + 1 > pageSize) {
                    fetchVaults(searchTerm, sortField, sortOrder, 1, pageSize, true)
                }
            }

            setModalOpen(false)
            setEditingVault(null)
        } catch (err) {
            console.error('Ошибка сохранения хранилища:', err)
            fetchVaults(searchTerm, sortField, sortOrder, currentPage, pageSize, true)
            throw err
        }
    }

    const handleDeleteVault = (vault) => {
        const { id, name } = vault
        confirm({
            title: 'Удаление хранилища',
            description: isLiquidityReserve(vault)
                ? `Удалить ликвидное хранилище "${name}"? Оно создастся заново, когда понадобится для переноса остатков.`
                : `Удалить хранилище "${name}"?`,
            onConfirm: async () => {
                const oldVaults = [...vaults]
                setVaults((prev) => prev.filter((vault) => vault.id !== id))

                try {
                    setError('')
                    await api.delete(`/vaults/${id}`)
                    if (vaults.length === 1 && currentPage > 1) {
                        setCurrentPage(currentPage - 1)
                    }
                } catch (err) {
                    console.error('Ошибка удаления хранилища:', err)
                    setVaults(oldVaults)
                    setError(err.response?.data?.message || 'Не удалось удалить хранилище')
                }
            },
        })
    }

    const handleVaultClick = (vaultId) => {
        navigate(`/vaults/${vaultId}`, { state: { from: 'vaults' } })
    }

    const toggleSort = (field) => {
        setCurrentPage(1)
        if (sortField === field) {
            setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')
        } else {
            setSortField(field)
            setSortOrder('asc')
        }
    }

    // Тип, а не название: резерв можно переименовать, особенным он от этого быть не перестанет
    const isLiquidityReserve = (vault) => vault.vaultType === 'LIQUIDITY_BUFFER'
    // Резерв не редактируют - имя, ставку и банк он держит свои,
    // но пустой удаляется: бэкенд заведёт его заново, когда понадобится
    const canEdit = (vault) => !isLiquidityReserve(vault)
    const canDelete = (vault) => !isLiquidityReserve(vault) || Number(vault.totalAmount) === 0
    const hasAnyActions = vaults.some((v) => canEdit(v) || canDelete(v))
    const columnCount = hasAnyActions ? 7 : 6

    if (loading && vaults.length === 0) {
        return (
            <PageContainer>
                <PageLoading />
            </PageContainer>
        )
    }

    return (
        <TooltipProvider>
            <PageContainer>
                <PageHeader title="Хранилища" onBack={() => navigate('/dashboard')}>
                    <Button onClick={handleAddVault}>
                        <Plus />
                        Добавить хранилище
                    </Button>
                </PageHeader>

                <SearchInput
                    value={searchTerm}
                    onChange={setSearchTerm}
                    placeholder="Поиск хранилищ..."
                />

                <ErrorMessage>{error}</ErrorMessage>

                <Card className="overflow-hidden py-0">
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <SortableHead
                                    label="Банк"
                                    field="bankName"
                                    sortField={sortField}
                                    sortOrder={sortOrder}
                                    onSort={toggleSort}
                                />
                                <SortableHead
                                    label="Имя хранилища"
                                    field="name"
                                    sortField={sortField}
                                    sortOrder={sortOrder}
                                    onSort={toggleSort}
                                />
                                <SortableHead
                                    label="Всего в хранилище"
                                    field="totalAmount"
                                    sortField={sortField}
                                    sortOrder={sortOrder}
                                    onSort={toggleSort}
                                />
                                <TableHead>Тип счета</TableHead>
                                <SortableHead
                                    label="Ставка (%)"
                                    field="interestRate"
                                    sortField={sortField}
                                    sortOrder={sortOrder}
                                    onSort={toggleSort}
                                />
                                <TableHead className="w-full">Описание</TableHead>
                                {hasAnyActions && (
                                    <TableHead className="w-28 text-right">Действия</TableHead>
                                )}
                            </TableRow>
                        </TableHeader>

                        <TableBody>
                            {tableLoading ? (
                                Array.from({ length: 3 }).map((_, row) => (
                                    <TableRow key={row}>
                                        {Array.from({ length: columnCount }).map((__, cell) => (
                                            <TableCell key={cell}>
                                                <Skeleton className="h-5 w-full" />
                                            </TableCell>
                                        ))}
                                    </TableRow>
                                ))
                            ) : vaults.length === 0 ? (
                                <TableRow>
                                    <TableCell colSpan={columnCount} className="p-0">
                                        <EmptyState
                                            className="border-0"
                                            icon={Vault}
                                            title={searchTerm ? 'Ничего не найдено' : 'Нет хранилищ'}
                                            description={
                                                searchTerm
                                                    ? 'Попробуйте изменить запрос'
                                                    : 'Добавьте первое хранилище'
                                            }
                                        />
                                    </TableCell>
                                </TableRow>
                            ) : (
                                vaults.map((vault) => {
                                    const editable = canEdit(vault)
                                    const deletable = canDelete(vault)

                                    return (
                                        <TableRow key={vault.id}>
                                            <TableCell>
                                                {vault.bankName ? (
                                                    <Link
                                                        to={`/banks/${vault.bankId}`}
                                                        className="text-primary hover:underline"
                                                    >
                                                        {vault.bankName}
                                                    </Link>
                                                ) : (
                                                    <span className="text-muted-foreground">—</span>
                                                )}
                                            </TableCell>
                                            <TableCell>
                                                <button
                                                    type="button"
                                                    onClick={() => handleVaultClick(vault.id)}
                                                    className="text-left font-medium text-primary hover:underline"
                                                >
                                                    {vault.name}
                                                </button>
                                            </TableCell>
                                            <TableCell className="font-medium tabular-nums">
                                                {formatAmount(vault.totalAmount || 0)} ₽
                                            </TableCell>
                                            <TableCell>
                                                <span className="flex flex-wrap items-center gap-1.5">
                                                    <Badge
                                                        variant={
                                                            vault.accountType === 'SAVINGS'
                                                                ? 'success'
                                                                : 'info'
                                                        }
                                                    >
                                                        {vault.accountType === 'SAVINGS'
                                                            ? 'Накопительный'
                                                            : 'Срочный'}
                                                    </Badge>
                                                    {vault.closeDate && vault.accountType === 'TERM' && (
                                                        <span className="text-xs text-muted-foreground">
                                                            до {formatShortDate(vault.closeDate)}
                                                        </span>
                                                    )}
                                                </span>
                                            </TableCell>
                                            <TableCell className="tabular-nums">
                                                {vault.interestRate}%
                                            </TableCell>
                                            <TableCell className="w-full whitespace-normal text-muted-foreground">
                                                <div className="max-w-xs break-words">
                                                    {vault.description || ''}
                                                </div>
                                            </TableCell>
                                            {hasAnyActions && (
                                                <TableCell className="text-right">
                                                    {editable && (
                                                        <Tooltip>
                                                            <TooltipTrigger asChild>
                                                                <Button
                                                                    variant="ghost"
                                                                    size="icon-sm"
                                                                    onClick={() => handleEditVault(vault)}
                                                                    aria-label="Редактировать"
                                                                >
                                                                    <Pencil />
                                                                </Button>
                                                            </TooltipTrigger>
                                                            <TooltipContent>Редактировать</TooltipContent>
                                                        </Tooltip>
                                                    )}
                                                    {deletable && (
                                                        <Tooltip>
                                                            <TooltipTrigger asChild>
                                                                <Button
                                                                    variant="ghost"
                                                                    size="icon-sm"
                                                                    className="text-destructive hover:text-destructive"
                                                                    onClick={() => handleDeleteVault(vault)}
                                                                    aria-label="Удалить"
                                                                >
                                                                    <Trash2 />
                                                                </Button>
                                                            </TooltipTrigger>
                                                            <TooltipContent>Удалить</TooltipContent>
                                                        </Tooltip>
                                                    )}
                                                </TableCell>
                                            )}
                                        </TableRow>
                                    )
                                })
                            )}
                        </TableBody>
                    </Table>
                </Card>

                {!tableLoading && (
                    <TablePager
                        page={currentPage}
                        totalPages={totalPages}
                        onPageChange={setCurrentPage}
                        total={totalElements}
                        totalLabel="Всего хранилищ:"
                    />
                )}

                <div className="flex items-center gap-2">
                    <Label htmlFor="vault-page-size" className="text-muted-foreground">
                        Показывать на странице
                    </Label>
                    <Select
                        value={String(pageSize)}
                        onValueChange={(value) => {
                            setPageSize(Number(value))
                            setCurrentPage(1)
                        }}
                    >
                        <SelectTrigger id="vault-page-size" size="sm" className="w-20">
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

                <VaultModal
                    isOpen={modalOpen}
                    onClose={() => {
                        setModalOpen(false)
                        setEditingVault(null)
                    }}
                    onSave={handleSaveVault}
                    initialName={editingVault?.name || ''}
                    initialInterestRate={editingVault?.interestRate || 0}
                    initialDescription={editingVault?.description || ''}
                    initialBankId={editingVault?.bankId || ''}
                    initialAccountType={editingVault?.accountType || 'SAVINGS'}
                    initialCloseDate={editingVault?.closeDate || ''}
                    initialAllowedIncome={editingVault?.settings?.allowedIncome !== false}
                    initialAllowedExpense={editingVault?.settings?.allowedExpense !== false}
                    initialAllowedTransfer={editingVault?.settings?.allowedTransfer !== false}
                    isEditing={!!editingVault}
                />

                {confirmDialog}
            </PageContainer>
        </TooltipProvider>
    )
}

export default VaultsPage
