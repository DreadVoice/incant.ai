import { MessageSquarePlus, PanelLeft, Settings } from 'lucide-react'
import { useState, type ComponentType } from 'react'

import { Button } from '@/components/ui/button'
import { ConversationList } from '@/features/conversations/conversation-list'
import type { ConversationSummary } from '@/features/conversations/types'
import { cn } from '@/lib/utils'

export type AppView = 'chat' | 'settings'

interface AppSidebarProps {
  view: AppView
  conversations: ConversationSummary[]
  activeConversationId: number | null
  onSelect: (view: AppView) => void
  onOpenConversation: (id: number) => void
  onNewChat: () => void
}

interface SidebarButtonProps {
  icon: ComponentType<{ className?: string }>
  label: string
  active?: boolean
  collapsed: boolean
  onSelect: () => void
}

function SidebarButton({ icon: Icon, label, active = false, collapsed, onSelect }: SidebarButtonProps) {
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

export function AppSidebar({
  view,
  conversations,
  activeConversationId,
  onSelect,
  onOpenConversation,
  onNewChat,
}: AppSidebarProps) {
  const [collapsed, setCollapsed] = useState(true)

  return (
    <nav
      aria-label="Sections"
      className={cn('flex min-h-0 shrink-0 flex-col gap-1 border-r p-2', collapsed ? 'w-14' : 'w-64')}
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

      <SidebarButton
        icon={MessageSquarePlus}
        label="New chat"
        collapsed={collapsed}
        onSelect={onNewChat}
      />

      {collapsed ? null : (
        <ConversationList
          items={conversations}
          activeId={activeConversationId}
          onOpen={onOpenConversation}
        />
      )}

      <SidebarButton
        icon={Settings}
        label="Settings"
        active={view === 'settings'}
        collapsed={collapsed}
        onSelect={() => onSelect('settings')}
      />
    </nav>
  )
}
