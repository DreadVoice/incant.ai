import { cn } from '@/lib/utils'

import type { ChatMessage as ChatMessageModel } from './types'

interface ChatMessageProps {
  message: ChatMessageModel
}

export function ChatMessage({ message }: ChatMessageProps) {
  const isUser = message.role === 'user'
  const hasContent = message.content.length > 0

  if (!hasContent && message.streaming === true) {
    return null
  }

  return (
    <div className={cn('flex w-full', isUser ? 'justify-end' : 'justify-start')}>
      <div className={cn('flex max-w-[85%] flex-col gap-1', isUser ? 'items-end' : 'items-start')}>
        <div
          className={cn(
            'rounded-lg px-3 py-2 text-sm break-words whitespace-pre-wrap',
            isUser ? 'bg-primary text-primary-foreground' : 'bg-muted text-foreground',
            !hasContent && 'text-muted-foreground italic',
          )}
        >
          {hasContent ? message.content : 'The model returned an empty reply.'}
          {message.streaming === true ? (
            <span className="bg-foreground ml-0.5 inline-block h-3.5 w-1.5 animate-pulse align-middle" />
          ) : null}
        </div>
        {message.provider && message.model ? (
          <p className="text-muted-foreground px-1 text-xs">
            {message.provider} · {message.model}
          </p>
        ) : null}
      </div>
    </div>
  )
}
