import type { ConversationDetail, ConversationSummary } from './types'

const CONVERSATIONS_ENDPOINT = '/api/conversations'

export async function fetchConversations(): Promise<ConversationSummary[]> {
  const response = await fetch(CONVERSATIONS_ENDPOINT)

  if (!response.ok) {
    throw new Error(`The server responded with status ${response.status}.`)
  }

  return (await response.json()) as ConversationSummary[]
}

export async function fetchConversation(id: number): Promise<ConversationDetail> {
  const response = await fetch(`${CONVERSATIONS_ENDPOINT}/${id}`)

  if (!response.ok) {
    throw new Error(`The conversation could not be opened (status ${response.status}).`)
  }

  return (await response.json()) as ConversationDetail
}
