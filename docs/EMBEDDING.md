# Embedding the ODB++ viewer

The viewer ships as a stand-alone Spring Boot service. The easiest way to drop
it into another site (e.g. the dp website alongside the gerber viewer) is an
iframe pointed at the service's root path.

## Quick start: iframe embed

```html
<iframe
    src="https://odbpp.deltaproto.com/"
    style="width:100%;height:100vh;border:0"
    allow="clipboard-write"
    title="ODB++ viewer"></iframe>
```

That's it — upload, layer toggles, pan/zoom, theme, realistic top/bottom, and
PNG download all work out of the box. The page owns its own `<html>`/`<body>`
and fills its container.

## Cross-origin API calls

If the frontend HTML is served from a *different* origin than the Java service
(for example, the HTML is hosted at `deltaproto.com` and the API at
`odbpp-api.deltaproto.com`), set `window.ODBPP_API_BASE` **before** the viewer
script runs:

```html
<script>window.ODBPP_API_BASE = 'https://odbpp-api.deltaproto.com';</script>
```

Every `fetch()` from the UI prefixes its path with that base — so it calls
`https://odbpp-api.deltaproto.com/api/odbpp/render` instead of
`/api/odbpp/render`.

The server sends `Access-Control-Allow-Origin: *` on every `/api/*` endpoint
(`@CrossOrigin` on `OdbViewController`). If you need a tighter policy, edit
that annotation — the default is deliberately permissive for open hosting.

## API surface

All endpoints are rooted at `/api/odbpp/` and are safe to call directly from a
separate frontend (e.g. your own React app, or a different viewer built on top
of this service).

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/odbpp/render` | Parse uploaded archive, return JSON with combined + realistic-top + realistic-bottom SVGs and layer metadata. Form fields: `file` (multipart), `showComponents` (bool). |
| `POST` | `/api/odbpp/thumbnail?side=top\|bottom&width=N&height=M` | Rasterise one realistic side view to PNG via Batik. Form field: `file`. Returns `image/png`. Defaults: `side=top`, `width=800`. |
| `GET`  | `/api/odbpp/example.zip` | Bundled sample archive (a small 4-layer PCB) for the "Try Example" button. |

Archives accepted: `.zip`, `.tgz`, `.tar.gz` (up to `spring.servlet.multipart.max-file-size` — 100 MB by default).

Cache-Control:
- `/api/odbpp/render` — not cached (each upload is fresh)
- `/api/odbpp/thumbnail` — `no-store` (PNG depends on the uploaded archive)
- `/api/odbpp/example.zip` — `public, max-age=3600`

## Running the service

```bash
mvn -pl odbpp-app -am spring-boot:run
# → http://localhost:8082
```

Change the port via `--server.port=N` or by editing
`odbpp-app/src/main/resources/application.properties`.

For production, build the fat jar and run it behind your reverse proxy:

```bash
mvn -pl odbpp-app -am package
java -jar odbpp-app/target/odbpp-app-*.jar --server.port=8082
```

Typical nginx/Caddy placement:

```
deltaproto.com/opensource-odbpp-viewer/  → proxy_pass http://odbpp-service:8082/
```

If you mount the viewer under a non-root path, make sure both the static HTML
(served from `/`) and the `/api/odbpp/*` endpoints are reachable — the easiest
shape is to strip the path prefix in the proxy so the Spring app keeps seeing
requests at `/`.

## What is *not* supported

- **Inline-mount into a parent div.** Both this viewer and the gerber viewer
  own their `<html>`/`<body>` and set `body { height: 100vh; display: flex }`,
  so styles would leak if you inline the HTML. Use an iframe instead. If you
  genuinely need inline mounting, Shadow DOM around the HTML is the shortest
  path; drop us a note and we can land a scoped-CSS variant.
- **Cross-origin *cookie* flows.** The API is stateless and auth-free by
  design. If you add authentication, switch `@CrossOrigin(origins = "*")` to a
  specific origin list and enable `allowCredentials = true`.

## Sibling project

The gerber viewer (`github.com/mcix/gerber`) exposes an identical shape:
`/api/gerber/render`, `/api/gerber/thumbnail`, `/api/gerber/arduino-uno-example.zip`.
Anything you can do with one, you can do with the other.
