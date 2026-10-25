# SYNAPSE — AI-Powered Legacy Software Knowledge Graph Generation System

> **Transform legacy codebases into living, searchable knowledge graphs with AI-written documentation — running entirely on your own machine, at zero cost.**

Major Project — Department of Information Technology
Team: Akkala Sriharsh · Pullakhandum Dhanush · Preetham Pulumamidi · Thota Nikhil Karthikeya
Supervisor: Shruti Patil, Assistant Professor

---

## What it does

Point Synapse at a **GitHub repository** (public or private) or upload a **ZIP archive**. It then:

1. **Parses every file** — full syntax trees for 12 languages via tree-sitter (Java, Kotlin, Python, JavaScript, TypeScript, Go, C, C++, C#, PHP, Ruby, Rust), pattern-based extraction for 25+ more (COBOL, Scala, Swift, SQL, Fortran, VB, Pascal, Shell, Perl, Lua, R, Dart, Elixir, Haskell, …), plus Vue/Svelte script blocks, configs and docs.
2. **Builds a Developer Knowledge Graph** — packages/folders, files, classes, methods, functions, HTTP API endpoints and third-party libraries, linked by *contains, imports, calls, creates, extends, implements, handles* and *depends on* relationships, resolved across files.
3. **Writes documentation with a local AI model** — per-file and per-symbol summaries, plus `README.md`, `ARCHITECTURE.md` (with a module dependency diagram), `BUSINESS_LOGIC.md` and an `API.md` reference.
4. **Lets you explore it** — interactive graph viewer, full-text search, impact analysis ("what breaks if I change this?"), source viewer, documentation viewer and ZIP export.

Accounts are secured with BCrypt-hashed passwords and JWT sessions; every user's graphs and documents are stored centrally in PostgreSQL.

## Architecture

```
React + Vite (frontend, :5173)  ──REST/JWT──▶  Spring Boot 4 (backend, :8080)
                                                 ├─ JGit ............ clone GitHub repos (token used once, never stored)
                                                 ├─ tree-sitter ..... syntax trees for 12 languages
                                                 ├─ heuristics ...... 25+ more languages, endpoints, manifests
                                                 ├─ graph builder ... cross-file resolution of imports / calls / types
                                                 ├─ Spring AI ─────▶ Ollama (qwen2.5-coder:7b, local GPU)
                                                 └─ PostgreSQL 17 ... users, projects, graph, docs (full-text search)
```

| Layer | Technology | Cost |
|---|---|---|
| Frontend | React 19, Vite, Framer Motion, Cytoscape.js (fcose), react-markdown, Mermaid | Free / open source |
| Backend | Java 21, Spring Boot 4, Spring Security (JWT), Spring Data JPA, Flyway | Free / open source |
| AI | Spring AI 2 + Ollama running `qwen2.5-coder:7b` locally | Free, offline |
| Parsing | tree-sitter grammars (bonede Java bindings), JGit | Free / open source |
| Database | PostgreSQL 17 in Docker | Free |

## Getting started

### Prerequisites

- **Java 21** (Temurin recommended) — Maven is bundled via `mvnw`
- **Node.js 18+**
- **Docker Desktop** (runs PostgreSQL)
- **Ollama** from <https://ollama.com> — a GPU with 6 GB+ VRAM is recommended for the 7B model (it also runs on CPU, slower)

### One command

```powershell
powershell -ExecutionPolicy Bypass -File .\start.ps1     # Windows
```
```bash
./start.sh                                                # macOS / Linux
```

The script creates a local `.env` with random secrets, starts PostgreSQL, pulls the AI model on first run (~4.7 GB), starts the backend and opens the frontend at **http://localhost:5173/synapse/**.

### Manual steps

```bash
docker compose up -d                      # PostgreSQL on localhost:5433 (credentials from .env)
ollama pull qwen2.5-coder:7b              # one-time
cd backend && ./mvnw spring-boot:run      # API on http://localhost:8080
npm install && npm run dev                # UI on http://localhost:5173/synapse/
```

The `.env` file (repo root, gitignored) holds `POSTGRES_*`, `SYNAPSE_DB_PORT`, `SYNAPSE_JWT_SECRET`, `OLLAMA_BASE_URL` and `OLLAMA_MODEL`. Point the frontend at another API with `VITE_API_URL`.

### Private repositories

Paste a GitHub **personal access token** (fine-grained, *Contents: read*) in the *Access token* field. It is used for that single clone and then discarded — never stored or logged.

### Analysis depth

| Depth | Files summarised by AI | Typical time (RTX 5060) |
|---|---|---|
| Quick | 10 | 2–3 min |
| Standard | 25 | 3–5 min |
| Deep | 60 | 8–12 min |

Parsing and graph building take seconds even for thousands of files; the knowledge graph is explorable while the AI is still writing documentation. Without Ollama the analysis still completes with static documentation.

## API overview

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/register`, `/api/auth/login` | Create account / sign in (returns JWT) |
| `GET` | `/api/auth/me` | Current user |
| `GET` | `/api/system/status` | Is the local AI model ready? |
| `GET` | `/api/projects` | List your projects |
| `POST` | `/api/projects/github` | Analyse a GitHub repo `{repoUrl, branch?, token?, depth}` |
| `POST` | `/api/projects/upload` | Analyse a ZIP (multipart `file`, `name?`, `depth?`) |
| `POST` | `/api/projects/{id}/reanalyze` | Re-run a GitHub analysis |
| `GET` | `/api/projects/{id}/graph?kinds=` | Graph nodes and edges |
| `GET` | `/api/projects/{id}/nodes/{nodeId}` | Node detail, source snippet, neighbours |
| `GET` | `/api/projects/{id}/nodes/{nodeId}/impact` | Everything that depends on a node |
| `GET` | `/api/projects/{id}/search?q=` | Full-text search |
| `GET` | `/api/projects/{id}/documents` · `/documents/export` | Generated docs · ZIP export |

## Tests

```bash
cd backend && ./mvnw test
```

Covers multi-language extraction (13 languages including COBOL and Scala), cross-file graph resolution, endpoint detection and module detection.

## Repository structure

```
SYNAPSE/
├── backend/                       Spring Boot service
│   └── src/main/java/com/synapse/
│       ├── auth/                  registration, login, JWT
│       ├── project/               project lifecycle API
│       ├── analysis/              pipeline orchestration
│       │   ├── ingest/            GitHub clone, ZIP unpack, file scanning
│       │   ├── parse/             tree-sitter + heuristic extractors, imports, endpoints, manifests
│       │   └── graph/             knowledge graph builder
│       ├── graph/                 graph storage, search, impact analysis API
│       ├── ai/                    Ollama client and documentation generation
│       └── docs/                  generated documents API
├── src/                           React frontend
│   ├── pages/Landing/             marketing site
│   ├── pages/Login, SignUp/       authentication
│   ├── pages/Dashboard/           projects + new analysis
│   ├── pages/Project/             overview, graph explorer, docs viewer, API endpoints
│   ├── api/, context/             API client, auth and theme state
│   └── styles/                    design tokens and app primitives
├── docker-compose.yml             PostgreSQL
└── start.ps1 / start.sh           one-command local start
```

## Design system

- **Dark mode**: deep brown background (`#5D4439`) with silver surfaces (`#C4C8D0`); **light mode**: warm salmon (`#FEC19F`).
- Square-edge CAD bento grids with `2px` solid black borders, **Guldenz** display type and **ClashDisplay** body type, single blue accent.

## Future enhancements

UML diagram generation · IDE integration · CI/CD hooks · incremental graph and documentation updates.

## License

MIT © [Nikhil Kthota](https://github.com/nikhil-kthota)
