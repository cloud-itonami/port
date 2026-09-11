# Operator quickstart

**One check you can run offline, and two things that will mislead you if you read
the source instead.**

This repository is a migration seed: 15 tracked files extracted verbatim from
`etzhayyim/root` at `60-apps/etzhayyim-project-port` (`migration.edn` pins the
source revision and tree hash). The port ingest logic — 8.5K ports from UNCTAD and
World Port Source, UN/LOCODE identifiers, port-call tracking — **is not here**.
What is here is the edge facade (`src/app.ts`) and, since the 2026-09
Svelte→ClojureScript migration, a reagent + re-frame + jp-go-dds appview shell
under `appview/port-infra-p0rt7890/cljs/` (formerly a SvelteKit app under
`svelte/`, now removed).

Steps marked ✅ were run against this tree. The ⚠ items were measured, and in
section 2's case, measured three times as the tree changed under it.

---

## 1. The check that needs nothing installed ✅

`src/app.ts` declares its own `ExportedHandler` interface and otherwise uses only
`Request` and `Response`, which are globals. Node strips the type annotations, so
the handler runs directly — no `npm install`, no wrangler, no network. Walked on
Node v26.3.0, where `--experimental-strip-types` is a no-op (stripping is default
from Node 23); keep the flag because it is required on 22.6–22.x.

```bash
cd appview/port-infra-p0rt7890

cat > /tmp/portwalk.mjs <<'EOF'
const app = (await import(process.argv[2])).default;
for (const [label, req] of [
  ["GET /health", new Request("https://port.etzhayyim.com/health")],
  ["GET /nope  ", new Request("https://port.etzhayyim.com/nope")],
  ["bad json   ", new Request("https://port.etzhayyim.com/xrpc/com.etzhayyim.apps.port.getPort",
                              { method: "POST", body: "{not json" })],
]) {
  const res = await app.fetch(req, {});
  console.log(label, "->", res.status, await res.text());
}
EOF

node --experimental-strip-types /tmp/portwalk.mjs "$PWD/src/app.ts"
```

Actual output:

```
GET /health -> 200 {"ok":true,"actor":"did:web:p0rt7890.etzhayyim.com","nanoid":"p0rt7890",...}
GET /nope   -> 404 {"error":"NotFound","message":"port not found"}
bad json    -> 400 {"error":"InvalidJson"}
```

The third line is the one worth having: the malformed-body guard runs **before**
the upstream call, which is why this check needs no network. Hold onto it — the
next section is about that line.

---

## 2. ⚠ Neither XRPC handler is deployed right now — read this before you trust `/health`

This section described a divergence as of 2026-08-15; it has changed twice since,
and neither change was a decision, so read the history rather than trusting either
handler by default.

**2026-08-15 (original state).** `wrangler.jsonc`'s `"main"` pointed at the
SvelteKit build (`svelte/.svelte-kit/cloudflare/_worker.js`), which embedded
`svelte/src/routes/xrpc/[...path]/+server.ts` — an XRPC BFF that proxied to
`AGENTGATEWAY_MCP_ROUTER_URL` (`mcp.etzhayyim.com`) as an MCP `tools/call`. That
was the deployed handler. `src/app.ts` — a separate, never-deployed facade that
proxies a *different* upstream (`DISPATCHER_URL`, `dispatcher.etzhayyim.com`) and
has different `/health` and malformed-JSON behavior — sat unused. This is not
unique to this repository: the same 2026-08-15 measurement found 175 of 329
`cloud-itonami`/`etzhayyim` appview repos with `main` pointed at a SvelteKit
build alongside an unused `src/app.ts` facade, 89 of them exposing `/health` in
the facade with no deployed route answering it.

**2026-09-05 (a Svelte→cljs migration commit, merged without this doc being
updated).** The commit that removed `svelte/` also silently repointed `"main"`
to `./src/app.ts` and deleted `+server.ts` with no replacement. That is a
production behavior change wearing a frontend-migration commit message: the
proxy target flipped from `mcp.etzhayyim.com` to `dispatcher.etzhayyim.com`
(a binding that isn't even declared in this file's `vars`), and the
malformed-JSON behavior changed from "tool called with `{}`" to `400
InvalidJson`. Nobody decided this; it fell out of treating `src/app.ts` as
if it were the SvelteKit facade's drop-in replacement, which measurement never
supported.

