import { useCallback, useEffect, useState } from 'react'

import { fetchConversations } from './api'
import type { ConversationSummary } from './types'

export interface UseConversationsResult {
  items: ConversationSummary[]
  reload: () => Promise<void>
}

export function useConversations(): UseConversationsResult {
  const [items, setItems] = useState<ConversationSummary[]>([])

  const reload = useCallback(async () => {
    try {
      setItems(await fetchConversations())
    } catch {
      setItems([])
    }
  }, [])

  useEffect(() => {
    let active = true

    fetchConversations()
      .then((loaded) => {
        if (active) {
          setItems(loaded)
        }
      })
      .catch(() => {
        if (active) {
          setItems([])
        }
      })

    return () => {
      active = false
    }
  }, [])

  return { items, reload }
}
