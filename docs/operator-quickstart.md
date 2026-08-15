# Operator quickstart

**One check you can run offline, and two things that will mislead you if you read
the source instead.**

This repository is a migration seed: 15 tracked files extracted verbatim from
`etzhayyim/root` at `60-apps/etzhayyim-project-port` (`migration.edn` pins the
source revision and tree hash). The port ingest logic — 8.5K ports from UNCTAD and
World Port Source, UN/LOCODE identifiers, port-call tracking — **is not here**.
What is here is the edge facade and its Svelte appview.

Steps marked ✅ were run against this tree. The ⚠ items were measured. The one
marked NOT WALKED says why.

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

## 2. ⚠ The file you just ran is not the file that deploys

Measured against this tree on 2026-08-15:

| | `src/app.ts` | `svelte/src/routes/xrpc/[...path]/+server.ts` |
|---|---|---|
| Reached by `wrangler deploy`? | **no** | **yes** |
| Named as an entry point | `package.json` `"main"` | — |
| Imported by any code here? | no | yes, by SvelteKit routing |
| `/health` | yes | **no route serves it** |
| Upstream | `dispatcher.etzhayyim.com` | `mcp.etzhayyim.com` as an MCP `tools/call` |
| Malformed JSON body | `400 InvalidJson` | `.catch(() => ({}))` — **the tool is called with `{}`** |

Confirm the first and fourth rows yourself:

```bash
grep '"main"' appview/port-infra-p0rt7890/wrangler.jsonc
#     "main": "svelte/.svelte-kit/cloudflare/_worker.js",

# -c prints a count per file, so read the zeros rather than expecting silence:
grep -rc health appview/port-infra-p0rt7890/svelte/src/ ; echo "exit=$?"
#   appview/port-infra-p0rt7890/svelte/src/app.html:0
#   appview/port-infra-p0rt7890/svelte/src/routes/+page.svelte:0
#   appview/port-infra-p0rt7890/svelte/src/routes/xrpc/[...path]/+server.ts:0
#   exit=1
```

Three files, zero matches in each, exit 1. The output above is what actually
prints, noise included — a step whose shown output does not match what happens
teaches the reader to stop reading it.

**This is not a quirk of this repository.** Measured across the 329 appview
repositories in `cloud-itonami` and `etzhayyim` that carry a `wrangler.jsonc`:

| | count |
|---|---|
| `main` points at the SvelteKit build | 175 |
| ships a `src/app.ts` facade | 147 |
| **`/health` exists in the facade and in no deployed route** | **89** |
| deployed route turns a malformed body into `{}` | 118 |
| facade returns `400 InvalidJson` while the deployed route silently sends `{}` | 58 |

So for an operator: **do not health-check this service at `/health`**, and do not
assume a malformed request fails. Both beliefs come from reading the facade, and
the facade is not what answers. Which of the two handlers is authoritative is a
decision for the app's owner; this document names the divergence and does not
resolve it.

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

## 4. Build or deploy the appview ⚠ NOT WALKED

`svelte/` is a SvelteKit app and there is no lockfile and no `node_modules` here,
so a build needs a network install. It was not run while writing this and is
therefore not claimed to work. If you run it, go through the repo-wide resource
governor rather than invoking the build directly — high-load builds are limited to
one at a time across the workspace:

```bash
node <root>/scripts/resource-guard.mjs run build -- \
  npm --prefix appview/port-infra-p0rt7890/svelte run build
```

Deployment additionally needs Cloudflare credentials and, per the workspace rule,
a checkout that contains `origin/main` — deploys have no fast-forward check, so
the last writer wins.

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
