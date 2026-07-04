import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../../api/axios';
import CategoryModal from './CategoryModal';
import './Categories.css';

function CategoriesPage() {
    const [categories, setCategories] = useState([]);
    const [filteredCategories, setFilteredCategories] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [modalOpen, setModalOpen] = useState(false);
    const [editingCategory, setEditingCategory] = useState(null);
    const [currentPage, setCurrentPage] = useState(1);
    const [itemsPerPage] = useState(10);
    const [importing, setImporting] = useState(false);

    const fileInputRef = useRef(null);

    // Сортировка
    const [sortOrder, setSortOrder] = useState('asc');

    const navigate = useNavigate();

    useEffect(() => {
        fetchCategories();
    }, []);

    useEffect(() => {
        let filtered = [...categories];

        if (searchTerm) {
            filtered = filtered.filter(cat =>
                cat.name.toLowerCase().includes(searchTerm.toLowerCase())
            );
        }

        filtered.sort((a, b) => {
            if (sortOrder === 'asc') {
                return a.name.localeCompare(b.name, 'ru');
            } else {
                return b.name.localeCompare(a.name, 'ru');
            }
        });

        setFilteredCategories(filtered);
        setCurrentPage(1);
    }, [searchTerm, categories, sortOrder]);

    const fetchCategories = async () => {
        try {
            const response = await api.get('/categories');
            setCategories(response.data.data || []);
        } catch (err) {
            console.error('Ошибка загрузки категорий:', err);
            setError('Не удалось загрузить категории');
        } finally {
            setLoading(false);
        }
    };

    const handleAddCategory = () => {
        setEditingCategory(null);
        setModalOpen(true);
    };

    const handleEditCategory = (category) => {
        setEditingCategory(category);
        setModalOpen(true);
    };

    const handleSaveCategory = async (name, color) => {
        try {
            if (editingCategory) {
                const response = await api.put(`/categories/${editingCategory.id}`, {
                    name,
                    color
                });
                setCategories(prev => prev.map(cat =>
                    cat.id === editingCategory.id ? response.data.data : cat
                ));
            } else {
                const response = await api.post('/categories', {
                    name,
                    color
                });
                setCategories(prev => [...prev, response.data.data]);
            }
            setModalOpen(false);
            setEditingCategory(null);
        } catch (err) {
            console.error('Ошибка сохранения категории:', err);
            throw err;
        }
    };

    const handleDeleteCategory = async (id, name) => {
        if (window.confirm(`Удалить категорию "${name}"?`)) {
            try {
                await api.delete(`/categories/${id}`);
                setCategories(prev => prev.filter(cat => cat.id !== id));
            } catch (err) {
                console.error('Ошибка удаления категории:', err);
                alert('Не удалось удалить категорию');
            }
        }
    };

    // Экспорт в Excel
    const handleExport = async () => {
        try {
            const response = await api.get('/categories/export', {
                responseType: 'blob'
            });

            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', 'categories.xlsx');
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(url);
        } catch (err) {
            console.error('Ошибка экспорта:', err);
            alert('Не удалось экспортировать категории');
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
            alert('Пожалуйста, выберите файл с расширением .xlsx или .xls');
            return;
        }

        const formData = new FormData();
        formData.append('file', file);

        setImporting(true);

        try {
            const response = await api.post('/categories/import', formData, {
                headers: {
                    'Content-Type': 'multipart/form-data',
                },
            });

            const result = response.data.data;

            let message = `✅ Импорт завершен!\nДобавлено: ${result.added}\nПропущено (дубликаты): ${result.skipped}`;
            if (result.errors && result.errors.length > 0) {
                message += `\n\nОшибки (${result.errors.length}):\n${result.errors.slice(0, 5).join('\n')}`;
                if (result.errors.length > 5) {
                    message += `\n...и еще ${result.errors.length - 5} ошибок`;
                }
            }
            alert(message);

            await fetchCategories();
        } catch (err) {
            console.error('Ошибка импорта:', err);
            alert('Ошибка при импорте файла: ' + (err.response?.data?.message || err.message));
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
    const currentItems = filteredCategories.slice(indexOfFirstItem, indexOfLastItem);
    const totalPages = Math.ceil(filteredCategories.length / itemsPerPage);

    const paginate = (pageNumber) => setCurrentPage(pageNumber);

    if (loading) {
        return <div className="categories-container">Загрузка...</div>;
    }

    return (
        <div className="categories-container">
            <div className="categories-content">
                <div className="categories-header">
                    <h1>📁 Управление категориями</h1>
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
                        <button onClick={handleAddCategory} className="add-category-btn">
                            + Добавить категорию
                        </button>
                    </div>
                </div>

                <div className="search-bar">
                    <input
                        type="text"
                        placeholder="🔍 Поиск категорий..."
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

                <div className="categories-table-wrapper">
                    <table className="categories-table">
                        <thead>
                        <tr>
                            <th className="sortable-header" onClick={toggleSortOrder}>
                                Название категории
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
                                    {searchTerm ? 'Ничего не найдено' : 'Нет категорий. Добавьте первую или импортируйте из Excel!'}
                                </td>
                            </tr>
                        ) : (
                            currentItems.map((category) => (
                                <tr key={category.id}>
                                    <td>
                                        <div className="category-name-with-color">
                                            <span
                                                className="category-color-dot"
                                                style={{ backgroundColor: category.color || '#cccccc' }}
                                            />
                                            <span className="category-name">{category.name}</span>
                                        </div>
                                    </td>
                                    <td className="actions">
                                        <button
                                            onClick={() => handleEditCategory(category)}
                                            className="edit-btn"
                                            title="Редактировать"
                                        >
                                            ✏️
                                        </button>
                                        <button
                                            onClick={() => handleDeleteCategory(category.id, category.name)}
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

                <CategoryModal
                    isOpen={modalOpen}
                    onClose={() => {
                        setModalOpen(false);
                        setEditingCategory(null);
                    }}
                    onSave={handleSaveCategory}
                    initialName={editingCategory?.name || ''}
                    initialColor={editingCategory?.color || ''}
                    isEditing={!!editingCategory}
                />
            </div>
        </div>
    );
}

export default CategoriesPage;