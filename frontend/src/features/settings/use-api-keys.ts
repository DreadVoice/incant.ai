import { useCallback, useEffect, useState } from 'react'

import { fetchApiKeyStatuses } from './api'
import type { KeyStatus } from './types'

export type ApiKeysState = 'loading' | 'ready' | 'failed'

export interface UseApiKeysResult {
  statuses: KeyStatus[]
  state: ApiKeysState
  reload: () => Promise<void>
}

export function useApiKeys(): UseApiKeysResult {
  const [statuses, setStatuses] = useState<KeyStatus[]>([])
  const [state, setState] = useState<ApiKeysState>('loading')

  const reload = useCallback(async () => {
    try {
      setStatuses(await fetchApiKeyStatuses())
      setState('ready')
    } catch {
      setState('failed')
    }
  }, [])

  useEffect(() => {
    let active = true

    fetchApiKeyStatuses()
      .then((loaded) => {
        if (!active) {
          return
        }
        setStatuses(loaded)
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

  return { statuses, state, reload }
}
