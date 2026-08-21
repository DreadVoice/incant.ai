# Compatibility

What actually runs, what doesn't, and what each provider does differently. Everything here was measured against the code in this repo, where something is untested, it says so rather than guessing.

Last updated: 2026-08-21, against Incant at `v0.2` (agent loop + three providers).

---

## Skill classes

Incant sorts every skill into one of three classes on load and tells you which one it landed in. The class decides whether the skill is offered to the model at all.

| Class | What it needs | Offered to the model when | Status |
|---|---|---|---|
| `INSTRUCTION` | Nothing but context | Always | ✅ Works today |
| `DOCUMENT` | A sandbox that can run scripts | `sandboxAvailable` is true | ⛔ Never - no sandbox implemented yet |
| `CODING_AGENT` | A workspace with repo access | `workspaceActive` is true | ⛔ Never - no workspace implemented yet |

Both flags are currently hardcoded to `false` at the one place that builds a system prompt (`ChatController`), so **only instruction skills reach the model today**. Document and coding-agent skills are still parsed, classified, and listed by the loader — they are simply withheld from the prompt, which is the honest failure mode: the model never sees a skill it cannot run.

### How classification is decided

`SkillClassifier` checks in this order, first match wins:

1. **Coding-agent markers**
   - `allowed-tools` in the frontmatter names any of `edit`, `multiedit`, `write`, `task`, `git` (case-insensitive), **or**
   - the skill folder contains an `agents/`, `commands/`, or `hooks/` directory.
2. **Scripts**: any regular file under the skill root (walked 4 levels deep) with extension `.py`, `.sh`, `.js`, `.ts`, `.rb`, or `.ps1`.
3. Otherwise **instruction**.

Consequences worth knowing:

- **Agent markers beat scripts.** A skill with both `allowed-tools: Bash, Edit` and a `scripts/` folder is classified `CODING_AGENT`, not `DOCUMENT`. That is deliberate — the harness requirement is the harder constraint.
- **It is a heuristic, not a contract.** The skill format has no class field; these rules are inferred from how published skills are laid out. A document skill that ships no script (because it only describes a manual process) will be classified `INSTRUCTION`, and that is usually the right answer anyway.
- **Only `allowed-tools` is read.** Other frontmatter keys naming tools are ignored, because `allowed-tools` is the key the published format actually uses.
- **`bash` is not an agent marker.** Document skills routinely need to run a script; treating `bash` as a repo-access signal misclassified too much.

### Frontmatter requirements

`FrontmatterParser` is strict on purpose, a malformed skill fails loudly at load rather than silently half-loading:

- The file must open with a `---` line and close the block with another `---`. Missing or unterminated fences are errors.
- `name` and `description` are required; a skill missing either fails to load.
- Every other key (`license`, `allowed-tools`, anything custom) is preserved in the skill's `metadata`.
- Duplicate keys are rejected rather than last-one-wins.
- Handled without complaint: a UTF-8 BOM, CRLF line endings, YAML folded scalars (`description: >`), and `---` horizontal rules inside the markdown body.
- `allowed-tools` accepts either a YAML sequence or a comma-separated string.

Verified against real published skills (`academy-guide`, `doc-coauthoring`, `frontend-design`) as well as synthetic fixtures.

---

## Providers

| Provider | Auth | Base URL | Tool calling | Verified |
|---|---|---|---|---|
| `anthropic` | `incant.providers.anthropic.api-key` | Optional override | Expected to work | ⚠️ Not exercised live, no key available during development |
| `openai` | `incant.providers.openai.api-key` | Optional override; any OpenAI-compatible endpoint | ✅ Works | ✅ Live, via OpenRouter |
| `ollama` | None | Defaults to `http://localhost:11434` | ⚠️ Partial, see below | ✅ Live, local |

Selection is per request (`provider` and `model` fields on `POST /api/chat`) with configured defaults, and `GET /api/providers` reports what is actually usable right now.

### Fallback behavior

If the selected provider needs an API key and none is configured, Incant switches to the local Ollama model and logs a warning. The response body always reports the provider and model that actually served the request, so a fallback is never silent to the caller.

On first start, Incant creates the `incant-qwen` model in Ollama from [`src/main/resources/ollama/Modelfile`](src/main/resources/ollama/Modelfile) (`FROM qwen2.5:0.5b`). If Ollama isn't installed or running, that step logs a warning and startup continues — the app never fails to boot over it.

### Anthropic

Untested end to end. The provider is built through the same langchain4j `ChatModel` interface as the others and the loop makes no provider-specific calls, so nothing in the code path is Anthropic-shaped — but "should work" is not "does work", and this row stays ⚠️ until someone runs it with a key.

### OpenAI-compatible (including OpenRouter)

Verified with `openai/gpt-4o-mini` through `https://openrouter.ai/api/v1`. A skill-loading turn completed as expected:

```
iterations=2, inputTokens=537, outputTokens=32, durationMillis=3257
transcript: SystemMessage, UserMessage, AiMessage, ToolExecutionResultMessage, AiMessage
```

The model read the skill list from the system prompt, called `load_skill`, received the `SKILL.md` body, and answered from it. Point `incant.providers.openai.base-url` at any OpenAI-compatible endpoint; no other change is needed.

### Ollama (`qwen2.5:0.5b`)

This is the default local fallback, and it is a 0.5B model; the limitations below are what that size buys, not bugs in Incant.

**Tool calling on the first turn is reliable.** Four out of four raw probes emitted a well-formed `load_skill` call with correct JSON arguments (`{"name":"writing-clearly"}`) and `finishReason=TOOL_EXECUTION`.

**The turn after a tool result often comes back empty.** In repeated runs of the same prompt, the model frequently returns an assistant message with no text and no further tool calls once the skill body has been injected. The loop treats that as a finished turn, so the API responds with `"reply": null`. Measured roughly 2 in 3 attempts on `incant-qwen` and on the bare `qwen2.5:0.5b`. When it does answer, the answer is grounded in the loaded skill.

**Latency:** first request after model load ≈ 49s (weights loading into memory); subsequent requests ≈ 200ms for short replies.

**Practical read:** the local model is good enough to prove the plumbing works offline and to smoke-test changes. It is not good enough to evaluate whether a skill *works*. Use a hosted model for that.

---

## Not supported yet

- **Document skills** - no sandbox, so they are never offered to the model.
- **Coding-agent skills** - no workspace mode, same.
- **Streaming** - responses are returned whole.
- **Conversation persistence** - the `conversations` and `messages` tables exist and migrate, but `POST /api/chat` is stateless and writes nothing to them.
