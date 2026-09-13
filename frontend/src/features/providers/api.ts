import type { ProviderReport } from './types'

const PROVIDERS_ENDPOINT = '/api/providers'

export async function fetchProviders(): Promise<ProviderReport> {
  const response = await fetch(PROVIDERS_ENDPOINT)

  if (!response.ok) {
    throw new Error(`The server responded with status ${response.status}.`)
  }

  return (await response.json()) as ProviderReport
}
