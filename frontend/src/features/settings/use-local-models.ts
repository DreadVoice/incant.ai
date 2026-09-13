import { useCallback, useEffect, useState } from 'react'

import { fetchLocalModels } from './api'
import type { LocalModels } from './types'

export type LocalModelsState = 'loading' | 'ready' | 'failed'

export interface UseLocalModelsResult {
  models: LocalModels | null
  state: LocalModelsState
  markSelected: (model: string) => void
}

export function useLocalModels(): UseLocalModelsResult {
  const [models, setModels] = useState<LocalModels | null>(null)
  const [state, setState] = useState<LocalModelsState>('loading')

  useEffect(() => {
    let active = true

    fetchLocalModels()
      .then((loaded) => {
        if (!active) {
          return
        }
        setModels(loaded)
        setState('ready')
      })
      .catch(() => {
        if (active) {
          setState('failed')
        }
      })

    return () => {
      active = false
    }
  }, [])

  const markSelected = useCallback((model: string) => {
    setModels((current) => (current === null ? current : { ...current, selected: model }))
  }, [])

  return { models, state, markSelected }
}
