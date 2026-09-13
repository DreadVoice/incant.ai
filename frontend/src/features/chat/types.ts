export type ChatRole = 'user' | 'assistant'

export interface ChatTelemetry {
  iterations: number
  inputTokens: number
  outputTokens: number
  durationMillis: number
}

export interface ChatRequest {
  message: string
}

export interface ChatReply {
  reply: string | null
  provider: string
  model: string
  telemetry: ChatTelemetry
}

export interface ChatMessage {
  id: string
  role: ChatRole
  content: string
  provider?: string
  model?: string
}
