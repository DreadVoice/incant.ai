import { useState } from 'react'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type { ProviderReport } from '@/features/providers/types'
import type { ProviderStatusState } from '@/features/providers/use-providers'

import { updateApiKeys } from './api'

const KEYED_PROVIDERS = ['anthropic', 'openai'] as const

type KeyedProvider = (typeof KEYED_PROVIDERS)[number]

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

  const pending = Object.fromEntries(
    Object.entries(drafts).filter(([, apiKey]) => apiKey.trim().length > 0),
  )
  const canSave = Object.keys(pending).length > 0 && !saving

  function detailFor(provider: KeyedProvider): string {
    return report?.providers.find((entry) => entry.name === provider)?.detail ?? 'status unknown'
  }

  function isConfigured(provider: KeyedProvider): boolean {
    return report?.providers.find((entry) => entry.name === provider)?.available ?? false
  }

  async function save() {
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

  return (
    <div className="flex-1 overflow-y-auto px-6 py-8">
      <div className="mx-auto flex max-w-2xl flex-col gap-8">
        <div className="flex flex-col gap-1">
          <h1 className="text-xl font-semibold tracking-tight">Settings</h1>
          <p className="text-muted-foreground text-sm">
            Incant talks to your provider directly. Add a key to make that provider available.
          </p>
        </div>

        <section className="flex flex-col gap-4">
          <div className="flex flex-col gap-1">
            <h2 className="text-sm font-semibold tracking-tight">API keys</h2>
            <p className="text-muted-foreground text-xs">
              Keys are written to the Incant config file on this machine, <code>~/.incant/config.yml</code> by
              default, and are never sent anywhere else. Ollama needs no key.
            </p>
          </div>

          {status === 'failed' ? (
            <p role="alert" className="text-destructive text-sm">
              The provider list could not be loaded, so key status is unavailable.
            </p>
          ) : null}

          {KEYED_PROVIDERS.map((provider) => (
            <div key={provider} className="flex flex-col gap-1.5">
              <div className="flex items-baseline justify-between gap-2">
                <label htmlFor={`${provider}-api-key`} className="text-sm font-medium">
                  {provider}
                </label>
                <span
                  className={isConfigured(provider) ? 'text-xs text-emerald-600' : 'text-muted-foreground text-xs'}
                >
                  {detailFor(provider)}
                </span>
              </div>
              <Input
                id={`${provider}-api-key`}
                type="password"
                autoComplete="off"
                spellCheck={false}
                value={drafts[provider] ?? ''}
                onChange={(event) =>
                  setDrafts((current) => ({ ...current, [provider]: event.target.value }))
                }
                placeholder={isConfigured(provider) ? 'Replace the stored key' : 'Paste a key'}
                aria-label={`${provider} API key`}
              />
            </div>
          ))}

          {error ? (
            <p
              role="alert"
              className="border-destructive/40 bg-destructive/10 text-destructive rounded-md border px-3 py-2 text-sm"
            >
              {error}
            </p>
          ) : null}

          <div className="flex items-center gap-3">
            <Button type="button" onClick={() => void save()} disabled={!canSave}>
              {saving ? 'Saving…' : 'Save keys'}
            </Button>
            {saved ? (
              <span role="status" className="text-muted-foreground text-sm">
                Saved.
              </span>
            ) : null}
          </div>
        </section>
      </div>
    </div>
  )
}
