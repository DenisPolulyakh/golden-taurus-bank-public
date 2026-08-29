import { useRef, useState } from 'react'
import { toast } from 'sonner'
import api from '@/api/axios'

/**
 * Импорт и экспорт справочника в Excel — одинаковые ~80 строк были и в банках,
 * и в наименованиях.
 *
 * Тост об ошибке импорта показывает перехватчик в api/axios.js. Раньше
 * наименования дополнительно звали toast.error сами, из-за чего на одну ошибку
 * прилетало два тоста; теперь тост один.
 */
export function useExcelPort({ basePath, fileName, onImported, exportErrorText }) {
    const [importing, setImporting] = useState(false)
    const fileInputRef = useRef(null)

    const handleExport = async () => {
        try {
            const response = await api.get(`${basePath}/export`, {
                responseType: 'blob',
                // тело ответа — blob, message не прочитать, показываем свой тост
                _skipErrorToast: true,
            })

            const url = window.URL.createObjectURL(new Blob([response.data]))
            const link = document.createElement('a')
            link.href = url
            link.setAttribute('download', fileName)
            document.body.appendChild(link)
            link.click()
            link.remove()
            window.URL.revokeObjectURL(url)
        } catch (err) {
            console.error('Ошибка экспорта:', err)
            toast.error(exportErrorText)
        }
    }

    const handleImportClick = () => fileInputRef.current?.click()

    const handleFileChange = async (event) => {
        const file = event.target.files[0]
        if (!file) return

        const fileExt = file.name.split('.').pop().toLowerCase()
        if (!['xlsx', 'xls'].includes(fileExt)) {
            toast.error('Пожалуйста, выберите файл с расширением .xlsx или .xls')
            return
        }

        const formData = new FormData()
        formData.append('file', file)

        setImporting(true)

        try {
            const response = await api.post(`${basePath}/import`, formData, {
                headers: { 'Content-Type': 'multipart/form-data' },
            })

            const result = response.data.data
            const errors = result.errors || []

            let description = `Добавлено: ${result.added}, пропущено (дубликаты): ${result.skipped}`
            if (errors.length > 0) {
                description += `\nОшибки (${errors.length}): ${errors.slice(0, 5).join('; ')}`
                if (errors.length > 5) {
                    description += ` ...и еще ${errors.length - 5}`
                }
                toast.warning('Импорт завершён с ошибками', { description })
            } else {
                toast.success('Импорт завершён', { description })
            }

            await onImported?.()
        } catch (err) {
            console.error('Ошибка импорта:', err)
        } finally {
            setImporting(false)
            // Очищаем input, чтобы можно было загрузить тот же файл повторно
            if (fileInputRef.current) {
                fileInputRef.current.value = ''
            }
        }
    }

    /** Скрытый input — рендерится на странице как <input {...fileInputProps} /> */
    const fileInputProps = {
        type: 'file',
        ref: fileInputRef,
        onChange: handleFileChange,
        accept: '.xlsx,.xls',
        className: 'hidden',
    }

    return { importing, handleExport, handleImportClick, fileInputProps }
}