**Current state (this migration).** `+server.ts`'s logic is preserved
byte-identical at `appview/port-infra-p0rt7890/src/xrpc-proxy.ts` (it imports
`@sveltejs/kit`, which is no longer a dependency, so it does not run as-is —
see its header comment). `wrangler.jsonc`'s `"main"` key has been **removed**
rather than left pointed at `src/app.ts`, because `src/app.ts` does not call
`env.ASSETS.fetch()`: leaving it as `"main"` would mean the Worker never falls
through to the `assets` binding, and every request that isn't `/health`,
`/_app/meta`, or `/xrpc/com.etzhayyim.apps.port.*` would 404 instead of serving
the appview. With `"main"` removed this is a plain assets-only Worker: the
`cljs/public` build is served for everything, and **no XRPC proxy handler is
deployed at all** — not `app.ts`'s, not the preserved-but-unwired
`xrpc-proxy.ts`'s.

So for an operator, right now: **`/xrpc/*` is not proxied to anything** (neither
upstream), and `/health` / `/_app/meta` are not served either (they were only
`app.ts` routes, and `app.ts` is no longer `"main"`). Only static assets answer.
Whether to wire up `xrpc-proxy.ts`, `app.ts`, or something else as the deployed
handler — and whether that handler should also call `env.ASSETS.fetch()` for
non-matching paths so it can coexist with the appview — is a product decision
this migration does not make. `src/app.ts`'s standalone Node walk in section 1
above still works exactly as documented; it is just not what a real request to
`port.etzhayyim.com` will reach until someone wires a handler back into
`"main"`.

## 3. ⚠ There is a test runner and there are no tests

`appview/port-infra-p0rt7890/vitest.config.ts` exists. Measured:

- **0 files** in this repository match `*.test.*` or `*.spec.*`
- `package.json` declares only `typecheck`, **no `test` script**
- all three of its `resolve.alias` targets point outside this repository, at the
  old monorepo layout, and are absent here:

  ```bash
  ls -d appview/port-infra-p0rt7890/../../../../40-engine        # absent
  ls -d appview/port-infra-p0rt7890/../../../../../com-etzhayyim-xrpc  # absent
  ```

A configured runner with no tests and unresolvable aliases is worth knowing about
because it reads, from a file listing, exactly like a tested repository.

## 4. Build the appview ✅

`appview/port-infra-p0rt7890/cljs/` is a shadow-cljs (reagent + re-frame +
jp-go-dds) build; `wrangler.jsonc`'s `assets.directory` points at
`./cljs/public`. Go through the repo-wide resource governor rather than
invoking the build directly — high-load builds are limited to one at a time
across the workspace:

```bash
cd appview/port-infra-p0rt7890/cljs
npm install
node <root>/scripts/resource-guard.mjs run build -- amu compile --target wasm32-browser app
```

This was run as part of the Svelte→cljs migration:
`[:app] Build completed. (111 files, 110 compiled, 0 warnings, 18.76s)`, exit 0,
`public/js/app.js` (~3.2 MB) produced.
Deployment (`wrangler deploy` / `wrangler versions deploy`) additionally needs
Cloudflare credentials and, per the workspace rule, a checkout that contains
`origin/main` — deploys have no fast-forward check, so the last writer wins.
**Deployment itself was not run as part of this migration** — see section 2
above for why `"main"` was removed rather than repointed, and confirm the
intended request-routing behavior before deploying.

---

## 5. Where the actual behaviour lives

From `src/app.ts` and `CLAUDE.md`, none of it verifiable from here:

| Thing | Where |
|---|---|
| Actor DID | `did:web:p0rt7890.etzhayyim.com`, nanoid `p0rt7890` |
| Ingest | `kotodama/ingest/port.py` in the kotoba engine |
| BPMN | `00-contracts/bpmn/com/etzhayyim/port/` in `etzhayyim/root` |
| Identifier | UN/LOCODE, 5 characters |
| Cross-actor links | `vessel` (port calls), `cargo` (loading/discharge port DIDs), `bunker`, `oil-distribution`, `oil-shipping` (chokepoint exposure) |
