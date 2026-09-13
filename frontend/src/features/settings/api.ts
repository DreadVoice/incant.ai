import type { ProviderReport } from '@/features/providers/types'

import type { LocalModels } from './types'

const API_KEYS_ENDPOINT = '/api/config/api-keys'
const LOCAL_MODELS_ENDPOINT = '/api/config/local-models'
const LOCAL_MODEL_ENDPOINT = '/api/config/local-model'

export async function updateApiKeys(apiKeys: Record<string, string>): Promise<ProviderReport> {
  const response = await fetch(API_KEYS_ENDPOINT, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ apiKeys }),
  })

  if (!response.ok) {
    let detail = `The server responded with status ${response.status}.`
    try {
      const body = (await response.json()) as Record<string, unknown>
      if (typeof body.error === 'string') {
        detail = body.error
      }
    } catch {
      detail = `The server responded with status ${response.status}.`
    }
    throw new Error(detail)
  }

  return (await response.json()) as ProviderReport
}

export async function fetchLocalModels(): Promise<LocalModels> {
  const response = await fetch(LOCAL_MODELS_ENDPOINT)

  if (!response.ok) {
    throw new Error(`The server responded with status ${response.status}.`)
  }

  return (await response.json()) as LocalModels
}

export async function updateLocalModel(model: string): Promise<ProviderReport> {
  const response = await fetch(LOCAL_MODEL_ENDPOINT, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ model }),
  })

  if (!response.ok) {
    let detail = `The server responded with status ${response.status}.`
    try {
      const body = (await response.json()) as Record<string, unknown>
      if (typeof body.error === 'string') {
        detail = body.error
      }
    } catch {
      detail = `The server responded with status ${response.status}.`
    }
    throw new Error(detail)
  }

  return (await response.json()) as ProviderReport
}
