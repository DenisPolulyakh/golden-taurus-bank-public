import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../../api/axios';
import BankModal from './BankModal';
import './Banks.css';

function BanksPage() {
    const [banks, setBanks] = useState([]);
    const [filteredBanks, setFilteredBanks] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [modalOpen, setModalOpen] = useState(false);
    const [editingBank, setEditingBank] = useState(null);
    const [currentPage, setCurrentPage] = useState(1);
    const [itemsPerPage] = useState(10);
    const [importing, setImporting] = useState(false);
    const [importResult, setImportResult] = useState(null);

    const fileInputRef = useRef(null);

    const [sortOrder, setSortOrder] = useState('asc');
    const navigate = useNavigate();

    useEffect(() => {
        fetchBanks();
    }, []);

    useEffect(() => {
        let filtered = [...banks];

        if (searchTerm) {
            filtered = filtered.filter(bank =>
                bank.name.toLowerCase().includes(searchTerm.toLowerCase())
            );
        }

        filtered.sort((a, b) => {
            if (sortOrder === 'asc') {
                return a.name.localeCompare(b.name, 'ru');
            } else {
                return b.name.localeCompare(a.name, 'ru');
            }
        });

        setFilteredBanks(filtered);
        setCurrentPage(1);
    }, [searchTerm, banks, sortOrder]);

    const fetchBanks = async () => {
        try {
            const response = await api.get('/banks');
            setBanks(response.data.data || []);
            setError('');
        } catch (err) {
            console.error('Ошибка загрузки банков:', err);
            setError('Не удалось загрузить список банков');
        } finally {
            setLoading(false);
        }
    };

    const handleAddBank = () => {
        setEditingBank(null);
        setModalOpen(true);
    };

    const handleEditBank = (bank) => {
        setEditingBank(bank);
        setModalOpen(true);
    };

    const handleSaveBank = async (name) => {
        try {
            if (editingBank) {
                const response = await api.put(`/banks/${editingBank.id}`, { name });
                setBanks(prev => prev.map(bank =>
                    bank.id === editingBank.id ? response.data.data : bank
                ));
            } else {
                const response = await api.post('/banks', { name });
                setBanks(prev => [...prev, response.data.data]);
            }
            setModalOpen(false);
            setEditingBank(null);
        } catch (err) {
            console.error('Ошибка сохранения банка:', err);
            throw err;
        }
    };

    const handleDeleteBank = async (id, name) => {
        if (window.confirm(`Удалить банк "${name}"?`)) {
            try {
                await api.delete(`/banks/${id}`);
                setBanks(prev => prev.filter(bank => bank.id !== id));
            } catch (err) {
                console.error('Ошибка удаления банка:', err);
                alert('Не удалось удалить банк');
            }
        }
    };

    // Экспорт в Excel
    const handleExport = async () => {
        try {
            const response = await api.get('/banks/export', {
                responseType: 'blob'
            });

            // Создаем ссылку для скачивания
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', 'banks.xlsx');
            document.body.appendChild(link);
            link.click();
            link.remove();
            window.URL.revokeObjectURL(url);
        } catch (err) {
            console.error('Ошибка экспорта:', err);
            alert('Не удалось экспортировать банки');
        }
    };

    // Импорт из Excel
    const handleImportClick = () => {
        fileInputRef.current.click();
    };

    const handleFileChange = async (event) => {
        const file = event.target.files[0];
        if (!file) return;

        // Проверка расширения
        const fileExt = file.name.split('.').pop().toLowerCase();
        if (!['xlsx', 'xls'].includes(fileExt)) {
            alert('Пожалуйста, выберите файл с расширением .xlsx или .xls');
            return;
        }

        const formData = new FormData();
        formData.append('file', file);

        setImporting(true);
        setImportResult(null);

        try {
            const response = await api.post('/banks/import', formData, {
                headers: {
                    'Content-Type': 'multipart/form-data',
                },
            });

            const result = response.data.data;
            setImportResult(result);

            // Показываем сообщение о результате
            let message = `✅ Импорт завершен!\nДобавлено: ${result.added}\nПропущено (дубликаты): ${result.skipped}`;
            if (result.errors.length > 0) {
                message += `\n\nОшибки (${result.errors.length}):\n${result.errors.slice(0, 5).join('\n')}`;
                if (result.errors.length > 5) {
                    message += `\n...и еще ${result.errors.length - 5} ошибок`;
                }
            }
            alert(message);

            // Обновляем список банков
            await fetchBanks();
        } catch (err) {
            console.error('Ошибка импорта:', err);
            alert('Ошибка при импорте файла: ' + (err.response?.data?.message || err.message));
        } finally {
            setImporting(false);
            // Очищаем input, чтобы можно было загрузить тот же файл повторно
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
    const currentItems = filteredBanks.slice(indexOfFirstItem, indexOfLastItem);
    const totalPages = Math.ceil(filteredBanks.length / itemsPerPage);

    const paginate = (pageNumber) => setCurrentPage(pageNumber);

    if (loading) {
        return <div className="banks-container">Загрузка...</div>;
    }

    return (
        <div className="banks-container">
            <div className="banks-content">
                <div className="banks-header">
                    <h1>🏦 Справочник банков</h1>
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
                        <button onClick={handleAddBank} className="add-bank-btn">
                            + Добавить банк
                        </button>
                    </div>
                </div>

                <div className="search-bar">
                    <input
                        type="text"
                        placeholder="🔍 Поиск банков..."
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

                <div className="banks-table-wrapper">
                    <table className="banks-table">
                        <thead>
                        <tr>
                            <th className="sortable-header" onClick={toggleSortOrder}>
                                Название банка
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
                                    {searchTerm ? 'Ничего не найдено' : 'Нет банков. Добавьте первый или импортируйте из Excel!'}
                                </td>
                            </tr>
                        ) : (
                            currentItems.map((bank) => (
                                <tr key={bank.id}>
                                    <td className="bank-name">{bank.name}</td>
                                    <td className="actions">
                                        <button
                                            onClick={() => handleEditBank(bank)}
                                            className="edit-btn"
                                            title="Редактировать"
                                        >
                                            ✏️
                                        </button>
                                        <button
                                            onClick={() => handleDeleteBank(bank.id, bank.name)}
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

                <BankModal
                    isOpen={modalOpen}
                    onClose={() => {
                        setModalOpen(false);
                        setEditingBank(null);
                    }}
                    onSave={handleSaveBank}
                    initialName={editingBank?.name || ''}
                    isEditing={!!editingBank}
                />
            </div>
        </div>
    );
}

export default BanksPage;