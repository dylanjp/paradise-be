# Documentation Server API

Read-only API for browsing and retrieving Markdown documentation files. All endpoints require JWT authentication — send the token as `Authorization: Bearer <token>` on every request.

Base URL: `http://localhost:8180`

---

## Endpoints

### GET /docs/tree

Returns the full folder/file tree of available documentation.

**Response:** `200 OK` — `application/json`

```json
{
  "name": "",
  "type": "folder",
  "path": "",
  "children": [
    {
      "name": "guides",
      "type": "folder",
      "path": "guides",
      "children": [
        {
          "name": "getting-started.md",
          "type": "file",
          "path": "guides/getting-started.md",
          "children": null
        }
      ]
    },
    {
      "name": "README.md",
      "type": "file",
      "path": "README.md",
      "children": null
    }
  ]
}
```

**Node shape:**

| Field      | Type                  | Description                                              |
|------------|-----------------------|----------------------------------------------------------|
| `name`     | `string`              | File or folder name (empty string for root)              |
| `type`     | `"folder"` or `"file"`| Node type                                                |
| `path`     | `string`              | Relative path from docs root, using `/` separators       |
| `children` | `DocsTreeNode[]` or `null` | Non-null array for folders (may be empty), `null` for files |

Notes:
- The root node always has `name: ""`, `path: ""`, `type: "folder"`.
- Only `.md` files appear in the tree. Non-markdown files are excluded.
- Empty folders (no `.md` files anywhere inside) are excluded.
- Children are sorted: folders first, then files, alphabetically within each group.
- The tree is cached server-side and refreshed every 24 hours, so newly added files may take up to a day to appear.
- If the docs directory isn't configured or doesn't exist, you'll get an empty root: `{ "name": "", "type": "folder", "path": "", "children": [] }`.

---

### GET /docs/file?path={relativePath}

Returns the raw Markdown content of a single file.

**Query parameter:**

| Param  | Type     | Required | Description                                      |
|--------|----------|----------|--------------------------------------------------|
| `path` | `string` | yes      | Relative path from the tree (e.g. `guides/getting-started.md`) |

**Response:** `200 OK` — `text/markdown`

The response body is the raw Markdown string. Parse/render it however you like on the frontend.

---

## Error Responses

All errors return JSON with this shape:

```json
{
  "errorCode": "DOCS_FILE_NOT_FOUND",
  "message": "Documentation file not found: some/path.md",
  "timestamp": "2026-03-31T14:30:00"
}
```

| Status | Error Code              | When                                         |
|--------|-------------------------|----------------------------------------------|
| 400    | `DOCS_INVALID_FILE_TYPE`| Requested path doesn't end with `.md`        |
| 401    | —                       | Missing or invalid JWT token                 |
| 403    | `DOCS_PATH_TRAVERSAL`  | Path tries to escape the docs directory (e.g. `../../etc/passwd`) |
| 404    | `DOCS_FILE_NOT_FOUND`  | The `.md` file doesn't exist at that path    |

---

## TypeScript Types

```typescript
interface DocsTreeNode {
  name: string;
  type: "folder" | "file";
  path: string;
  children: DocsTreeNode[] | null;
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

// Fetch the file tree
const treeRes = await fetch(`${API_BASE}/docs/tree`, {
  headers: { Authorization: `Bearer ${token}` },
});
const tree: DocsTreeNode = await treeRes.json();

// Fetch a specific file's content
const fileRes = await fetch(
  `${API_BASE}/docs/file?path=${encodeURIComponent("guides/getting-started.md")}`,
  { headers: { Authorization: `Bearer ${token}` } },
);
const markdown: string = await fileRes.text(); // raw markdown string
```

**Remember to `encodeURIComponent` the path query param** — file paths can contain spaces or special characters.
