import { useState } from 'react'

import { AppSidebar, type AppView } from '@/components/layout/app-sidebar'
import { ChatPanel } from '@/features/chat/chat-panel'
import { useProviders } from '@/features/providers/use-providers'
import { SettingsScreen } from '@/features/settings/settings-screen'

function App() {
  const providers = useProviders()
  const [chosenView, setChosenView] = useState<AppView | null>(null)

  const needsConfiguration =
    providers.report !== null && providers.report.providers.every((provider) => !provider.available)
  const view = chosenView ?? (needsConfiguration ? 'settings' : 'chat')

  return (
    <div className="flex h-svh">
      <AppSidebar view={view} onSelect={setChosenView} />
      {view === 'chat' ? (
        <ChatPanel report={providers.report} status={providers.status} />
      ) : (
        <SettingsScreen
          report={providers.report}
          status={providers.status}
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
