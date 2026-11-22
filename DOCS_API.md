# Documentation Server API

Read-only API for browsing Obsidian-style documentation: Markdown notes, Canvas boards, PDFs, and images, plus the images and PDFs those notes embed.

Every endpoint requires JWT authentication. Send the token as `Authorization: Bearer <token>` on every request. There is no auth entry point, so a request **without a token (or with an invalid one) gets `403 Forbidden`**, not 401. Tokens are never accepted in query strings, so the frontend must `fetch` binary files and display them through a Blob / object URL.

Base URL: `http://localhost:8180`

---

## Sections

The docs come from one Obsidian vault (`DOCS_VAULT_PATH`). Each listed top-level folder of the vault is a **section**: a top-level folder of the tree with the same name. Tree paths are therefore the same as vault paths (`Home and Family/House Documentation/...`).

| Section (default) | Listed in | Who can see and read it |
|-------------------|-----------|-------------------------|
| `Home and Family` | `DOCS_FOLDERS` | Every signed-in user |
| `Dylan Personal` | `DOCS_ADMIN_FOLDERS` | `ROLE_ADMIN` only |
| `LLCs` | `DOCS_ADMIN_FOLDERS` | `ROLE_ADMIN` only |

- `DOCS_FOLDERS` and `DOCS_ADMIN_FOLDERS` are comma-separated folder names. A name must be a single folder directly under the vault: no `/`, `\`, `:` or leading `.`. Invalid names and duplicates are logged and ignored. A folder in both lists is admin-only.
- Vault content outside the listed sections (`Attachments/`, notes at the vault root, other folders) is never listed or opened from the tree. Notes can still embed images and PDFs from it; see `/docs/embed`.
- An unset `DOCS_VAULT_PATH` disables the feature. A section that doesn't exist (for example, an unmounted drive or a renamed folder) is skipped and re-checked on every refresh, so it appears once the folder exists. An existing section with no listed files is still shown.
- For users without `ROLE_ADMIN`, admin-only sections are **removed from the tree**, and every request for a path under them returns **404**, whether or not the file exists. Their existence is never revealed.
- Routing is by exact first path segment, which must be a section's name (case-sensitive). Anything else returns **404**.
- Every section shares the vault's scope for embeds, so admin-only sections are also hidden from non-admins when an open note embeds a file stored in one.

Configuration (`application.properties`):

```properties
docs.vault-path=${DOCS_VAULT_PATH:}
docs.folders=${DOCS_FOLDERS:Home and Family}
docs.admin-folders=${DOCS_ADMIN_FOLDERS:Dylan Personal,LLCs}
docs.refresh-millis=3600000   # auto-refresh interval, default 1 hour
```

Before 2026-11-21 (v1.0.4) the tree combined three separate vaults (`DOCS_PATH` at the tree root, plus the admin-only `LE Docs` and `Pratt Capitol` folders). Those variables are now ignored.

---

## File types

Only these extensions are listed in the tree or served. Matching is case-insensitive.

| Kind | Extensions | Served by | `Content-Type` |
|------|------------|-----------|----------------|
| Markdown | `md` | `GET /docs/file` | `text/markdown;charset=UTF-8` |
| Canvas | `canvas` | `GET /docs/file` | `application/json` (UTF-8) |
| PDF | `pdf` | `GET /docs/raw`, `GET /docs/embed` | `application/pdf` |
| Images | `png`, `jpg`, `jpeg`, `gif`, `webp`, `svg`, `bmp`, `avif` | `GET /docs/raw`, `GET /docs/embed` | `image/png`, `image/jpeg`, `image/gif`, `image/webp`, `image/svg+xml`, `image/bmp`, `image/avif` |

- Media types come from a fixed map. They never depend on the host OS.
- Text documents larger than **32 MB** return `413`. Text is decoded as UTF-8 leniently: invalid bytes become U+FFFD, and a leading BOM is removed.
- Binary files have no size cap and are streamed.
- Names starting with `.` are never listed or served, at any depth. This covers `.obsidian`, `.trash`, and `.git`.

---

## Endpoints

### GET /docs/tree

Returns the folder/file tree visible to the caller.

**Response:** `200 OK`, `application/json`

```json
{
  "name": "",
  "type": "folder",
  "path": "",
  "children": [
    {
      "name": "Home and Family",
      "type": "folder",
      "path": "Home and Family",
      "root": true,
      "children": [
        {
          "name": "Insurance",
          "type": "folder",
          "path": "Home and Family/Insurance",
          "children": [
            {
              "name": "ID Card.pdf",
              "type": "file",
              "path": "Home and Family/Insurance/ID Card.pdf",
              "children": null
            }
          ]
        }
      ]
    },
    {
      "name": "LLCs",
      "type": "folder",
      "path": "LLCs",
      "root": true,
      "children": [
        {
          "name": "Amira Board.canvas",
          "type": "file",
          "path": "LLCs/LegendaryEpics/IP Management/Amira Board.canvas",
          "children": null
        }
      ]
    }
  ]
}
```

(The `Amira Board.canvas` node is shortened. Its real parent folders are omitted. `LLCs` appears only for admins.)

**Node shape:**

| Field      | Type                       | Description |
|------------|----------------------------|-------------|
| `name`     | `string`                   | File or folder name (empty string for the tree root). |
| `type`     | `"folder"` or `"file"`     | Node type. |
| `path`     | `string`                   | Path to pass to the other endpoints, using `/` separators. It is the file's path in the vault, so it always starts with its section (`Home and Family/...`). |
| `children` | `DocsTreeNode[]` or `null` | Non-null array for folders, `null` for files. |
| `root`     | `true` (optional)          | Present only on a section's top-level folder. **Omitted** on every other node, including the tree root. |

Notes:
- The tree root always has `name: ""`, `path: ""`, `type: "folder"`, and no `root` field. Its children are the sections the caller may see, and nothing else.
- Only the file types above are listed. Folders with no listed files anywhere inside are pruned. A section's folder is always shown, even when it is empty.
- Children are sorted folders first, then case-insensitively by name.
- Symlinks and Windows junctions are followed only when their real target stays inside the same root. A folder already visited through another link is skipped, so link loops terminate.
- The tree is cached server-side. It is rebuilt every `docs.refresh-millis` (default **1 hour**) and on `POST /docs/refresh`.
- With no roots configured, the response is an empty root: `{ "name": "", "type": "folder", "path": "", "children": [] }`.

---

### POST /docs/refresh

Rescans every root, rebuilding the tree and the embed index, then returns the refreshed tree filtered for the caller.

- **Single-flight.** Concurrent calls share one rebuild.
- **Throttled.** Within 10 seconds of the last rebuild, the current tree is returned without rescanning.
- If a rebuild fails, the previous tree is kept.

**Request body:** none.

**Response:** `200 OK`, `application/json`. Same shape as `GET /docs/tree`.

---

### GET /docs/file?path={treePath}

Returns the text of a Markdown or Canvas document.

| Param  | Type     | Required | Description |
|--------|----------|----------|-------------|
| `path` | `string` | yes      | A file `path` from the tree, e.g. `Home and Family/Insurance/Insurance Overview.md` or `LLCs/LegendaryEpics/IP Management/Amira Board.canvas`. |

**Response:** `200 OK`
- `.md` → `text/markdown;charset=UTF-8`, raw Markdown.
- `.canvas` → `application/json`, the canvas file exactly as stored (`{"nodes":[...],"edges":[...]}`; may be `{}`).

**Errors:** `400` for other types, `403` traversal, `404` missing, hidden, or not permitted, `413` over 32 MB.

---

### GET /docs/raw?path={treePath}

Streams a PDF or image listed in the tree.

| Param  | Type     | Required | Description |
|--------|----------|----------|-------------|
| `path` | `string` | yes      | A file `path` from the tree, e.g. `Home and Family/Insurance/Car Insurance/ID Card.pdf`. |

**Response:** `200 OK` with the file bytes. Headers:

| Header | Value |
|--------|-------|
| `Content-Type` | From the type table above. |
| `Content-Disposition` | `inline; filename*=UTF-8''<encoded name>` |
| `Content-Length` | File size. |
| `Accept-Ranges` | `bytes`. `Range: bytes=a-b` requests get `206 Partial Content`. |
| `Cache-Control` | `no-cache, no-store, max-age=0, must-revalidate` (Spring Security default, kept on purpose so vault files never land in disk caches). |
| `X-Content-Type-Options` | `nosniff` |
| `Content-Security-Policy` | **SVG only:** `default-src 'none'; style-src 'unsafe-inline'; sandbox` |

**Errors:** `400` for md/canvas or unlisted types, `403` traversal, `404` missing, hidden, or not permitted.

Every successful call writes an audit log line: `AUDIT docs.raw user=<username> root=<section> path=<vault-relative path>`.

---

### GET /docs/embed?from={treePath}&target={target}&literal={bool}

Resolves an image or PDF that a note or canvas embeds, the way Obsidian does, and streams it. Use this for `![[...]]`, relative `![alt](...)` images, and canvas `file` nodes. Use `/docs/raw` for files opened from the tree.

| Param     | Type      | Required | Description |
|-----------|-----------|----------|-------------|
| `from`    | `string`  | yes      | Tree path of the `.md` or `.canvas` document that contains the embed. |
| `target`  | `string`  | yes      | The embed target as written. Examples: `Official_Amira_Artwork2.jpg`, `Attachments/Pictures/Amira/pic.jpg`, `pic.png\|300`, `../img/My Pic.png`. Send it URL-encoded as a normal query parameter. The server does **not** decode it a second time. |
| `literal` | `boolean` | no (default `false`) | `false`: strip Obsidian suffixes `\|alias`, `\|300`, `#heading`, `^block` before resolving. `true`: use the name as-is. **Use `true` for canvas `file` values**, which can legitimately contain `#` (e.g. `#ClairLineArt.jpg`). |

