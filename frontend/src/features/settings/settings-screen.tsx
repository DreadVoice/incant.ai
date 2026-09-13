import { Check } from 'lucide-react'
import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type { ProviderReport } from '@/features/providers/types'
import type { ProviderStatusState } from '@/features/providers/use-providers'

import { updateApiKeys, updateLocalModel } from './api'
import { useLocalModels } from './use-local-models'

interface KeyField {
  provider: string
  label: string
  supported: boolean
}

const KEY_FIELDS: KeyField[] = [
  { provider: 'anthropic', label: 'anthropic', supported: true },
  { provider: 'openai', label: 'openai', supported: true },
  { provider: 'gemini', label: 'gemini', supported: false },
  { provider: 'bedrock', label: 'aws bedrock', supported: false },
]

interface SettingsScreenProps {
  report: ProviderReport | null
  status: ProviderStatusState
  onSaved: (report: ProviderReport) => void
}

export function SettingsScreen({ report, status, onSaved }: SettingsScreenProps) {
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [switching, setSwitching] = useState<string | null>(null)
  const localModels = useLocalModels()

  const pending = Object.fromEntries(
    Object.entries(drafts).filter(([, apiKey]) => apiKey.trim().length > 0),
  )
  const canSave = Object.keys(pending).length > 0 && !saving

  function detailFor(provider: string): string {
    return report?.providers.find((entry) => entry.name === provider)?.detail ?? 'status unknown'
  }

  function isConfigured(provider: string): boolean {
    return report?.providers.find((entry) => entry.name === provider)?.available ?? false
  }

  async function saveKeys() {
    setSaving(true)
    setError(null)
    setSaved(false)

    try {
      onSaved(await updateApiKeys(pending))
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

          {status === 'failed' ? (
            <p role="alert" className="text-destructive text-sm">
              The provider list could not be loaded, so key status is unavailable.
            </p>
          ) : null}

          {KEY_FIELDS.map((field) => (
            <div key={field.provider} className="flex flex-col gap-1.5">
              <div className="flex items-baseline justify-between gap-2">
                <label htmlFor={`${field.provider}-api-key`} className="text-sm font-medium">
                  {field.label}
                </label>
                <span
                  className={
                    field.supported && isConfigured(field.provider)
                      ? 'text-xs text-emerald-600'
                      : 'text-muted-foreground text-xs'
                  }
                >
                  {field.supported ? detailFor(field.provider) : 'not supported yet'}
                </span>
              </div>
              <Input
                id={`${field.provider}-api-key`}
                type="password"
                autoComplete="off"
                spellCheck={false}
                disabled={!field.supported}
                value={drafts[field.provider] ?? ''}
                onChange={(event) =>
                  setDrafts((current) => ({ ...current, [field.provider]: event.target.value }))
                }
                placeholder={
                  field.supported
                    ? isConfigured(field.provider)
                      ? 'Replace the stored key'
                      : 'Paste a key'
                    : 'Coming later'
                }
                aria-label={`${field.label} API key`}
              />
            </div>
          ))}

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
