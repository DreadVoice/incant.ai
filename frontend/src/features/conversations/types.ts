export interface ConversationSummary {
  id: number
  title: string
  provider: string
  model: string
  updatedAt: string
  messageCount: number
}

export interface StoredMessage {
  id: number
  role: 'user' | 'assistant'
  content: string
  skills: string[]
  createdAt: string
}

export interface ConversationDetail {
  id: number
  title: string
  provider: string
  model: string
  messages: StoredMessage[]
}
