import { useCallback, useState } from 'react'

import type { ProviderReport, ProviderSelection } from './types'

export interface UseProviderSelectionResult {
  provider: string
  model: string
  selection: ProviderSelection
  selectProvider: (name: string) => void
  setModel: (value: string) => void
}

interface Choice {
  provider: string
  model: string
}

function configuredModel(report: ProviderReport | null, name: string): string {
  return report?.providers.find((provider) => provider.name === name)?.model ?? ''
}

export function useProviderSelection(report: ProviderReport | null): UseProviderSelectionResult {
  const [choice, setChoice] = useState<Choice | null>(null)

  const provider = choice?.provider ?? report?.defaultProvider ?? ''
  const model = choice?.model ?? configuredModel(report, provider)

  const selectProvider = useCallback(
    (name: string) => {
      setChoice({ provider: name, model: configuredModel(report, name) })
    },
    [report],
  )

  const setModel = useCallback(
    (value: string) => {
      setChoice((current) => ({ provider: current?.provider ?? report?.defaultProvider ?? '', model: value }))
    },
    [report],
  )

  return {
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
