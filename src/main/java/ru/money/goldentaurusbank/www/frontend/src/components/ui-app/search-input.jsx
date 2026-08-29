import { Search, X } from 'lucide-react'
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from '@/components/ui/input-group'
import { cn } from '@/lib/utils'

/** Поле поиска с лупой и крестиком — бывший `.search-bar input`. */
export function SearchInput({ value, onChange, placeholder = 'Поиск...', className }) {
    return (
        <InputGroup className={cn('max-w-md', className)}>
            <InputGroupAddon>
                <Search />
            </InputGroupAddon>
            <InputGroupInput
                value={value}
                onChange={(e) => onChange(e.target.value)}
                placeholder={placeholder}
            />
            {value && (
                <InputGroupAddon align="inline-end">
                    <InputGroupButton
                        size="icon-xs"
                        variant="ghost"
                        onClick={() => onChange('')}
                        aria-label="Очистить поиск"
                    >
                        <X />
                    </InputGroupButton>
                </InputGroupAddon>
            )}
        </InputGroup>
    )
}
