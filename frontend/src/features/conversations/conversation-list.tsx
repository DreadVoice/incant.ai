import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

import type { ConversationSummary } from './types'

interface ConversationListProps {
  items: ConversationSummary[]
  activeId: number | null
  onOpen: (id: number) => void
}

export function ConversationList({ items, activeId, onOpen }: ConversationListProps) {
  return (
    <div className="flex min-h-0 flex-1 flex-col gap-1">
      <p className="text-muted-foreground px-2 pt-2 text-xs font-medium">Conversations</p>

      {items.length === 0 ? (
        <p className="text-muted-foreground px-2 text-xs">Nothing saved yet.</p>
      ) : (
        <ul className="flex min-h-0 flex-1 flex-col gap-0.5 overflow-y-auto">
          {items.map((conversation) => (
            <li key={conversation.id}>
              <Button
                type="button"
                variant={conversation.id === activeId ? 'secondary' : 'ghost'}
                onClick={() => onOpen(conversation.id)}
                aria-current={conversation.id === activeId ? 'true' : undefined}
                className={cn('h-8 w-full justify-start px-2 text-xs font-normal')}
              >
                <span className="truncate">{conversation.title}</span>
              </Button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
