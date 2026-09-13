import { useCallback, useEffect, useState } from 'react'

import { fetchProviders } from './api'
import type { ProviderReport, ProviderSelection } from './types'

export type ProviderStatusState = 'loading' | 'ready' | 'failed'

export interface UseProviderSelectionResult {
  report: ProviderReport | null
  status: ProviderStatusState
  provider: string
  model: string
  selection: ProviderSelection
  selectProvider: (name: string) => void
  setModel: (value: string) => void
}

function configuredModel(report: ProviderReport, name: string): string {
  return report.providers.find((provider) => provider.name === name)?.model ?? ''
}

export function useProviderSelection(): UseProviderSelectionResult {
  const [report, setReport] = useState<ProviderReport | null>(null)
  const [status, setStatus] = useState<ProviderStatusState>('loading')
  const [provider, setProvider] = useState('')
  const [model, setModel] = useState('')

  useEffect(() => {
    let active = true

    fetchProviders()
      .then((loaded) => {
        if (!active) {
          return
        }
        setReport(loaded)
        setProvider(loaded.defaultProvider)
        setModel(configuredModel(loaded, loaded.defaultProvider))
        setStatus('ready')
      })
      .catch(() => {
        if (active) {
          setStatus('failed')
        }
      })

    return () => {
      active = false
    }
  }, [])

  const selectProvider = useCallback(
    (name: string) => {
      setProvider(name)
      setModel(report === null ? '' : configuredModel(report, name))
    },
    [report],
  )

  return {
    report,
    status,
    provider,
    model,
    selection: {
      provider: provider === '' ? undefined : provider,
      model: model.trim() === '' ? undefined : model.trim(),
    },
    selectProvider,
    setModel,
  }
}
