import type { ProviderReport } from '@/features/providers/types'

const API_KEYS_ENDPOINT = '/api/config/api-keys'

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
