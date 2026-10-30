// src/components/layout/Sidebar.tsx
import { LayoutDashboard, Settings, Wallet } from 'lucide-react';

interface SidebarProps {
  activeView: string;
  onViewChange: (view: string) => void;
}

const navItems = [
  { id: 'dashboard', icon: LayoutDashboard, label: 'Dashboard' },
  { id: 'paper', icon: Wallet, label: 'Paper Portfolio' },
  { id: 'settings', icon: Settings, label: 'Settings' },
];

export function Sidebar({ activeView, onViewChange }: SidebarProps) {
  return (
    <aside className="fixed left-0 top-14 h-[calc(100vh-3.5rem)] w-16 border-r border-[hsl(var(--border))] bg-[hsl(var(--card))] z-10">
      <nav className="flex flex-col gap-1 p-2">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeView === item.id;
          
          return (
            <button
              key={item.id}
              onClick={() => onViewChange(item.id)}
              className={`
                group relative flex h-12 w-12 items-center justify-center rounded-lg
                transition-colors
                ${
                  isActive
                    ? 'bg-[hsl(var(--primary))] text-[hsl(var(--primary-foreground))]'
                    : 'text-[hsl(var(--muted-foreground))] hover:bg-[hsl(var(--accent))] hover:text-[hsl(var(--accent-foreground))]'
                }
              `}
              title={item.label}
            >
              <Icon className="h-5 w-5" />
              
              {/* Tooltip */}
              <span className="absolute left-full ml-2 px-2 py-1 bg-[hsl(var(--popover))] text-[hsl(var(--popover-foreground))] text-xs rounded opacity-0 group-hover:opacity-100 transition-opacity whitespace-nowrap pointer-events-none z-50 border border-[hsl(var(--border))]">
                {item.label}
              </span>
            </button>
          );
        })}
      </nav>
    </aside>
  );
}