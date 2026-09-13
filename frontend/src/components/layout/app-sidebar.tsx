import { MessageSquare, PanelLeft, Settings } from 'lucide-react'
import { useState, type ComponentType } from 'react'

import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export type AppView = 'chat' | 'settings'

interface AppSidebarProps {
  view: AppView
  onSelect: (view: AppView) => void
}

interface SidebarItemProps {
  icon: ComponentType<{ className?: string }>
  label: string
  active: boolean
  collapsed: boolean
  onSelect: () => void
}

function SidebarItem({ icon: Icon, label, active, collapsed, onSelect }: SidebarItemProps) {
  return (
    <Button
      type="button"
      variant={active ? 'secondary' : 'ghost'}
      size={collapsed ? 'icon' : 'default'}
      onClick={onSelect}
      aria-label={label}
      aria-current={active ? 'page' : undefined}
      className={cn('shrink-0', collapsed ? '' : 'w-full justify-start font-normal')}
    >
      <Icon className="size-4" />
      {collapsed ? null : label}
    </Button>
  )
}

export function AppSidebar({ view, onSelect }: AppSidebarProps) {
  const [collapsed, setCollapsed] = useState(true)

  return (
    <nav
      aria-label="Sections"
      className={cn('flex shrink-0 flex-col gap-1 border-r p-2', collapsed ? 'w-14' : 'w-52')}
    >
      <Button
        type="button"
        variant="ghost"
        size={collapsed ? 'icon' : 'default'}
        onClick={() => setCollapsed((current) => !current)}
        aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        aria-expanded={!collapsed}
        className={cn('shrink-0', collapsed ? '' : 'w-full justify-start font-normal')}
      >
        <PanelLeft className="size-4" />
        {collapsed ? null : 'Collapse'}
      </Button>

      <SidebarItem
        icon={MessageSquare}
        label="Chat"
        active={view === 'chat'}
        collapsed={collapsed}
        onSelect={() => onSelect('chat')}
      />
      <SidebarItem
        icon={Settings}
        label="Settings"
        active={view === 'settings'}
        collapsed={collapsed}
        onSelect={() => onSelect('settings')}
      />
    </nav>
  )
}