**Target cleaning:**
1. Strip the suffixes described under `literal`.
2. Trim, apply Unicode NFC normalization, and convert `\` to `/`.
3. Strip every leading `/` and `./`. A leading slash means the vault root.
4. Reject NUL, drive prefixes (`C:`), UNC/device/rooted paths, and anything the OS can't parse. These return `404`.
5. The target must end in a PDF or image extension. Otherwise the request returns `400`.

**Resolution.** Everything happens within the **vault scope** of the section that `from` belongs to:
- The vault scope is the nearest folder at or above the section that contains `.obsidian`: normally the vault itself, shared by every section. If there is none, it is the section folder.
- Example: a canvas in `Home and Family/` can show images from the vault-level `Attachments/`.
- Files inside an admin-only section are never served to a non-admin, even when an open note embeds them.

1. **`from` check.** `from` must be a readable `.md` / `.canvas` tree file for the caller, with the same rules as `/docs/file`.
2. **Reference gate.** `from` must actually reference the target. The server extracts the targets of `[[...]]`, `![[...]]`, Markdown link/image destinations (raw and percent-decoded, `<...>` form, and reference definitions), and, for canvases, every node's `file` plus the Markdown in `text` nodes. Unreferenced targets return `404`.
3. **Ancestor walk.** The server tries `target` relative to `from`'s folder, then each parent folder up to the vault scope. This covers inner-vault paths like `Attachments/Pictures/...` from a nested vault, and outer-vault paths like `IP Management/...`.
4. **Name index.** If nothing matched, the server looks for files with the same name anywhere in the scope. Names are compared case-insensitively and NFC-normalized.
   - If `target` contains folders, the file's scope-relative path must equal `target` or end with `/target`.
   - Candidates are ranked by most folders shared with `from`'s folder, then fewest path segments, then alphabetically.
5. **Final checks.** Every candidate must be a regular file whose **real** path (symlinks and junctions resolved) is inside the scope, has no dot-folder in its path, and is a PDF or image.
6. If nothing qualifies, the request returns `404`.

**Security note:** the vault scope can be wider than the configured folder, but the reference gate means the widened scope only exposes files that a note the caller can already read actually embeds. Files in dot-folders are never indexed or served, and error messages never contain absolute paths.

**Response:** identical to `/docs/raw`: same headers, Range support, SVG CSP, and `no-store`.

Every successful call writes an audit log line: `AUDIT docs.embed user=<username> root=<...> path=<vault-scope-relative path>`.

**Errors:** `400` when `from` is not md/canvas or `target` is not a PDF/image, `403` when `from` escapes its root, `404` when `from` is missing or not permitted, or `target` is unsafe, unreferenced, or not found.

---

## Path rules (all endpoints)

Checks run in this order. The purely textual checks happen before any filesystem access.

1. Blank or NUL → `404`.
2. A leading `/` or `\`, a drive prefix (`C:`), or a rooted path after routing → `403 DOCS_PATH_TRAVERSAL`.
3. A first segment that isn't a section name, or an admin-only section for a non-admin → `404`. Examples: `../x.md`, `Attachments/pic.png`.
4. After routing to a section and normalizing `.`/`..`, a path outside that section → `403 DOCS_PATH_TRAVERSAL`. Examples: `Home and Family/../Welcome.md`, `Home and Family/../LLCs/x.md`.
5. Any path element starting with `.` → `404`. Examples: `.obsidian/app.json`, `Home and Family/Insurance/../.trash/x.md`.
6. Wrong extension for the endpoint → `400`.
7. The file must exist and be a regular file. Its real path must stay inside the real section folder, contain no dot-element, and have an allowed extension. Otherwise → `404`.
8. Paths the OS can't parse → `404`. Examples: `a:b.md` or `x<y>.md` on Windows. These never produce a generic validation error.

---

## Error Responses

All errors return JSON with this shape:

```json
{
  "errorCode": "DOCS_FILE_NOT_FOUND",
  "message": "Documentation file not found: some/path.md",
  "timestamp": "2026-09-28T14:30:00"
}
```

| Status | Error Code               | When |
|--------|--------------------------|------|
| 400    | `DOCS_INVALID_FILE_TYPE` | Wrong extension for the endpoint, e.g. a `.pdf` to `/docs/file`, a `.md` to `/docs/raw`, or an embed `target` that isn't a PDF/image. |
| 400    | (Spring default)         | A required query parameter is missing. |
| 403    | —                        | Missing or invalid JWT token (no body contract). |
| 403    | `DOCS_PATH_TRAVERSAL`    | Path escapes its root, or is absolute or drive-prefixed. |
| 404    | `DOCS_FILE_NOT_FOUND`    | Missing, hidden (dot-path), not under a section, outside the real section folder, in an admin-only section for a non-admin, unparsable, or an embed that is unreferenced or unresolved. |
| 413    | `DOCS_FILE_TOO_LARGE`    | A text document is larger than 32 MB. |

---

## TypeScript Types

```typescript
interface DocsTreeNode {
  name: string;
  type: "folder" | "file";
  path: string;
  children: DocsTreeNode[] | null;
  root?: true; // only on section folders ("Home and Family", "LLCs", ...)
}

