import { useEffect, useRef } from 'react'

import { ProviderSelector } from '@/features/providers/provider-selector'
import { useProviderSelection } from '@/features/providers/use-provider-selection'

import { ChatComposer } from './chat-composer'
import { ChatMessage } from './chat-message'
import { SkillActivityPanel } from './skill-activity-panel'
import { useChat } from './use-chat'

export function ChatPanel() {
  const { messages, status, error, send } = useChat()
  const providers = useProviderSelection()
  const pending = status === 'sending'
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages, pending])

  return (
    <div className="flex h-svh">
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="border-b px-4 py-3">
          <div className="mx-auto flex max-w-3xl items-center gap-2">
            <img src="/favicon.svg" alt="" className="size-6" />
            <h1 className="text-sm font-semibold tracking-tight">Incant</h1>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto px-4 py-6">
          <div className="mx-auto flex max-w-3xl flex-col gap-4">
            {messages.length === 0 ? (
              <p className="text-muted-foreground py-16 text-center text-sm">
                Ask something to start the conversation.
              </p>
            ) : (
              messages.map((message) => <ChatMessage key={message.id} message={message} />)
            )}

            {pending ? (
              <p className="text-muted-foreground text-sm" role="status">
                Waiting for the model…
              </p>
            ) : null}

            <div ref={bottomRef} />
          </div>
        </main>

        <footer className="border-t px-4 py-4">
          <div className="mx-auto flex max-w-3xl flex-col gap-2">
            {error ? (
              <p
                role="alert"
                className="border-destructive/40 bg-destructive/10 text-destructive rounded-md border px-3 py-2 text-sm"
              >
                {error}
              </p>
            ) : null}
            <ChatComposer
              pending={pending}
              onSend={(message) => void send(message, providers.selection)}
            />
            <ProviderSelector
              report={providers.report}
              status={providers.status}
              provider={providers.provider}
              model={providers.model}
              disabled={pending}
              onProviderChange={providers.selectProvider}
              onModelChange={providers.setModel}
            />
          </div>
        </footer>
      </div>

      <SkillActivityPanel messages={messages} />
    </div>
  )
}
