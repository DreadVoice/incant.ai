# Incant

**A local-first, provider-agnostic runtime for Claude Skills.**

Run published skills on Claude, GPT, Gemini, Bedrock, or a local model. Nothing leaves your machine unless you send it there yourself.

> **Status: early development.** The chat UI, the skill loader, the agent loop, streaming, stored conversations and five providers work today. The sandbox and workspace mode do not. See [Project status](#project-status)

---

## The idea

[Claude Skills](https://www.anthropic.com/news/skills) are a nice format: a folder with a `SKILL.md` file, some YAML frontmatter, markdown instructions, and optionally a few scripts. An agent reads the description, decides the skill is relevant, loads the instructions into context, and follows them.

That's a simple enough contract that there's no real reason it should only work inside Anthropic's own products. People have published hundreds of these (document generation, code review, writing style, research workflows) and they're just files.

Incant is a runtime for them. Point it at a folder of skills, pick a model, and go.

## Why bother

- **No vendor lock-in.** The skill ecosystem is genuinely useful. It shouldn't be tied to one provider's client.
- **Local by default.** Your conversations live in a SQLite file on your disk. Your API keys live in a config file you own. There is no server, no account, and no telemetry.
- **Bring your own key.** Incant costs nothing to run and makes no money. You pay your provider directly, or run Ollama and pay nobody.
- **Something to read.** Part of the point of this project is documenting how skills actually work across providers: what ports cleanly, what doesn't, and why. See [`COMPATIBILITY.md`](./COMPATIBILITY.md).

## Not every skill ports cleanly

This turned out to be the most interesting problem in the project, so it's worth stating clearly up front. Published skills fall into three classes, and they need very different things from a runtime:

| Class | Needs | Examples | Incant support |
|---|---|---|---|
| **Instruction** | Just context | Writing style, code review heuristics, domain methodology | ✅ Works today |
| **Document** | Sandbox with Python, file I/O | `docx`, `pdf`, `xlsx` generation | 🔜 Planned for v3.0 |
| **Coding-agent** | A full agent harness with repo, git, subagents, file editing | Superpowers and similar | 🔜 Planned for v2.0 |

Incant classifies each skill on load and tells you which class it's in, including when a skill *can't* run here and why. A tool that's honest about its limits is more useful than one that fails mysteriously.

---

## How it works

```
┌─────────────────────────────────────────────┐
│  React SPA - chat, skills, settings         │
└──────────────────┬──────────────────────────┘
                   │ REST + SSE (localhost)
┌──────────────────▼──────────────────────────┐
│  AgentOrchestrator - the tool-calling loop  │
├──────────────┬──────────────┬───────────────┤
│  Skills      │  Tools       │  Providers    │
│  loader      │  dispatcher  │  LangChain4j  │
│  classifier  │  load_skill  │  Anthropic    │
│  registry    │              │  OpenAI       │
│              │              │  Gemini       │
│              │              │  Bedrock      │
│              │              │  Ollama       │
├──────────────┴──────────────┴───────────────┤
│  ExecutionBackend - not built yet           │
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

**Chat mode** is a normal conversation, and it is the mode that exists today. Instruction skills load into context. Document skills are recognised and then held back, because the container that would run their scripts is not built yet.

**Workspace mode** is not built yet. The plan: point Incant at a directory on your machine. It gets file editing, bash, and git tools, and coding-agent skills become available. There's no sandbox here; the trust model is the same as any coding agent you'd install: you chose the directory, you chose the skills, and you can watch every command in the activity log. That's a deliberate choice, not an oversight. See [Security](#security).

---

## Getting started

There is no packaged release yet, so you build it yourself. What runs today is a Spring Boot service that also serves the chat UI.

**Requirements:** JDK 21+ and Node.js 22+ (the UI is built by the Gradle build). [Ollama](https://ollama.com) if you want a local model to fall back on. Docker only when document skills land.

### Run it

```bash
git clone https://github.com/DreadVoice/incant.ai.git
cd incant.ai
./gradlew bootJar
java -jar build/libs/incant.jar
```

Then open <http://localhost:8080>. `bootJar` builds the frontend and packs it into the jar, so the UI is served from the same port as the API.

`./gradlew bootRun` starts the API alone without the UI, which is what you want when you are working on the backend. If you downloaded a zip instead of cloning, run `chmod +x gradlew` first, since zips do not carry the executable bit.

To work on the frontend, run the Vite dev server next to the backend:

```bash
cd frontend
npm install
npm run dev
```

It serves <http://localhost:5173> and proxies `/api` to port 8080, so the backend must be running too.

### Packaging it

```bash
./gradlew jpackageImage      # a runnable app image
./gradlew jpackageInstaller  # a native installer for this machine
```

`jlink` first builds a Java runtime holding only the modules Incant needs, which takes it from about 150MB to 57MB, and `jpackage` wraps that runtime, the jar and a native launcher together. The result runs on a machine with no JDK installed. The app image lands in `build/jpackage/Incant` and starts with `bin/Incant`; it is about 143MB, nearly all of it runtime and dependencies.

`jpackageInstaller` builds whatever the host supports: `.deb` on Linux, `.dmg` on macOS, `.msi` on Windows. jpackage shells out to the platform's own packaging tools, so Linux needs `fakeroot` (`apt install fakeroot`), and Windows needs [WiX](https://wixtoolset.org/). Cross-building is not possible: each installer has to be built on the platform it targets.

If you add a dependency that reaches for a part of the JDK the trimmed runtime left out, the app fails at startup with `NoClassDefFoundError`. The fix is to add the module to `runtimeModules` in `build.gradle.kts`.

On first start Incant loads every skill in `./skills`, and, if Ollama is running, creates a small `incant-qwen` model from `src/main/resources/ollama/Modelfile` so there is always something to talk to. That download happens once and can take a few minutes. If Ollama is missing the step is skipped with a warning and the app still starts.

### What the UI gives you

The chat screen streams each answer as it arrives, and names the provider and model that served it. A collapsible sidebar lists your saved conversations and holds the settings screen; a panel on the right shows which skills each answer loaded. The composer has a provider and model picker, and settings holds the API keys and the list of models Ollama already has installed.

On first run, when no provider has a key, Incant opens settings instead of the chat so there is something to do.

### Testing it from the terminal

Everything the UI does is a plain HTTP call:

| Endpoint | What it does |
| --- | --- |
| `GET /api/providers` | Which providers are configured and usable |
| `POST /api/chat` | One turn, answered in a single JSON response |
| `POST /api/chat/stream` | The same turn, streamed as Server-Sent Events |
| `GET /api/conversations` | Stored conversations, most recently used first |
| `GET /api/conversations/{id}` | One conversation with its messages |
| `GET /api/config/api-keys` | Which keys are stored, and whether that provider can run |
| `PUT /api/config/api-keys` | Store or clear a key |
| `GET /api/config/local-models` | Models installed in Ollama |
| `GET /api/version` | The build this jar was made from |
| `PUT /api/config/local-model` | Switch the local model |

**What is configured right now:**

```bash
curl -s localhost:8080/api/providers
```

```json
{"defaultProvider":"anthropic","providers":[
  {"name":"anthropic","available":false,"model":"claude-opus-5","detail":"no api key configured"},
  {"name":"openai","available":false,"model":"gpt-4o-mini","detail":"no api key configured"},
  {"name":"gemini","available":false,"model":"gemini-2.5-flash","detail":"no api key configured"},
  {"name":"bedrock","available":false,"model":"","detail":"no api key configured"},
  {"name":"ollama","available":true,"model":"incant-qwen","detail":"model installed"}]}
```

`available` is the useful field. Anthropic, OpenAI and Gemini need a key; Bedrock needs a key, a region and a model id; Ollama needs a reachable server with the model installed.

**Send a message:**

```bash
curl -s -X POST localhost:8080/api/chat   -H "Content-Type: application/json"   -d '{"message":"Say hello in five words."}'
```

```json
{"reply":"Hello, how are you today?","provider":"ollama","model":"incant-qwen","conversationId":1,
 "telemetry":{"iterations":1,"inputTokens":317,"outputTokens":8,"durationMillis":74}}
```

The response always names the provider and model that actually served the request, so a fallback is never silent.

**Continue a conversation.** Every reply carries a `conversationId`. Send it back and the turn is appended to that conversation, so the model is given everything said before it:

```bash
curl -s -X POST localhost:8080/api/chat   -H "Content-Type: application/json"   -d '{"message":"What did I just ask you?","conversationId":1}'
```

Conversations and their messages are stored in SQLite at `~/.incant/incant.db` (override with `INCANT_DB_PATH`), so they outlive a restart. A database left behind at the old `./data/incant.db` is moved there on the next start. Leaving `conversationId` out starts a new conversation; an id that does not exist is rejected with HTTP 400.

**Stream the answer.** `POST /api/chat/stream` takes the same body and returns Server-Sent Events, so the reply arrives token by token instead of in one block:

```bash
curl -sN -X POST localhost:8080/api/chat/stream   -H "Content-Type: application/json"   -d '{"message":"Use the writing-clearly skill"}'
```

```
event:skill
data:{"name":"writing-clearly"}

event:token
data:{"text":"Read "}

event:done
data:{"reply":"Read the whole passage before changing anything.","provider":"openai","model":"gpt-4o-mini","conversationId":1,"skills":["writing-clearly"],"telemetry":{...}}
```

A `skill` event fires the moment a skill is loaded, `token` carries each fragment as the model produces it, and `done` repeats the whole reply with the same fields the blocking endpoint returns. Failures arrive as a single `error` event rather than an HTTP status, because the response has already started. The turn is written to SQLite when it completes, exactly as the blocking endpoint does.

**Check that a skill was really used.** Name a skill in the message and watch `iterations` in the response. One iteration means the model answered on its own; two or more means it called `load_skill` and read the instructions first.

```bash
curl -s -X POST localhost:8080/api/chat   -H "Content-Type: application/json"   -d '{"message":"Use the writing-clearly skill and give me its first editing step."}'
```

```json
{"reply":"The first editing step is to read the whole passage before changing anything.",
 "provider":"openai","model":"openai/gpt-4o-mini","conversationId":2,
 "telemetry":{"iterations":2,"inputTokens":589,"outputTokens":31,"durationMillis":2983}}
```

**Pick a provider or model per request**, overriding the configured default:

```bash
curl -s -X POST localhost:8080/api/chat -H "Content-Type: application/json"   -d '{"message":"Say hi.","provider":"ollama","model":"qwen2.5:0.5b"}'
```

**Error cases**, so you can tell a bug from expected behaviour:

```bash
# unknown provider, HTTP 400
curl -s -X POST localhost:8080/api/chat -H "Content-Type: application/json"   -d '{"message":"hi","provider":"mistral"}'
# {"error":"unknown provider 'mistral', supported: [gemini, anthropic, ollama, bedrock, openai]"}

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

GEMINI_API_KEY=...  INCANT_PROVIDER=gemini  ./gradlew bootRun

AWS_BEARER_TOKEN_BEDROCK=...  INCANT_PROVIDER=bedrock   AWS_REGION=us-east-1   INCANT_BEDROCK_MODEL=anthropic.claude-sonnet-4-v1:0  ./gradlew bootRun
```

Bedrock ships no default model id, because ids differ by account and region: set `INCANT_BEDROCK_MODEL` (or the model field) or the provider stays unavailable. A stored Bedrock key is sent as a bearer token; leave it empty to fall back to the AWS credential chain.

Every setting in `src/main/resources/application.properties` reads from an environment variable, including `INCANT_SKILLS_PATH`, `INCANT_ANTHROPIC_MODEL`, `OLLAMA_BASE_URL`, and `INCANT_OLLAMA_AUTO_INSTALL`.

Keys can also be set from the UI. The settings screen writes them to `~/.incant/config.yml` (override with `INCANT_CONFIG_PATH`), a file only your user can read, and applies them without a restart. The same screen lists the models Ollama already has installed and switches the local model between them. An environment variable always wins over a stored value, so `ANTHROPIC_API_KEY=... ./gradlew bootRun` still overrides whatever the screen saved.

### Adding skills

Drop skill folders into `./skills`, or point `INCANT_SKILLS_PATH` somewhere else. Each one needs a `SKILL.md` with `name` and `description` in the frontmatter, the standard format. Skills are loaded and classified at startup, and only the ones that can actually run on this machine are offered to the model. The repo ships one sample of each class in [`skills/`](./skills).

### Running the tests

```bash
./gradlew test
```

No network and no API key required. The suite covers frontmatter parsing, skill classification against real published skills, the agent loop against a stubbed model, conversation storage, the config file, and provider construction. Frontend checks are separate:

```bash
cd frontend
npm run build
npm run lint
```

---

## Security

**What's protected today:** nothing needs protecting yet, because nothing executes. Only instruction skills are offered to the model, and an instruction skill is text that goes into the prompt. Document skills are classified on load and then withheld, so no skill-supplied script runs anywhere.

**The plan for v3.0:** document skills run in a container with no network egress, capped memory and CPU, and a read-only root filesystem. On Linux with gVisor installed, they would additionally run under a userspace kernel that filters syscalls before they reach the host.

**What won't be protected:** workspace mode, when it lands, runs on your machine with your permissions. Coding-agent skills need real repo access to be useful. Incant shows you every command before and after it runs, but it does not sandbox them. Treat installing a skill the way you'd treat installing any dependency: know where it came from.

**On API keys:** they're stored in `~/.incant/config.yml` in plaintext, written so only your user can read the file, and protected by nothing else. This is the same posture as most local dev tools. If that's not acceptable for your keys, use scoped or throwaway ones.

Incant makes no network calls except to the provider you configured. There is no telemetry, no update check, and no analytics.

---

## Project status

| Milestone | What it means | Status |
|---|---|---|
| `v0.1` | Skills parsed and classified | ✅ Done |
| `v0.2` | Agent loop, one provider | ✅ Done |
| `v0.3` | Multi-provider | ✅ Done |
| `v1.0` | Chat UI, runnable JAR | ✅ Done |
| `v2.0` | Workspace mode, coding-agent skills | 🟡 In progress |
| `v3.0` | Docker sandbox, document skills | 🔴 Not started |

## Caveats

- **This is a learning project**, built by a final-year CS student. It's designed to be read as much as used. Expect the code to prioritize clarity over cleverness.
- **The skill format isn't a standard.** It's a convention Anthropic uses, and it could change. Incant tracks it; it doesn't control it.
- **Small local models are unreliable at tool calling.** Ollama support is real, but a 7B model will sometimes fail to invoke skills correctly. That's a model limitation, not a bug; findings get documented rather than papered over.
- **Some skills assume Claude-specific paths and preinstalled packages.** The plan is to mount volumes where skills expect them and ship a base image with common libraries, but a skill will still be able to fail because it needed something nobody declared. Failure should at least be legible when it happens.
- **Not affiliated with Anthropic.** "Claude" and "Claude Skills" are theirs. This is an independent, unaffiliated project that reads a public file format.

## Contributing

Not open to contributions yet, the architecture is still moving. Issues and ideas are welcome once `v0.3` is tagged.

## License

None yet.