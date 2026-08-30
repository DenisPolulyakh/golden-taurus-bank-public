import { useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { InputGroup, InputGroupAddon, InputGroupButton, InputGroupInput } from '@/components/ui/input-group'

/** Поле пароля с глазиком — было на входе и на регистрации. */
export function PasswordInput({ id, name, value, onChange, placeholder, required, minLength }) {
    const [visible, setVisible] = useState(false)

    return (
        <InputGroup>
            <InputGroupInput
                id={id}
                name={name}
                type={visible ? 'text' : 'password'}
                value={value}
                onChange={onChange}
                placeholder={placeholder}
                required={required}
                minLength={minLength}
            />
            <InputGroupAddon align="inline-end">
                <InputGroupButton
                    size="icon-xs"
                    variant="ghost"
                    onClick={() => setVisible(!visible)}
                    aria-label={visible ? 'Скрыть пароль' : 'Показать пароль'}
                >
                    {visible ? <EyeOff /> : <Eye />}
                </InputGroupButton>
            </InputGroupAddon>
        </InputGroup>
    )
}
