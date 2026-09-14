import { useCallback, useState } from 'react'

import { AppSidebar, type AppView } from '@/components/layout/app-sidebar'
import { ChatPanel } from '@/features/chat/chat-panel'
import { useChat } from '@/features/chat/use-chat'
import { useConversations } from '@/features/conversations/use-conversations'
import { useProviders } from '@/features/providers/use-providers'
import { SettingsScreen } from '@/features/settings/settings-screen'

function App() {
  const providers = useProviders()
  const conversations = useConversations()
  const [chosenView, setChosenView] = useState<AppView | null>(null)

  const onTurnComplete = useCallback(() => {
    void conversations.reload()
  }, [conversations])

  const chat = useChat({ onTurnComplete })

  const needsConfiguration =
    providers.report !== null && providers.report.providers.every((provider) => !provider.available)
  const view = chosenView ?? (needsConfiguration ? 'settings' : 'chat')

  return (
    <div className="flex h-svh">
      <AppSidebar
        view={view}
        conversations={conversations.items}
        activeConversationId={chat.conversationId}
        onSelect={setChosenView}
        onOpenConversation={(id) => {
          setChosenView('chat')
          void chat.open(id)
        }}
        onNewChat={() => {
          setChosenView('chat')
          chat.reset()
        }}
      />
      {view === 'chat' ? (
        <ChatPanel chat={chat} report={providers.report} status={providers.status} />
      ) : (
        <SettingsScreen
          onSaved={(saved) => {
            providers.setReport(saved)
            setChosenView('settings')
          }}
        />
      )}
    </div>
  )
}

export default App
