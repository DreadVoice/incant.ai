# Incant

**A local-first, provider-agnostic runtime for Claude Skills.**

Run published skills on Claude, GPT, Gemini, or a local model. Nothing leaves your machine unless you send it there yourself.

> **Status: early development.** The skill loader, the agent loop, and three providers work today. The UI, sandbox, and workspace mode do not. See [Project status](#project-status)

---

## The idea

[Claude Skills](https://www.anthropic.com/news/skills) are a nice format: a folder with a `SKILL.md` file, some YAML frontmatter, markdown instructions, and optionally a few scripts. An agent reads the description, decides the skill is relevant, loads the instructions into context, and follows them.

That's a simple enough contract that there's no real reason it should only work inside Anthropic's own products. People have published hundreds of these (document generation, code review, writing style, research workflows) and they're just files.

Incant is a runtime for them. Point it at a folder of skills, pick a model, and go.

## Why bother

- **No vendor lock-in.** The skill ecosystem is genuinely useful. It shouldn't be tied to one provider's client.
- **Local by default.** Your conversations live in a SQLite file on your disk. Your API keys live in a config file you own. There is no server, no account, and no telemetry.
- **Bring your own key.** Incant costs nothing to run and makes no money. You pay your provider directly, or run Ollama and pay nobody.
- **Something to read.** Part of the point of this project is documenting how skills actually work across providers: what ports cleanly, what doesn't, and why. See [`COMPATIBILITY.md`](./COMPATIBILITY.md) once it exists.

## Not every skill ports cleanly

This turned out to be the most interesting problem in the project, so it's worth stating clearly up front. Published skills fall into three classes, and they need very different things from a runtime:

| Class | Needs | Examples | Incant support |
|---|---|---|---|
| **Instruction** | Just context | Writing style, code review heuristics, domain methodology | ✅ Planned for v1.0 |
| **Document** | Sandbox with Python, file I/O | `docx`, `pdf`, `xlsx` generation | 🔜 Planned for v3.0 |
| **Coding-agent** | A full agent harness with repo, git, subagents, file editing | Superpowers and similar | 🔜 Planned for v2.0 |

Incant classifies each skill on load and tells you which class it's in, including when a skill *can't* run here and why. A tool that's honest about its limits is more useful than one that fails mysteriously.

---

## How it works

```
┌─────────────────────────────────────────────┐
│  React SPA - chat, workspace, skill panel   │
└──────────────────┬──────────────────────────┘
                   │ REST + SSE (localhost)
┌──────────────────▼──────────────────────────┐
│  AgentOrchestrator - the tool-calling loop  │
├──────────────┬──────────────┬───────────────┤
│  Skills      │  Tools       │  Providers    │
│  loader      │  dispatcher  │  LangChain4j  │
│  classifier  │  load_skill  │  Anthropic    │
│  registry    │  bash, files │  OpenAI       │
│              │  git, edit   │  Ollama       │
├──────────────┴──────────────┴───────────────┤
│  ExecutionBackend                           │
│  · LocalWorkspace  - your repo, your machine│
│  · Docker          - sandboxed, no egress   │
│  · Docker + gVisor - syscall-filtered       │
├─────────────────────────────────────────────┤
│  SQLite  ·  ~/.incant/                      │
└─────────────────────────────────────────────┘
```

The core loop is small: inject every skill's `description` into the system prompt alongside a `load_skill` tool, let the model decide what's relevant, return the full `SKILL.md` body when it asks, and continue. That's the same mechanism Anthropic's own harness uses, and it works identically across providers because it's just tool calling.

Everything else (sandboxing, workspace access, subagents) is infrastructure hanging off that loop.

## Two modes

**Chat mode** is a normal conversation. Instruction skills load into context; document skills run their scripts in a locked-down container with no network access.

**Workspace mode** points Incant at a directory on your machine. It gets file editing, bash, and git tools, and coding-agent skills become available. There's no sandbox here; the trust model is the same as any coding agent you'd install: you chose the directory, you chose the skills, and you can watch every command in the activity log. That's a deliberate choice, not an oversight. See [Security](#security).

---

## Getting started

There is no packaged release yet. What runs today is a Spring Boot service with two JSON endpoints, and you drive it from the terminal.

**Requirements:** JDK 21+. [Ollama](https://ollama.com) if you want a local model to fall back on. Docker only when document skills land.

### Run it

```bash
git clone https://github.com/DreadVoice/incant.ai.git
cd incant.ai
./gradlew bootRun
```

On first start Incant loads every skill in `./skills`, and, if Ollama is running, creates a small `incant-qwen` model from `src/main/resources/ollama/Modelfile` so there is always something to talk to. That download happens once and can take a few minutes. If Ollama is missing the step is skipped with a warning and the app still starts.

### Testing it from the terminal

**What is configured right now:**

```bash
curl -s localhost:8080/api/providers
```

```json
{"defaultProvider":"anthropic","providers":[
  {"name":"anthropic","available":false,"model":"claude-opus-5","detail":"no api key configured"},
  {"name":"openai","available":false,"model":"gpt-4o-mini","detail":"no api key configured"},
  {"name":"ollama","available":true,"model":"incant-qwen","detail":"model installed"}]}
```

`available` is the useful field. Anthropic and OpenAI need a key; Ollama needs a reachable server with the model installed.

**Send a message:**

```bash
curl -s -X POST localhost:8080/api/chat   -H "Content-Type: application/json"   -d '{"message":"Say hello in five words."}'
```

```json
{"reply":"Hello, how are you today?","provider":"ollama","model":"incant-qwen",
 "telemetry":{"iterations":1,"inputTokens":317,"outputTokens":8,"durationMillis":74}}
```

The response always names the provider and model that actually served the request, so a fallback is never silent.

**Check that a skill was really used.** Name a skill in the message and watch `iterations` in the response. One iteration means the model answered on its own; two or more means it called `load_skill` and read the instructions first.

```bash
curl -s -X POST localhost:8080/api/chat   -H "Content-Type: application/json"   -d '{"message":"Use the writing-clearly skill and give me its first editing step."}'
```

```json
{"reply":"The first editing step is to read the whole passage before changing anything.",
 "provider":"openai","model":"openai/gpt-4o-mini",
 "telemetry":{"iterations":2,"inputTokens":589,"outputTokens":31,"durationMillis":2983}}
```

**Pick a provider or model per request**, overriding the configured default:

```bash
curl -s -X POST localhost:8080/api/chat -H "Content-Type: application/json"   -d '{"message":"Say hi.","provider":"ollama","model":"qwen2.5:0.5b"}'
```

**Error cases**, so you can tell a bug from expected behaviour:

```bash
# unknown provider, HTTP 400
curl -s -X POST localhost:8080/api/chat -H "Content-Type: application/json"   -d '{"message":"hi","provider":"gemini"}'
# {"error":"unknown provider 'gemini', supported: [openai, anthropic, ollama]"}

# blank message, HTTP 400
curl -s -o /dev/null -w "%{http_code}
" -X POST localhost:8080/api/chat   -H "Content-Type: application/json" -d '{"message":"  "}'
```

Two things that look like failures but are not. A `"reply": null` is the local 0.5B model returning an empty turn after reading a skill; see [`COMPATIBILITY.md`](./COMPATIBILITY.md). A first request that takes 30 to 60 seconds is Ollama loading weights into memory, and later requests are fast.

### Using a hosted model

Keys, models, and base URLs are configured per provider, so you can hold several at once. Any OpenAI-compatible endpoint works through the `openai` provider, including OpenRouter and a local vLLM server.

```bash
ANTHROPIC_API_KEY=sk-ant-...  ./gradlew bootRun

OPENAI_API_KEY=sk-...  INCANT_PROVIDER=openai  ./gradlew bootRun

OPENAI_API_KEY=sk-or-...  INCANT_PROVIDER=openai   INCANT_OPENAI_BASE_URL=https://openrouter.ai/api/v1   INCANT_OPENAI_MODEL=openai/gpt-4o-mini  ./gradlew bootRun
```

Every setting in `src/main/resources/application.properties` reads from an environment variable, including `INCANT_SKILLS_PATH`, `INCANT_ANTHROPIC_MODEL`, `OLLAMA_BASE_URL`, and `INCANT_OLLAMA_AUTO_INSTALL`.

### Adding skills

Drop skill folders into `./skills`, or point `INCANT_SKILLS_PATH` somewhere else. Each one needs a `SKILL.md` with `name` and `description` in the frontmatter, the standard format. Skills are loaded and classified at startup, and only the ones that can actually run on this machine are offered to the model. The repo ships one sample of each class in [`skills/`](./skills).

### Running the tests

```bash
./gradlew test
```

No network and no API key required. The suite covers frontmatter parsing, skill classification against real published skills, and the agent loop against a stubbed model.

---

## Security

**What's protected:** document skills run in a container with no network egress, capped memory and CPU, and a read-only root filesystem. On Linux with gVisor installed, they additionally run under a userspace kernel that filters syscalls before they reach the host. Incant detects this at startup and uses it when available.

**What isn't:** *NOT IMPLEMENTED YET* workspace mode runs on your machine with your permissions. Coding-agent skills need real repo access to be useful. Incant shows you every command before and after it runs, but it does not sandbox them. Treat installing a skill the way you'd treat installing any dependency: know where it came from.

**On API keys:** they're stored in `~/.incant/config.yml` in plaintext, protected by nothing but your filesystem permissions. This is the same posture as most local dev tools. If that's not acceptable for your keys, use scoped or throwaway ones.

Incant makes no network calls except to the provider you configured. There is no telemetry, no update check, and no analytics.

---

## Project status

| Milestone | What it means | Status |
|---|---|---|
| `v0.1` | Skills parsed and classified | ✅ Done |
| `v0.2` | Agent loop, one provider | ✅ Done |
| `v0.3` | Multi-provider | ✅ Done |
| `v1.0` | Chat UI, runnable JAR | 🟡 In progress |
| `v2.0` | Workspace mode, coding-agent skills | 🔴 Not started |
| `v3.0` | Docker sandbox, document skills | 🔴 Not started |

## Caveats

- **This is a learning project**, built by a final-year CS student. It's designed to be read as much as used. Expect the code to prioritize clarity over cleverness.
- **The skill format isn't a standard.** It's a convention Anthropic uses, and it could change. Incant tracks it; it doesn't control it.
- **Small local models are unreliable at tool calling.** Ollama support is real, but a 7B model will sometimes fail to invoke skills correctly. That's a model limitation, not a bug; findings get documented rather than papered over.
- **Some skills assume Claude-specific paths and preinstalled packages.** Incant mounts volumes where skills expect them and ships a base image with common libraries, but a skill can still fail because it needed something nobody declared. Failure should at least be legible when it happens.
- **Not affiliated with Anthropic.** "Claude" and "Claude Skills" are theirs. This is an independent, unaffiliated project that reads a public file format.

## Contributing

Not open to contributions yet, the architecture is still moving. Issues and ideas are welcome once `v0.3` is tagged.

## License

None yet.