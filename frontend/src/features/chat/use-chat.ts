import { useCallback, useState } from 'react'

import type { ProviderSelection } from '@/features/providers/types'

import { sendChatMessage } from './api'
import type { ChatMessage } from './types'

export type ChatStatus = 'idle' | 'sending'

let lastMessageId = 0

function createMessageId(): string {
  lastMessageId += 1
  return `message-${lastMessageId}`
}

export interface UseChatResult {
  messages: ChatMessage[]
  status: ChatStatus
  error: string | null
  send: (input: string, selection: ProviderSelection) => Promise<void>
}

export function useChat(): UseChatResult {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [status, setStatus] = useState<ChatStatus>('idle')
  const [error, setError] = useState<string | null>(null)

  const send = useCallback(async (input: string, selection: ProviderSelection) => {
    const content = input.trim()
    if (content.length === 0) {
      return
    }

    setMessages((current) => [
      ...current,
      { id: createMessageId(), role: 'user', content },
    ])
    setStatus('sending')
    setError(null)

    try {
      const reply = await sendChatMessage({
        message: content,
        provider: selection.provider,
        model: selection.model,
      })
      setMessages((current) => [
        ...current,
        {
          id: createMessageId(),
          role: 'assistant',
          content: reply.reply ?? '',
          provider: reply.provider,
          model: reply.model,
        },
      ])
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'The request could not be completed.')
    } finally {
      setStatus('idle')
    }
  }, [])

  return { messages, status, error, send }
}
