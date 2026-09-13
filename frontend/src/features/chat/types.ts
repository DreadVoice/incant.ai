export type ChatRole = 'user' | 'assistant'

export interface ChatTelemetry {
  iterations: number
  inputTokens: number
  outputTokens: number
  durationMillis: number
}

export interface ChatRequest {
  message: string
  provider?: string
  model?: string
}

export interface ChatReply {
  reply: string | null
  provider: string
  model: string
  skills: string[]
  telemetry: ChatTelemetry
}

export interface ChatMessage {
  id: string
  role: ChatRole
  content: string
  provider?: string
  model?: string
  skills?: string[]
}
