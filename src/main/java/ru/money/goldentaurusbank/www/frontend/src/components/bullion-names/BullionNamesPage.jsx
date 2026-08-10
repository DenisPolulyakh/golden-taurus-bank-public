import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import api from '../../api/axios';
import BullionNameModal from './BullionNameModal';
import './BullionNames.css';

function BullionNamesPage() {
    const [bullionNames, setBullionNames] = useState([]);
    const [filteredBullionNames, setFilteredBullionNames] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [modalOpen, setModalOpen] = useState(false);
    const [editingBullionName, setEditingBullionName] = useState(null);
    const [currentPage, setCurrentPage] = useState(1);
    const [itemsPerPage] = useState(10);
    const [importing, setImporting] = useState(false);

    const fileInputRef = useRef(null);

    // Сортировка
    const [sortOrder, setSortOrder] = useState('asc');

    const navigate = useNavigate();

    useEffect(() => {
        fetchBullionNames();
    }, []);

    useEffect(() => {
        let filtered = [...bullionNames];

        if (searchTerm) {
            filtered = filtered.filter(bn =>
                bn.title.toLowerCase().includes(searchTerm.toLowerCase())
            );
        }

        filtered.sort((a, b) => {
            if (sortOrder === 'asc') {
                return a.title.localeCompare(b.title, 'ru');
            } else {
                return b.title.localeCompare(a.title, 'ru');
            }
        });

        setFilteredBullionNames(filtered);
        setCurrentPage(1);
    }, [searchTerm, bullionNames, sortOrder]);

    const fetchBullionNames = async () => {
        try {
            const response = await api.get('/bullion-names');
            setBullionNames(response.data.data || []);
        } catch (err) {
            console.error('Ошибка загрузки наименований:', err);
            setError('Не удалось загрузить наименования');
        } finally {
            setLoading(false);
        }
    };

    const handleAddBullionName = () => {
        setEditingBullionName(null);
        setModalOpen(true);
    };

    const handleEditBullionName = (bullionName) => {
        setEditingBullionName(bullionName);
        setModalOpen(true);
    };

    const handleSaveBullionName = async (title, color) => {
        try {
            if (editingBullionName) {
                const response = await api.put(`/bullion-names/${editingBullionName.id}`, {
                    title,
                    color
                });
                setBullionNames(prev => prev.map(bn =>
                    bn.id === editingBullionName.id ? response.data.data : bn
                ));
            } else {
                const response = await api.post('/bullion-names', {
                    title,
                    color
                });
                setBullionNames(prev => [...prev, response.data.data]);
            }
            setModalOpen(false);
            setEditingBullionName(null);
        } catch (err) {
            console.error('Ошибка сохранения наименования:', err);
            throw err;
        }
    };

    const handleDeleteBullionName = async (id, title) => {
        if (window.confirm(`Удалить наименование "${title}"?`)) {
            try {
                await api.delete(`/bullion-names/${id}`);
                setBullionNames(prev => prev.filter(bn => bn.id !== id));
            } catch (err) {
                console.error('Ошибка удаления наименования:', err);
            }
        }
    };

    // Экспорт в Excel
    const handleExport = async () => {
        try {
            const response = await api.get('/bullion-names/export', {
                responseType: 'blob',
                _skipErrorToast: true  // тело ответа — blob, message не прочитать, показываем свой тост
            });

            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', 'bullion-names.xlsx');
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(url);
        } catch (err) {
            console.error('Ошибка экспорта:', err);
            toast.error('Не удалось экспортировать наименования');
        }
    };

    // Импорт из Excel
    const handleImportClick = () => {
        fileInputRef.current.click();
    };

    const handleFileChange = async (event) => {
        const file = event.target.files[0];
        if (!file) return;

        const fileExt = file.name.split('.').pop().toLowerCase();
        if (!['xlsx', 'xls'].includes(fileExt)) {
            toast.error('Пожалуйста, выберите файл с расширением .xlsx или .xls');
            return;
        }

        const formData = new FormData();
        formData.append('file', file);

        setImporting(true);

        try {
            const response = await api.post('/bullion-names/import', formData, {
                headers: {
                    'Content-Type': 'multipart/form-data',
                },
            });

            const result = response.data.data;

            let description = `Добавлено: ${result.added}, пропущено (дубликаты): ${result.skipped}`;
            if (result.errors && result.errors.length > 0) {
                description += `\nОшибки (${result.errors.length}): ${result.errors.slice(0, 5).join('; ')}`;
                if (result.errors.length > 5) {
                    description += ` ...и еще ${result.errors.length - 5}`;
                }
                toast.warning('Импорт завершён с ошибками', { description });
            } else {
                toast.success('Импорт завершён', { description });
            }

            await fetchBullionNames();
        } catch (err) {
            console.error('Ошибка импорта:', err);
            toast.error('Ошибка при импорте файла: ' + (err.response?.data?.message || err.message));
        } finally {
            setImporting(false);
            if (fileInputRef.current) {
                fileInputRef.current.value = '';
            }
        }
    };

    const toggleSortOrder = () => {
        setSortOrder(sortOrder === 'asc' ? 'desc' : 'asc');
    };

    const indexOfLastItem = currentPage * itemsPerPage;
    const indexOfFirstItem = indexOfLastItem - itemsPerPage;
    const currentItems = filteredBullionNames.slice(indexOfFirstItem, indexOfLastItem);
    const totalPages = Math.ceil(filteredBullionNames.length / itemsPerPage);

    const paginate = (pageNumber) => setCurrentPage(pageNumber);

    if (loading) {
        return <div className="bullionNames-container">Загрузка...</div>;
    }

    return (
        <div className="bullionNames-container">
            <div className="bullionNames-content">
                <div className="bullionNames-header">
                    <h1>📁 Управление наименованиями</h1>
                    <div className="header-actions">
                        <button onClick={() => navigate('/dashboard')} className="back-btn">
                            ← Назад
                        </button>
                        <button onClick={handleExport} className="export-btn">
                            📎 Экспорт Excel
                        </button>
                        <button onClick={handleImportClick} className="import-btn" disabled={importing}>
                            📂 Импорт Excel
                        </button>
                        <button onClick={handleAddBullionName} className="add-bullionName-btn">
                            + Добавить наименование
                        </button>
                    </div>
                </div>

                <div className="search-bar">
                    <input
                        type="text"
                        placeholder="🔍 Поиск наименований..."
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                    />
                </div>

                {error && <div className="error-message">{error}</div>}

                {/* Скрытый input для выбора файла */}
                <input
                    type="file"
                    ref={fileInputRef}
                    onChange={handleFileChange}
                    accept=".xlsx,.xls"
                    style={{ display: 'none' }}
                />

                {/* Индикатор импорта */}
                {importing && (
                    <div className="import-progress">
                        <div className="spinner"></div>
                        <span>Импорт данных...</span>
                    </div>
                )}

                <div className="bullionNames-table-wrapper">
                    <table className="bullionNames-table">
                        <thead>
                        <tr>
                            <th className="sortable-header" onClick={toggleSortOrder}>
                                Название наименования
                                <span className="sort-indicator">
                                    {sortOrder === 'asc' ? ' ↑' : ' ↓'}
                                </span>
                            </th>
                            <th>Действия</th>
                        </tr>
                        </thead>
                        <tbody>
                        {currentItems.length === 0 ? (
                            <tr>
                                <td colSpan="2" className="empty-row">
                                    {searchTerm ? 'Ничего не найдено' : 'Нет наименований. Добавьте первое или импортируйте из Excel!'}
                                </td>
                            </tr>
                        ) : (
                            currentItems.map((bullionName) => (
                                <tr key={bullionName.id}>
                                    <td>
                                        <div className="bullionName-name-with-color">
                                            <span
                                                className="bullionName-color-dot"
                                                style={{ backgroundColor: bullionName.color || '#cccccc' }}
                                            />
                                            <span className="bullionName-name">{bullionName.title}</span>
                                        </div>
                                    </td>
                                    <td className="actions">
                                        <button
                                            onClick={() => handleEditBullionName(bullionName)}
                                            className="edit-btn"
                                            title="Редактировать"
                                        >
                                            ✏️
                                        </button>
                                        <button
                                            onClick={() => handleDeleteBullionName(bullionName.id, bullionName.title)}
                                            className="delete-btn"
                                            title="Удалить"
                                        >
                                            🗑️
                                        </button>
                                    </td>
                                </tr>
                            ))
                        )}
                        </tbody>
                    </table>
                </div>

                {totalPages > 1 && (
                    <div className="pagination">
                        <button
                            onClick={() => paginate(currentPage - 1)}
                            disabled={currentPage === 1}
                            className="page-btn"
                        >
                            ← Назад
                        </button>
                        <span className="page-info">
                            Страница {currentPage} из {totalPages}
                        </span>
                        <button
                            onClick={() => paginate(currentPage + 1)}
                            disabled={currentPage === totalPages}
                            className="page-btn"
                        >
                            Вперёд →
                        </button>
                    </div>
                )}

                <BullionNameModal
                    isOpen={modalOpen}
                    onClose={() => {
                        setModalOpen(false);
                        setEditingBullionName(null);
                    }}
                    onSave={handleSaveBullionName}
                    initialTitle={editingBullionName?.title || ''}
                    initialColor={editingBullionName?.color || ''}
                    isEditing={!!editingBullionName}
                />
            </div>
        </div>
    );
}

export default BullionNamesPage;
