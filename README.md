# Incant

**A local-first, provider-agnostic runtime for Claude Skills.**

Run published skills on Claude, GPT, Gemini, or a local model. Nothing leaves your machine unless you send it there yourself.

> **Status: early development.** Nothing works yet. This README describes what Incant is being built to do, and is honest about what it doesn't do. See [Project status](#project-status) for what actually runs today.

---

## The idea

[Claude Skills](https://www.anthropic.com/news/skills) are a nice format: a folder with a `SKILL.md` file, some YAML frontmatter, markdown instructions, and optionally a few scripts. An agent reads the description, decides the skill is relevant, loads the instructions into context, and follows them.

That's a simple enough contract that there's no real reason it should only work inside Anthropic's own products. People have published hundreds of these — document generation, code review, writing style, research workflows — and they're just files.

Incant is a runtime for them. Point it at a folder of skills, pick a model, and go.

## Why bother

- **No vendor lock-in.** The skill ecosystem is genuinely useful. It shouldn't be tied to one provider's client.
- **Local by default.** Your conversations live in a SQLite file on your disk. Your API keys live in a config file you own. There is no server, no account, and no telemetry.
- **Bring your own key.** Incant costs nothing to run and makes no money. You pay your provider directly, or run Ollama and pay nobody.
- **Something to read.** Part of the point of this project is documenting how skills actually work across providers — what ports cleanly, what doesn't, and why. See [`COMPATIBILITY.md`](./COMPATIBILITY.md) once it exists.

## Not every skill ports cleanly

This turned out to be the most interesting problem in the project, so it's worth stating clearly up front. Published skills fall into three classes, and they need very different things from a runtime:

| Class | Needs | Examples | Incant support |
|---|---|---|---|
| **Instruction** | Just context | Writing style, code review heuristics, domain methodology | ✅ Planned for v1.0 |
| **Document** | Sandbox with Python, file I/O | `docx`, `pdf`, `xlsx` generation | 🔜 Planned for v3.0 |
| **Coding-agent** | A full agent harness — repo, git, subagents, file editing | Superpowers and similar | 🔜 Planned for v2.0 |

Incant classifies each skill on load and tells you which class it's in — including when a skill *can't* run here and why. A tool that's honest about its limits is more useful than one that fails mysteriously.

---

## How it works

```
┌─────────────────────────────────────────────┐
│  React SPA — chat, workspace, skill panel   │
└──────────────────┬──────────────────────────┘
                   │ REST + SSE (localhost)
┌──────────────────▼──────────────────────────┐
│  AgentOrchestrator — the tool-calling loop  │
├──────────────┬──────────────┬───────────────┤
│  Skills      │  Tools       │  Providers    │
│  loader      │  dispatcher  │  LangChain4j  │
│  classifier  │  load_skill  │  Anthropic    │
│  registry    │  bash, files │  OpenAI       │
│              │  git, edit   │  Ollama       │
├──────────────┴──────────────┴───────────────┤
│  ExecutionBackend                           │
│  · LocalWorkspace  — your repo, your machine│
│  · Docker          — sandboxed, no egress   │
│  · Docker + gVisor — syscall-filtered       │
├─────────────────────────────────────────────┤
│  SQLite  ·  ~/.incant/                      │
└─────────────────────────────────────────────┘
```

The core loop is small: inject every skill's `description` into the system prompt alongside a `load_skill` tool, let the model decide what's relevant, return the full `SKILL.md` body when it asks, and continue. That's the same mechanism Anthropic's own harness uses, and it works identically across providers because it's just tool calling.

Everything else — sandboxing, workspace access, subagents — is infrastructure hanging off that loop.

## Two modes

**Chat mode** is a normal conversation. Instruction skills load into context; document skills run their scripts in a locked-down container with no network access.

**Workspace mode** points Incant at a directory on your machine. It gets file editing, bash, and git tools, and coding-agent skills become available. There's no sandbox here — the trust model is the same as any coding agent you'd install: you chose the directory, you chose the skills, and you can watch every command in the activity log. That's a deliberate choice, not an oversight. See [Security](#security).

---

## Getting started

*Not yet — there's nothing to install. When there is, it'll look like this:*

```bash
java -jar incant.jar
```

Then open `http://localhost:8080`. On first run, Incant creates `~/.incant/` and asks for an API key, or you can point it at a local Ollama instance and skip that entirely.

**Requirements:** JDK 21+. Docker only if you want document skills.

### Adding skills

Drop skill folders into `~/.incant/skills/`. Each one needs a `SKILL.md` with `name` and `description` in the frontmatter — the standard format. Incant picks them up on refresh and tells you what class each one landed in.

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