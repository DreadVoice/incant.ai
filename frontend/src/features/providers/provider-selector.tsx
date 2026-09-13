import { ChevronDown } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
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
      <p className="text-muted-foreground px-1 text-xs">
        {status === 'loading' ? 'Loading providers…' : 'Providers unavailable, using the server default.'}
      </p>
    )
  }

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          disabled={disabled}
          aria-label="Provider and model"
          className="text-muted-foreground h-7 self-start px-2 text-xs font-normal"
        >
          {provider} · {model === '' ? 'configured model' : model}
          <ChevronDown />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="flex w-72 flex-col gap-3">
        <div className="flex flex-col gap-1.5">
          <span className="text-xs font-medium">Provider</span>
          <Select value={provider} onValueChange={onProviderChange}>
            <SelectTrigger size="sm" className="w-full" aria-label="Provider">
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
        </div>

        <div className="flex flex-col gap-1.5">
          <label htmlFor="provider-model" className="text-xs font-medium">
            Model
          </label>
          <Input
            id="provider-model"
            value={model}
            onChange={(event) => onModelChange(event.target.value)}
            aria-label="Model"
            placeholder="Model"
            className="h-8"
          />
        </div>
      </PopoverContent>
    </Popover>
  )
}
