import { ArrowUp, LoaderCircle } from 'lucide-react'
import { useState, type FormEvent, type KeyboardEvent } from 'react'

import { Button } from '@/components/ui/button'
import { Textarea } from '@/components/ui/textarea'

interface ChatComposerProps {
  pending: boolean
  onSend: (message: string) => void
}

export function ChatComposer({ pending, onSend }: ChatComposerProps) {
  const [value, setValue] = useState('')
  const canSend = value.trim().length > 0 && !pending

  function submit() {
    if (!canSend) {
      return
    }
    onSend(value)
    setValue('')
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    submit()
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submit()
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex items-end gap-2">
      <Textarea
        value={value}
        onChange={(event) => setValue(event.target.value)}
        onKeyDown={handleKeyDown}
        disabled={pending}
        rows={1}
        placeholder="Send a message"
        aria-label="Message"
        className="max-h-48 min-h-11 resize-none"
      />
      <Button type="submit" size="icon" disabled={!canSend} aria-label="Send message">
        {pending ? <LoaderCircle className="animate-spin" /> : <ArrowUp />}
      </Button>
    </form>
  )
}
