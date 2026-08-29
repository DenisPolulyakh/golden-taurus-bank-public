import { Link } from 'react-router-dom'
import { Coins, FolderTree, History, Landmark, LogOut, Settings, Vault, Wallet } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuLabel,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Separator } from '@/components/ui/separator'

const NAV = [
    { to: '/bullions', label: 'Слитки', icon: Coins },
    { to: '/vaults', label: 'Хранилища', icon: Vault },
    { to: '/credit-cards', label: 'Кредитные карты', icon: Wallet },
    { to: '/transactions', label: 'История', icon: History },
]

/** Справочники живут под шестерёнкой: заходят в них раз в месяц. */
const REFERENCES = [
    { to: '/bullion-names', label: 'Наименования', icon: FolderTree },
    { to: '/banks', label: 'Банки', icon: Landmark },
]

export function AppHeader({ onLogout }) {
    return (
        <header className="sticky top-0 z-40 border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
            <div className="mx-auto flex w-full max-w-7xl items-center gap-3 px-4 py-3 sm:px-6 lg:px-8">
                <Link to="/dashboard" className="flex items-center gap-2 font-semibold">
                    <Landmark className="size-5 text-primary" />
                    <span className="hidden sm:inline">Твой личный банк</span>
                </Link>

                <Separator orientation="vertical" className="mx-1 hidden h-6 md:block" />

                <nav className="flex min-w-0 flex-1 items-center gap-1 overflow-x-auto">
                    {NAV.map(({ to, label, icon: Icon }) => (
                        <Button key={to} variant="ghost" size="sm" asChild>
                            <Link to={to}>
                                <Icon />
                                <span className="hidden lg:inline">{label}</span>
                            </Link>
                        </Button>
                    ))}
                </nav>

                <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                        <Button variant="ghost" size="icon" aria-label="Справочники">
                            <Settings />
                        </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                        <DropdownMenuLabel>Справочники</DropdownMenuLabel>
                        <DropdownMenuSeparator />
                        {REFERENCES.map(({ to, label, icon: Icon }) => (
                            <DropdownMenuItem key={to} asChild>
                                <Link to={to}>
                                    <Icon />
                                    {label}
                                </Link>
                            </DropdownMenuItem>
                        ))}
                    </DropdownMenuContent>
                </DropdownMenu>

                <Button variant="outline" size="sm" onClick={onLogout}>
                    <LogOut />
                    <span className="hidden sm:inline">Выйти</span>
                </Button>
            </div>
        </header>
    )
}
