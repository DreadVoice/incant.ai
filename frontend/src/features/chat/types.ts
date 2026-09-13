export type ChatRole = 'user' | 'assistant'

export interface ChatTelemetry {
  iterations: number
  inputTokens: number
  outputTokens: number
  durationMillis: number
}

export interface ChatRequest {
  message: string
  conversationId?: number
  provider?: string
  model?: string
}

export interface ChatReply {
  conversationId: number
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
  streaming?: boolean
}
