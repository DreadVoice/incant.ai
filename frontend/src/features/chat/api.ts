import type { ChatReply, ChatRequest } from './types'

const CHAT_STREAM_ENDPOINT = '/api/chat/stream'

export interface ChatStreamHandlers {
  onToken: (text: string) => void
  onSkill: (name: string) => void
  onDone: (reply: ChatReply) => void
}

interface StreamFrame {
  event: string
  data: string
}

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

function parseFrame(frame: string): StreamFrame {
  let event = 'message'
  const data: string[] = []

  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length).trim()
    } else if (line.startsWith('data:')) {
      data.push(line.slice('data:'.length).replace(/^ /, ''))
    }
  }

  return { event, data: data.join('\n') }
}

function handleFrame(frame: StreamFrame, handlers: ChatStreamHandlers): void {
  if (frame.data === '') {
    return
  }

  const payload = JSON.parse(frame.data) as Record<string, unknown>

  switch (frame.event) {
    case 'token':
      if (typeof payload.text === 'string') {
        handlers.onToken(payload.text)
      }
      return
    case 'skill':
      if (typeof payload.name === 'string') {
        handlers.onSkill(payload.name)
      }
      return
    case 'done':
      handlers.onDone(payload as unknown as ChatReply)
      return
    case 'error':
      throw new Error(
        typeof payload.error === 'string' ? payload.error : 'The model could not complete the answer.',
      )
    default:
      return
  }
}

export async function streamChatMessage(
  request: ChatRequest,
  handlers: ChatStreamHandlers,
): Promise<void> {
  const response = await fetch(CHAT_STREAM_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(request),
  })

  if (!response.ok) {
    throw new Error(await readErrorMessage(response))
  }
  if (response.body === null) {
    throw new Error('The server returned an empty stream.')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  for (;;) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }

    buffer += decoder.decode(value, { stream: true })

    let boundary = buffer.indexOf('\n\n')
    while (boundary !== -1) {
      const frame = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      handleFrame(parseFrame(frame), handlers)
      boundary = buffer.indexOf('\n\n')
    }
  }
}
