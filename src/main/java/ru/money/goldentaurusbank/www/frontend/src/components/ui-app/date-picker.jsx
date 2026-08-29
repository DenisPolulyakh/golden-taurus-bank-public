import { useState } from 'react'
import { format, isValid, parseISO } from 'date-fns'
import { ru } from 'date-fns/locale'
import { CalendarIcon, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'

/**
 * Выбор даты вместо `<input type="date">`. Наружу отдаёт ту же строку
 * `ГГГГ-ММ-ДД`, что уходила на бэкенд раньше, — формат запросов не меняется.
 */
export function DatePicker({
    value,
    onChange,
    placeholder = 'Выберите дату',
    id,
    disabled,
    clearable = false,
    className,
}) {
    const [open, setOpen] = useState(false)

    const parsed = value ? parseISO(value) : null
    const date = parsed && isValid(parsed) ? parsed : null

    return (
        <div className={cn('flex items-center gap-1', className)}>
            <Popover open={open} onOpenChange={setOpen}>
                <PopoverTrigger asChild>
                    <Button
                        id={id}
                        type="button"
                        variant="outline"
                        disabled={disabled}
                        className={cn(
                            'w-full justify-start font-normal',
                            !date && 'text-muted-foreground'
                        )}
                    >
                        <CalendarIcon />
                        {date ? format(date, 'd MMMM yyyy', { locale: ru }) : placeholder}
                    </Button>
                </PopoverTrigger>
                <PopoverContent className="w-auto p-0" align="start">
                    <Calendar
                        mode="single"
                        selected={date || undefined}
                        defaultMonth={date || undefined}
                        locale={ru}
                        captionLayout="dropdown"
                        onSelect={(selected) => {
                            onChange(selected ? format(selected, 'yyyy-MM-dd') : '')
                            setOpen(false)
                        }}
                    />
                </PopoverContent>
            </Popover>
            {clearable && value && (
                <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    onClick={() => onChange('')}
                    aria-label="Очистить дату"
                >
                    <X />
                </Button>
            )}
        </div>
    )
}
