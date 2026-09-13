import type { ChatMessage } from './types'

interface SkillActivityPanelProps {
  messages: ChatMessage[]
}

export function SkillActivityPanel({ messages }: SkillActivityPanelProps) {
  const turns = messages.filter((message) => message.role === 'assistant')

  return (
    <aside className="hidden w-72 shrink-0 flex-col border-l lg:flex">
      <div className="border-b px-4 py-3">
        <h2 className="text-sm font-semibold tracking-tight">Skill activity</h2>
      </div>

      <div className="flex-1 overflow-y-auto px-4 py-4">
        {turns.length === 0 ? (
          <p className="text-muted-foreground text-xs">
            Every answer is listed here with the skills it loaded.
          </p>
        ) : (
          <ol className="flex flex-col gap-3">
            {turns.map((turn, index) => (
              <li key={turn.id} className="flex flex-col gap-1.5">
                <span className="text-muted-foreground text-xs">Turn {index + 1}</span>
                {turn.skills === undefined ? (
                  <span className="text-muted-foreground text-xs">Not recorded for a reopened conversation</span>
                ) : turn.skills.length > 0 ? (
                  <ul className="flex flex-wrap gap-1">
                    {turn.skills.map((skill, position) => (
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
            ))}
          </ol>
        )}
      </div>
    </aside>
  )
}
