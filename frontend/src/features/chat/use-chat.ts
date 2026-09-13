import { useCallback, useState } from 'react'

import type { ProviderSelection } from '@/features/providers/types'

import { streamChatMessage } from './api'
import type { ChatMessage } from './types'

export type ChatStatus = 'idle' | 'sending' | 'streaming'

export interface UseChatResult {
  messages: ChatMessage[]
  status: ChatStatus
  error: string | null
  send: (input: string, selection: ProviderSelection) => Promise<void>
}

let lastMessageId = 0

function createMessageId(): string {
  lastMessageId += 1
  return `message-${lastMessageId}`
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

    const replyId = createMessageId()
    const update = (change: (message: ChatMessage) => ChatMessage) => {
      setMessages((current) =>
        current.map((message) => (message.id === replyId ? change(message) : message)),
      )
    }

    setMessages((current) => [
      ...current,
      { id: createMessageId(), role: 'user', content },
      { id: replyId, role: 'assistant', content: '', streaming: true },
    ])
    setStatus('sending')
    setError(null)

    try {
      await streamChatMessage(
        { message: content, provider: selection.provider, model: selection.model },
        {
          onToken: (text) => {
            setStatus('streaming')
            update((message) => ({ ...message, content: message.content + text }))
          },
          onSkill: (name) => {
            update((message) => ({ ...message, skills: [...(message.skills ?? []), name] }))
          },
          onDone: (reply) => {
            update((message) => ({
              ...message,
              content: reply.reply ?? message.content,
              provider: reply.provider,
              model: reply.model,
              skills: reply.skills,
              streaming: false,
            }))
          },
        },
      )
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'The request could not be completed.')
      setMessages((current) =>
        current.filter((message) => message.id !== replyId || message.content.length > 0),
      )
      update((message) => ({ ...message, streaming: false }))
    } finally {
      setStatus('idle')
    }
  }, [])

  return { messages, status, error, send }
}
