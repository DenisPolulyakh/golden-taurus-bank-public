import { Link, NavLink } from 'react-router-dom'
import { ChartColumnBig, Coins, FolderTree, History, Landmark, LogOut, Settings, Vault, Wallet } from 'lucide-react'
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
import { cn } from '@/lib/utils'

const NAV = [
    { to: '/bullions', label: 'Слитки', icon: Coins },
    { to: '/vaults', label: 'Хранилища', icon: Vault },
    { to: '/credit-cards', label: 'Кредитные карты', icon: Wallet },
    { to: '/transactions', label: 'История', icon: History },
    { to: '/reports', label: 'Отчёты', icon: ChartColumnBig },
]

/** Справочники живут под шестерёнкой: заходят в них раз в месяц. */
const REFERENCES = [
    { to: '/bullion-names', label: 'Наименования', icon: FolderTree },
    { to: '/banks', label: 'Банки', icon: Landmark },
]

export function AppHeader({ onLogout }) {
    return (
        <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur-md">
            {/* Тонкая фирменная полоса вместо серой линии сверху */}
            <div className="brand-gradient h-0.5 w-full" />
            <div className="mx-auto flex w-full max-w-7xl items-center gap-3 px-4 py-3 sm:px-6 lg:px-8">
                <Link to="/dashboard" className="flex items-center gap-2 font-semibold">
                    <span className="brand-gradient flex size-8 items-center justify-center rounded-lg text-white shadow-sm">
                        <Landmark className="size-4.5" />
                    </span>
                    <span className="brand-text hidden sm:inline">Твой личный банк</span>
                </Link>

                <Separator orientation="vertical" className="mx-1 hidden h-6 md:block" />

                <nav className="flex min-w-0 flex-1 items-center gap-1 overflow-x-auto">
                    {NAV.map(({ to, label, icon: Icon }) => (
                        <Button key={to} variant="ghost" size="sm" asChild>
                            <NavLink
                                to={to}
                                className={({ isActive }) =>
                                    cn(isActive && 'bg-primary/10 text-primary hover:bg-primary/15')
                                }
                            >
                                <Icon />
                                <span className="hidden lg:inline">{label}</span>
                            </NavLink>
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
