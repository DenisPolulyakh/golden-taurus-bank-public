import { useState } from 'react'
import { Check, ChevronsUpDown, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
    Command,
    CommandEmpty,
    CommandGroup,
    CommandInput,
    CommandItem,
    CommandList,
} from '@/components/ui/command'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { cn } from '@/lib/utils'

/**
 * Выпадающий список с поиском — замена react-select.
 *
 * options: [{ value, label, keywords?, data? }]
 * renderOption(option) — как рисовать строку списка (у переводов это три
 * колонки: наименование, хранилище, сумма).
 */
export function Combobox({
    options = [],
    value,
    onChange,
    placeholder = 'Выберите...',
    searchPlaceholder = 'Поиск...',
    emptyText = 'Ничего не найдено',
    clearable = false,
    disabled = false,
    className,
    contentClassName,
    renderOption,
    renderValue,
}) {
    const [open, setOpen] = useState(false)
    const selected = options.find((option) => String(option.value) === String(value)) || null

    return (
        <Popover open={open} onOpenChange={setOpen}>
            <PopoverTrigger asChild>
                <Button
                    type="button"
                    variant="outline"
                    role="combobox"
                    aria-expanded={open}
                    disabled={disabled}
                    className={cn(
                        'w-full justify-between font-normal',
                        !selected && 'text-muted-foreground',
                        className
                    )}
                >
                    <span className="truncate">
                        {selected
                            ? renderValue
                                ? renderValue(selected)
                                : selected.label
                            : placeholder}
                    </span>
                    <span className="flex items-center gap-1">
                        {clearable && selected && (
                            <X
                                className="size-4 opacity-50 hover:opacity-100"
                                role="button"
                                aria-label="Очистить"
                                onClick={(event) => {
                                    event.stopPropagation()
                                    onChange(null, null)
                                }}
                            />
                        )}
                        <ChevronsUpDown className="size-4 opacity-50" />
                    </span>
                </Button>
            </PopoverTrigger>
            <PopoverContent
                className={cn('w-(--radix-popover-trigger-width) p-0', contentClassName)}
                align="start"
            >
                <Command
                    filter={(itemValue, search) =>
                        itemValue.toLowerCase().includes(search.toLowerCase()) ? 1 : 0
                    }
                >
                    <CommandInput placeholder={searchPlaceholder} />
                    <CommandList>
                        <CommandEmpty>{emptyText}</CommandEmpty>
                        <CommandGroup>
                            {options.map((option) => (
                                <CommandItem
                                    key={option.value}
                                    value={`${option.label} ${option.keywords || ''}`}
                                    onSelect={() => {
                                        onChange(option.value, option)
                                        setOpen(false)
                                    }}
                                >
                                    <Check
                                        className={cn(
                                            'size-4',
                                            String(option.value) === String(value)
                                                ? 'opacity-100'
                                                : 'opacity-0'
                                        )}
                                    />
                                    <span className="min-w-0 flex-1">
                                        {renderOption ? renderOption(option) : option.label}
                                    </span>
                                </CommandItem>
                            ))}
                        </CommandGroup>
                    </CommandList>
                </Command>
            </PopoverContent>
        </Popover>
    )
}
