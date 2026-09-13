import { PanelRight, PanelRightClose } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/components/ui/button'

import type { ChatMessage } from './types'

interface SkillActivityPanelProps {
  messages: ChatMessage[]
}

export function SkillActivityPanel({ messages }: SkillActivityPanelProps) {
  const [collapsed, setCollapsed] = useState(false)
  const turns = messages.filter((message) => message.role === 'assistant')

  if (collapsed) {
    return (
      <aside className="hidden w-12 shrink-0 flex-col items-center border-l p-2 lg:flex">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={() => setCollapsed(false)}
          aria-label="Expand skill activity"
          aria-expanded={false}
          className="size-8"
        >
          <PanelRight className="size-4" />
        </Button>
      </aside>
    )
  }

  return (
    <aside className="hidden w-72 shrink-0 flex-col border-l lg:flex">
      <div className="flex items-center justify-between gap-2 border-b px-4 py-3">
        <h2 className="text-sm font-semibold tracking-tight">Skill activity</h2>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          onClick={() => setCollapsed(true)}
          aria-label="Collapse skill activity"
          aria-expanded
          className="size-7"
        >
          <PanelRightClose className="size-4" />
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4">
        {turns.length === 0 ? (
          <p className="text-muted-foreground text-xs">
            Every answer is listed here with the skills it loaded.
          </p>
        ) : (
          <ol className="flex flex-col gap-3">
            {turns.map((turn, index) => {
              const skills = turn.skills ?? []

              return (
                <li key={turn.id} className="flex flex-col gap-1.5">
                  <span className="text-muted-foreground text-xs">Turn {index + 1}</span>
                  {skills.length > 0 ? (
                    <ul className="flex flex-wrap gap-1">
                      {skills.map((skill, position) => (
                        <li
                          key={`${turn.id}-${position}`}
                          className="bg-secondary text-secondary-foreground rounded-md px-2 py-0.5 text-xs"
                        >
                          {skill}
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <span className="text-muted-foreground text-xs">No skill loaded</span>
                  )}
                </li>
              )
            })}
          </ol>
        )}
      </div>
    </aside>
  )
}
