import { useCallback, useEffect, useState } from 'react'

import { fetchProviders } from './api'
import type { ProviderReport } from './types'

export type ProviderStatusState = 'loading' | 'ready' | 'failed'

export interface UseProvidersResult {
  report: ProviderReport | null
  status: ProviderStatusState
  setReport: (report: ProviderReport) => void
  reload: () => Promise<void>
}

export function useProviders(): UseProvidersResult {
  const [report, setReport] = useState<ProviderReport | null>(null)
  const [status, setStatus] = useState<ProviderStatusState>('loading')

  const reload = useCallback(async () => {
    try {
      setReport(await fetchProviders())
      setStatus('ready')
    } catch {
      setStatus('failed')
    }
  }, [])

  useEffect(() => {
    let active = true

    fetchProviders()
      .then((loaded) => {
        if (!active) {
          return
        }
        setReport(loaded)
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

  const replace = useCallback((updated: ProviderReport) => {
    setReport(updated)
    setStatus('ready')
  }, [])

  return { report, status, setReport: replace, reload }
}
