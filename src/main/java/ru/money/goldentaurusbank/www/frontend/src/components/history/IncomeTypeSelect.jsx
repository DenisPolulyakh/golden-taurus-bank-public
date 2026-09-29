import { toast } from 'sonner'
import api from '@/api/axios'
import { Badge } from '@/components/ui/badge'
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@/components/ui/select'

const NO_INCOME_TYPE = 'NONE'

export const IncomeTypeSelect = ({ transaction, incomeTypes, onChanged }) => {
    if (transaction.kind !== 'DEPOSIT') {
        return null
    }

    if (!transaction.canChangeIncomeType) {
        if (!transaction.incomeType) {
            return null
        }
        const label =
            incomeTypes.find((option) => option.code === transaction.incomeType)?.displayName ||
            transaction.incomeType
        return <Badge variant="outline">{label}</Badge>
    }

    const handleChange = async (value) => {
        const incomeType = value === NO_INCOME_TYPE ? null : value
        try {
            await api.patch(`/transactions/${transaction.id}/income-type`, { incomeType })
            onChanged(incomeType)
            toast.success('Тип дохода обновлён')
        } catch (err) {
            console.error('Ошибка обновления типа дохода:', err)
        }
    }

    return (
        <Select value={transaction.incomeType || NO_INCOME_TYPE} onValueChange={handleChange}>
            <SelectTrigger size="sm" className="h-6 w-auto gap-1 border-dashed px-2 text-xs">
                <SelectValue placeholder="Тип дохода" />
            </SelectTrigger>
            <SelectContent>
                <SelectItem value={NO_INCOME_TYPE}>Без классификации</SelectItem>
                {incomeTypes.map((option) => (
                    <SelectItem key={option.code} value={option.code}>
                        {option.displayName}
                    </SelectItem>
                ))}
            </SelectContent>
        </Select>
    )
}

