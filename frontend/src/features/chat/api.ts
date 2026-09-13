import type { ChatReply, ChatRequest } from './types'

const CHAT_ENDPOINT = '/api/chat'

async function readErrorMessage(response: Response): Promise<string> {
  const fallback = `The server responded with status ${response.status}.`

  let body: unknown
  try {
    body = await response.json()
  } catch {
    return fallback
  }

  if (body !== null && typeof body === 'object') {
    const fields = body as Record<string, unknown>
    if (typeof fields.error === 'string') {
      return fields.error
    }
    if (typeof fields.detail === 'string') {
      return fields.detail
    }
  }

  return fallback
}

export async function sendChatMessage(request: ChatRequest): Promise<ChatReply> {
  const response = await fetch(CHAT_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw new Error(await readErrorMessage(response))
  }

  return (await response.json()) as ChatReply
}
