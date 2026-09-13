import { Input } from '@/components/ui/input'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

import type { ProviderReport } from './types'
import type { ProviderStatusState } from './use-provider-selection'

interface ProviderSelectorProps {
  report: ProviderReport | null
  status: ProviderStatusState
  provider: string
  model: string
  disabled: boolean
  onProviderChange: (name: string) => void
  onModelChange: (value: string) => void
}

export function ProviderSelector({
  report,
  status,
  provider,
  model,
  disabled,
  onProviderChange,
  onModelChange,
}: ProviderSelectorProps) {
  if (status !== 'ready' || report === null) {
    return (
      <p className="text-muted-foreground text-xs">
        {status === 'loading' ? 'Loading providers…' : 'Providers unavailable, using the server default.'}
      </p>
    )
  }

  return (
    <div className="flex items-center gap-2">
      <Select value={provider} onValueChange={onProviderChange} disabled={disabled}>
        <SelectTrigger size="sm" className="w-32" aria-label="Provider">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {report.providers.map((entry) => (
            <SelectItem key={entry.name} value={entry.name} disabled={!entry.available}>
              <SelectItemText>{entry.name}</SelectItemText>
              <span className="text-muted-foreground text-xs">{entry.detail}</span>
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Input
        value={model}
        onChange={(event) => onModelChange(event.target.value)}
        disabled={disabled}
        aria-label="Model"
        placeholder="Model"
        className="h-8 w-44"
      />
    </div>
  )
}
