export interface LocalModels {
  baseUrl: string
  reachable: boolean
  models: string[]
  selected: string
}

export interface KeyStatus {
  provider: string
  configured: boolean
  usable: boolean
}
