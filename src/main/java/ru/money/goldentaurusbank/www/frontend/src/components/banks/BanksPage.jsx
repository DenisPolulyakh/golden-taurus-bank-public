import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileDown, FileUp, Landmark, Pencil, Plus, Trash2 } from 'lucide-react'
import api from '@/api/axios'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
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
import { useExcelPort } from '@/components/ui-app/use-excel-port'
import BankModal from './BankModal'

const ITEMS_PER_PAGE = 10

function BanksPage() {
    const [banks, setBanks] = useState([])
    const [filteredBanks, setFilteredBanks] = useState([])
    const [searchTerm, setSearchTerm] = useState('')
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [modalOpen, setModalOpen] = useState(false)
    const [editingBank, setEditingBank] = useState(null)
    const [currentPage, setCurrentPage] = useState(1)

    const [sortOrder, setSortOrder] = useState('asc')
    const navigate = useNavigate()
    const { confirm, confirmDialog } = useConfirm()

    const { importing, handleExport, handleImportClick, fileInputProps } = useExcelPort({
        basePath: '/banks',
        fileName: 'banks.xlsx',
        exportErrorText: 'Не удалось экспортировать банки',
        onImported: () => fetchBanks(),
    })

    useEffect(() => {
        fetchBanks()
    }, [])

    useEffect(() => {
        let filtered = [...banks]

        if (searchTerm) {
            filtered = filtered.filter((bank) =>
                bank.name.toLowerCase().includes(searchTerm.toLowerCase())
            )
        }

        filtered.sort((a, b) =>
            sortOrder === 'asc'
                ? a.name.localeCompare(b.name, 'ru')
                : b.name.localeCompare(a.name, 'ru')
        )

        setFilteredBanks(filtered)
        setCurrentPage(1)
    }, [searchTerm, banks, sortOrder])

    const fetchBanks = async () => {
        try {
            const response = await api.get('/banks')
            setBanks(response.data.data || [])
            setError('')
        } catch (err) {
            console.error('Ошибка загрузки банков:', err)
            setError('Не удалось загрузить список банков')
        } finally {
            setLoading(false)
        }
    }

    const handleAddBank = () => {
        setEditingBank(null)
        setModalOpen(true)
    }

    const handleEditBank = (bank) => {
        setEditingBank(bank)
        setModalOpen(true)
    }

    const handleSaveBank = async (name) => {
        try {
            if (editingBank) {
                const response = await api.put(`/banks/${editingBank.id}`, { name })
                setBanks((prev) =>
                    prev.map((bank) => (bank.id === editingBank.id ? response.data.data : bank))
                )
            } else {
                const response = await api.post('/banks', { name })
                setBanks((prev) => [...prev, response.data.data])
            }
            setModalOpen(false)
            setEditingBank(null)
        } catch (err) {
            console.error('Ошибка сохранения банка:', err)
            throw err
        }
    }

    const handleDeleteBank = (id, name) => {
        confirm({
            title: 'Удаление банка',
            description: `Удалить банк "${name}"?`,
            onConfirm: async () => {
                try {
                    await api.delete(`/banks/${id}`)
                    setBanks((prev) => prev.filter((bank) => bank.id !== id))
                } catch (err) {
                    console.error('Ошибка удаления банка:', err)
                }
            },
        })
    }

    const toggleSortOrder = () => {
        setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')
    }

    const indexOfLastItem = currentPage * ITEMS_PER_PAGE
    const currentItems = filteredBanks.slice(indexOfLastItem - ITEMS_PER_PAGE, indexOfLastItem)
    const totalPages = Math.ceil(filteredBanks.length / ITEMS_PER_PAGE)

    if (loading) {
        return (
            <PageContainer>
                <PageLoading />
            </PageContainer>
        )
    }

    return (
        <TooltipProvider>
            <PageContainer>
                <PageHeader title="Справочник банков" onBack={() => navigate('/dashboard')}>
                    <Button variant="outline" onClick={handleExport}>
                        <FileDown />
                        Экспорт Excel
                    </Button>
                    <Button variant="outline" onClick={handleImportClick} disabled={importing}>
                        {importing ? <Spinner /> : <FileUp />}
                        {importing ? 'Импорт данных...' : 'Импорт Excel'}
                    </Button>
                    <Button onClick={handleAddBank}>
                        <Plus />
                        Добавить банк
                    </Button>
                </PageHeader>

                <SearchInput
                    value={searchTerm}
                    onChange={setSearchTerm}
                    placeholder="Поиск банков..."
                />

                <ErrorMessage>{error}</ErrorMessage>

                {/* Скрытый input для выбора файла */}
                <input {...fileInputProps} />

                {currentItems.length === 0 ? (
                    <EmptyState
                        icon={Landmark}
                        title={searchTerm ? 'Ничего не найдено' : 'Нет банков'}
                        description={
                            searchTerm
                                ? 'Попробуйте изменить запрос'
                                : 'Добавьте первый банк или импортируйте список из Excel'
                        }
                    >
                        {!searchTerm && (
                            <Button onClick={handleAddBank}>
                                <Plus />
                                Добавить банк
                            </Button>
                        )}
                    </EmptyState>
                ) : (
                    <Card className="overflow-hidden py-0">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <SortableHead
                                        label="Название банка"
                                        field="name"
                                        sortField="name"
                                        sortOrder={sortOrder}
                                        onSort={toggleSortOrder}
                                    />
                                    <TableHead className="w-28 text-right">Действия</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {currentItems.map((bank) => (
                                    <TableRow key={bank.id}>
                                        <TableCell className="font-medium">{bank.name}</TableCell>
                                        <TableCell className="text-right">
                                            <Tooltip>
                                                <TooltipTrigger asChild>
                                                    <Button
                                                        variant="ghost"
                                                        size="icon-sm"
                                                        onClick={() => handleEditBank(bank)}
                                                        aria-label="Редактировать"
                                                    >
                                                        <Pencil />
                                                    </Button>
                                                </TooltipTrigger>
                                                <TooltipContent>Редактировать</TooltipContent>
                                            </Tooltip>
                                            <Tooltip>
                                                <TooltipTrigger asChild>
                                                    <Button
                                                        variant="ghost"
                                                        size="icon-sm"
                                                        className="text-destructive hover:text-destructive"
                                                        onClick={() => handleDeleteBank(bank.id, bank.name)}
                                                        aria-label="Удалить"
                                                    >
                                                        <Trash2 />
                                                    </Button>
                                                </TooltipTrigger>
                                                <TooltipContent>Удалить</TooltipContent>
                                            </Tooltip>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </Card>
                )}

                <TablePager
                    page={currentPage}
                    totalPages={totalPages}
                    onPageChange={setCurrentPage}
                    total={filteredBanks.length}
                    totalLabel="Всего банков:"
                />

                <BankModal
                    isOpen={modalOpen}
                    onClose={() => {
                        setModalOpen(false)
                        setEditingBank(null)
                    }}
                    onSave={handleSaveBank}
                    initialName={editingBank?.name || ''}
                    isEditing={!!editingBank}
                />

                {confirmDialog}
            </PageContainer>
        </TooltipProvider>
    )
}

export default BanksPage
