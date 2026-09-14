import { Check } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type { ProviderReport } from '@/features/providers/types'

import { updateApiKeys, updateLocalModel } from './api'
import type { KeyStatus } from './types'
import { useApiKeys } from './use-api-keys'
import { useLocalModels } from './use-local-models'

const KEY_LABELS: Record<string, string> = {
  anthropic: 'anthropic',
  openai: 'openai',
  gemini: 'gemini',
  bedrock: 'aws bedrock',
}

interface SettingsScreenProps {
  onSaved: (report: ProviderReport) => void
}

export function SettingsScreen({ onSaved }: SettingsScreenProps) {
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [switching, setSwitching] = useState<string | null>(null)
  const apiKeys = useApiKeys()
  const localModels = useLocalModels()

  const pending = Object.fromEntries(
    Object.entries(drafts).filter(([, apiKey]) => apiKey.trim().length > 0),
  )
  const canSave = Object.keys(pending).length > 0 && !saving

  function detailFor(key: KeyStatus): string {
    if (!key.configured) {
      return 'no api key configured'
    }
    return key.usable ? 'api key configured' : 'stored, not usable yet'
  }

  async function saveKeys() {
    setSaving(true)
    setError(null)
    setSaved(false)

    try {
      onSaved(await updateApiKeys(pending))
      await apiKeys.reload()
      setDrafts({})
      setSaved(true)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'The keys could not be saved.')
    } finally {
      setSaving(false)
    }
  }

  async function chooseLocalModel(model: string) {
    setSwitching(model)
    setError(null)

    try {
      onSaved(await updateLocalModel(model))
      localModels.markSelected(model)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'The model could not be selected.')
    } finally {
      setSwitching(null)
    }
  }

  return (
    <div className="flex-1 overflow-y-auto px-6 py-8">
      <div className="mx-auto flex max-w-2xl flex-col gap-10">
        <div className="flex flex-col gap-1">
          <h1 className="text-xl font-semibold tracking-tight">Settings</h1>
          <p className="text-muted-foreground text-sm">
            Incant talks to your provider directly. Add a key, or pick a model already installed on this machine.
          </p>
        </div>

        <section className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <h2 className="text-sm font-semibold tracking-tight">API keys</h2>
            <p className="text-muted-foreground text-xs">
              Keys are written to the Incant config file on this machine, <code>~/.incant/config.yml</code> by
              default, and are never sent anywhere else.
            </p>
          </div>

          {apiKeys.state === 'failed' ? (
            <p role="alert" className="text-destructive text-sm">
              The stored keys could not be read, so nothing can be saved from here right now.
            </p>
          ) : null}

          {apiKeys.statuses.map((key) => {
            const label = KEY_LABELS[key.provider] ?? key.provider

            return (
              <div key={key.provider} className="flex flex-col gap-1.5">
                <div className="flex items-baseline justify-between gap-2">
                  <label htmlFor={`${key.provider}-api-key`} className="text-sm font-medium">
                    {label}
                  </label>
                  <span
                    className={
                      key.configured && key.usable
                        ? 'text-xs text-emerald-600'
                        : 'text-muted-foreground text-xs'
                    }
                  >
                    {detailFor(key)}
                  </span>
                </div>
                <Input
                  id={`${key.provider}-api-key`}
                  type="password"
                  autoComplete="off"
                  spellCheck={false}
                  value={drafts[key.provider] ?? ''}
                  onChange={(event) =>
                    setDrafts((current) => ({ ...current, [key.provider]: event.target.value }))
                  }
                  placeholder={key.configured ? 'Replace the stored key' : 'Paste a key'}
                  aria-label={`${label} API key`}
                />
              </div>
            )
          })}

          <div className="flex items-center gap-3">
            <Button type="button" onClick={() => void saveKeys()} disabled={!canSave}>
              {saving ? 'Saving…' : 'Save keys'}
            </Button>
            {saved ? (
              <span role="status" className="text-muted-foreground text-sm">
                Saved.
              </span>
            ) : null}
          </div>
        </section>

        <section className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <h2 className="text-sm font-semibold tracking-tight">Local models</h2>
            <p className="text-muted-foreground text-xs">
              Models already installed in Ollama. Choosing one makes it the model the ollama provider runs.
            </p>
          </div>

          {localModels.state === 'loading' ? (
            <p className="text-muted-foreground text-sm">Looking for installed models…</p>
          ) : localModels.models === null || localModels.state === 'failed' ? (
            <p className="text-muted-foreground text-sm">The installed models could not be read.</p>
          ) : !localModels.models.reachable ? (
            <p className="text-muted-foreground text-sm">
              Ollama is not reachable at <code>{localModels.models.baseUrl}</code>.
            </p>
          ) : localModels.models.models.length === 0 ? (
            <p className="text-muted-foreground text-sm">
              No models are installed at <code>{localModels.models.baseUrl}</code>.
            </p>
          ) : (
            <ul className="flex flex-col gap-1">
              {localModels.models.models.map((model) => {
                const active = model === localModels.models?.selected

                return (
                  <li key={model}>
                    <Button
                      type="button"
                      variant={active ? 'secondary' : 'ghost'}
                      onClick={() => void chooseLocalModel(model)}
                      disabled={switching !== null}
                      aria-current={active ? 'true' : undefined}
                      className="w-full justify-between font-normal"
                    >
                      <span className="truncate">{model}</span>
                      {active ? <Check className="size-4" /> : null}
                      {switching === model ? <span className="text-xs">Switching…</span> : null}
                    </Button>
                  </li>
                )
              })}
            </ul>
          )}
        </section>

        {error ? (
          <p
            role="alert"
            className="border-destructive/40 bg-destructive/10 text-destructive rounded-md border px-3 py-2 text-sm"
          >
            {error}
          </p>
        ) : null}
      </div>
    </div>
  )
}