interface DocsErrorResponse {
  errorCode: string;
  message: string;
  timestamp: string;
}
```

---

## Example Usage

```typescript
const API_BASE = "http://localhost:8180";
const auth = { headers: { Authorization: `Bearer ${token}` } };

// Tree (filtered for the caller)
const tree: DocsTreeNode = await (await fetch(`${API_BASE}/docs/tree`, auth)).json();

// Refresh after adding notes
const fresh: DocsTreeNode = await (
  await fetch(`${API_BASE}/docs/refresh`, { method: "POST", ...auth })
).json();

// Markdown or canvas text
const q = (o: Record<string, string>) => new URLSearchParams(o).toString();
const md = await (await fetch(`${API_BASE}/docs/file?${q({ path: "Home and Family/Insurance/Insurance Overview.md" })}`, auth)).text();

// PDF or image from the tree → Blob → object URL
const pdf = await (await fetch(`${API_BASE}/docs/raw?${q({ path: "Home and Family/Insurance/Car Insurance/ID Card.pdf" })}`, auth)).blob();
const pdfUrl = URL.createObjectURL(pdf);

// Image embedded by a canvas file node (literal) or a note (default)
const img = await (
  await fetch(
    `${API_BASE}/docs/embed?${q({
      from: "LLCs/LegendaryEpics/IP Management/Lamaryah WorldBuilding/Characters/Amira/Amira Board.canvas",
      target: "Attachments/Pictures/Amira/Official_Amira_Artwork1.jpg",
      literal: "true",
    })}`,
    auth,
  )
).blob();
```

**Always encode query parameters** (`URLSearchParams` or `encodeURIComponent`). Paths contain spaces, `&`, `#`, and non-ASCII characters. Never put the token in a URL.
