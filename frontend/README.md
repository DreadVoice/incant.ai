# Incant frontend

The React single-page app for Incant, built with Vite and TypeScript, styled with Tailwind CSS and shadcn/ui.

## Requirements

Node.js 22 or newer.

## Commands

Run these from this directory:

| Command | What it does |
| --- | --- |
| `npm install` | Install dependencies |
| `npm run dev` | Start the dev server on http://localhost:5173 |
| `npm run build` | Type-check, then build to `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run lint` | Run Oxlint |

The dev server proxies `/api` to `http://localhost:8080`, so start the Spring Boot application alongside it.

Imports beginning with `@/` resolve to `src/`. shadcn/ui components live in `src/components/ui`, and the theme tokens are defined in `src/index.css`.
