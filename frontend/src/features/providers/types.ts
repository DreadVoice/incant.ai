export interface ProviderStatus {
  name: string
  available: boolean
  model: string
  detail: string
}

export interface ProviderReport {
  defaultProvider: string
  providers: ProviderStatus[]
}

export interface ProviderSelection {
  provider?: string
  model?: string
}
