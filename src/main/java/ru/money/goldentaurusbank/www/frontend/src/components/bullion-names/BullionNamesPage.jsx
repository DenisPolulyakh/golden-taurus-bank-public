import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { FileDown, FileUp, FolderTree, Pencil, Plus, Trash2 } from 'lucide-react'
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
import BullionNameModal from './BullionNameModal'

const ITEMS_PER_PAGE = 10

function BullionNamesPage() {
    const [bullionNames, setBullionNames] = useState([])
    const [filteredBullionNames, setFilteredBullionNames] = useState([])
    const [searchTerm, setSearchTerm] = useState('')
    const [loading, setLoading] = useState(true)
    const [error, setError] = useState('')
    const [modalOpen, setModalOpen] = useState(false)
    const [editingBullionName, setEditingBullionName] = useState(null)
    const [currentPage, setCurrentPage] = useState(1)

    // Сортировка
    const [sortOrder, setSortOrder] = useState('asc')

    const navigate = useNavigate()
    const { confirm, confirmDialog } = useConfirm()

    const { importing, handleExport, handleImportClick, fileInputProps } = useExcelPort({
        basePath: '/bullion-names',
        fileName: 'bullion-names.xlsx',
        exportErrorText: 'Не удалось экспортировать наименования',
        onImported: () => fetchBullionNames(),
    })

    useEffect(() => {
        fetchBullionNames()
    }, [])

    useEffect(() => {
        let filtered = [...bullionNames]

        if (searchTerm) {
            filtered = filtered.filter((bn) =>
                bn.title.toLowerCase().includes(searchTerm.toLowerCase())
            )
        }

        filtered.sort((a, b) =>
            sortOrder === 'asc'
                ? a.title.localeCompare(b.title, 'ru')
                : b.title.localeCompare(a.title, 'ru')
        )

        setFilteredBullionNames(filtered)
        setCurrentPage(1)
    }, [searchTerm, bullionNames, sortOrder])

    const fetchBullionNames = async () => {
        try {
            const response = await api.get('/bullion-names')
            setBullionNames(response.data.data || [])
        } catch (err) {
            console.error('Ошибка загрузки наименований:', err)
            setError('Не удалось загрузить наименования')
        } finally {
            setLoading(false)
        }
    }

    const handleAddBullionName = () => {
        setEditingBullionName(null)
        setModalOpen(true)
    }

    const handleEditBullionName = (bullionName) => {
        setEditingBullionName(bullionName)
        setModalOpen(true)
    }

    const handleSaveBullionName = async (title, color) => {
        try {
            if (editingBullionName) {
                const response = await api.put(`/bullion-names/${editingBullionName.id}`, {
                    title,
                    color,
                })
                setBullionNames((prev) =>
                    prev.map((bn) => (bn.id === editingBullionName.id ? response.data.data : bn))
                )
            } else {
                const response = await api.post('/bullion-names', { title, color })
                setBullionNames((prev) => [...prev, response.data.data])
            }
            setModalOpen(false)
            setEditingBullionName(null)
        } catch (err) {
            console.error('Ошибка сохранения наименования:', err)
            throw err
        }
    }

    const handleDeleteBullionName = (id, title) => {
        confirm({
            title: 'Удаление наименования',
            description: `Удалить наименование "${title}"?`,
            onConfirm: async () => {
                try {
                    await api.delete(`/bullion-names/${id}`)
                    setBullionNames((prev) => prev.filter((bn) => bn.id !== id))
                } catch (err) {
                    console.error('Ошибка удаления наименования:', err)
                }
            },
        })
    }

    const toggleSortOrder = () => {
        setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc')
    }

    const indexOfLastItem = currentPage * ITEMS_PER_PAGE
    const currentItems = filteredBullionNames.slice(
        indexOfLastItem - ITEMS_PER_PAGE,
        indexOfLastItem
    )
    const totalPages = Math.ceil(filteredBullionNames.length / ITEMS_PER_PAGE)

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
                <PageHeader
                    title="Управление наименованиями"
                    onBack={() => navigate('/dashboard')}
                >
                    <Button variant="outline" onClick={handleExport}>
                        <FileDown />
                        Экспорт Excel
                    </Button>
                    <Button variant="outline" onClick={handleImportClick} disabled={importing}>
                        {importing ? <Spinner /> : <FileUp />}
                        {importing ? 'Импорт данных...' : 'Импорт Excel'}
                    </Button>
                    <Button onClick={handleAddBullionName}>
                        <Plus />
                        Добавить наименование
                    </Button>
                </PageHeader>

                <SearchInput
                    value={searchTerm}
                    onChange={setSearchTerm}
                    placeholder="Поиск наименований..."
                />

                <ErrorMessage>{error}</ErrorMessage>

                {/* Скрытый input для выбора файла */}
                <input {...fileInputProps} />

                {currentItems.length === 0 ? (
                    <EmptyState
                        icon={FolderTree}
                        title={searchTerm ? 'Ничего не найдено' : 'Нет наименований'}
                        description={
                            searchTerm
                                ? 'Попробуйте изменить запрос'
                                : 'Добавьте первое наименование или импортируйте список из Excel'
                        }
                    >
                        {!searchTerm && (
                            <Button onClick={handleAddBullionName}>
                                <Plus />
                                Добавить наименование
                            </Button>
                        )}
                    </EmptyState>
                ) : (
                    <Card className="overflow-hidden py-0">
                        <Table>
                            <TableHeader>
                                <TableRow>
                                    <SortableHead
                                        label="Название наименования"
                                        field="title"
                                        sortField="title"
                                        sortOrder={sortOrder}
                                        onSort={toggleSortOrder}
                                    />
                                    <TableHead className="w-28 text-right">Действия</TableHead>
                                </TableRow>
                            </TableHeader>
                            <TableBody>
                                {currentItems.map((bullionName) => (
                                    <TableRow key={bullionName.id}>
                                        <TableCell>
                                            <span className="flex items-center gap-2 font-medium">
                                                <span
                                                    className="inline-block size-3 shrink-0 rounded-full border"
                                                    style={{
                                                        backgroundColor:
                                                            bullionName.color || '#cccccc',
                                                    }}
                                                />
                                                {bullionName.title}
                                            </span>
                                        </TableCell>
                                        <TableCell className="text-right">
                                            <Tooltip>
                                                <TooltipTrigger asChild>
                                                    <Button
                                                        variant="ghost"
                                                        size="icon-sm"
                                                        onClick={() =>
                                                            handleEditBullionName(bullionName)
                                                        }
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
                                                        onClick={() =>
                                                            handleDeleteBullionName(
                                                                bullionName.id,
                                                                bullionName.title
                                                            )
                                                        }
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
                    total={filteredBullionNames.length}
                    totalLabel="Всего наименований:"
                />

                <BullionNameModal
                    isOpen={modalOpen}
                    onClose={() => {
                        setModalOpen(false)
                        setEditingBullionName(null)
                    }}
                    onSave={handleSaveBullionName}
                    initialTitle={editingBullionName?.title || ''}
                    initialColor={editingBullionName?.color || ''}
                    isEditing={!!editingBullionName}
                />

                {confirmDialog}
            </PageContainer>
        </TooltipProvider>
    )
}

export default BullionNamesPage
