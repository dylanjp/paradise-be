# MyDrive API Reference

## Authentication

All endpoints require a JWT Bearer token in the `Authorization` header:

```
Authorization: Bearer <token>
```

Obtain a token via `POST /auth/login`.

---

## Base URL

```
/users/{userId}/drives/{driveKey}
```

### Drive Keys

| Key           | Description                  |
|---------------|------------------------------|
| `myDrive`     | User's personal drive        |
| `sharedDrive` | Shared/collaborative drive   |
| `adminDrive`  | Admin-only drive             |
| `mediaCache`  | Media cache drive            |

---

## Data Types

### DriveItem

Every file and folder is represented as a `DriveItem`:

```json
{
  "id": "a1b2c3d4",
  "name": "Documents",
  "type": "folder",
  "fileType": null,
  "size": null,
  "color": "#4A90D9",
  "children": ["x1y2z3", "m4n5o6"],
  "parentId": "root-id"
}
```

| Field      | Type       | Description                                                                 |
|------------|------------|-----------------------------------------------------------------------------|
| `id`       | `string`   | Unique item ID (8-char hex)                                                 |
| `name`     | `string`   | Display name of the file or folder                                          |
| `type`     | `string`   | `"folder"` or `"file"`                                                      |
| `fileType` | `string?`  | File extension (e.g. `"pdf"`, `"png"`). `null` for folders                  |
| `size`     | `string?`  | Human-readable size (e.g. `"1.5 MB"`). `null` for folders                   |
| `color`    | `string?`  | Hex color code for folder customization. `null` if not set                  |
| `children` | `string[]` | Array of child item IDs. Empty array `[]` for files                         |
| `parentId` | `string?`  | ID of the parent folder. `null` for the root folder                         |

### ErrorResponse

All errors return this shape:

```json
{
  "errorCode": "DRIVE_ITEM_NOT_FOUND",
  "message": "Item not found: a1b2c3d4",
  "timestamp": "2026-02-24T12:00:00"
}
```

---

## Endpoints

### 1. List Drive Contents

```
GET /users/{userId}/drives/{driveKey}
```

Returns a flat map of all items in the drive, keyed by item ID. The root folder's `children` array defines the top-level contents.

**Response:** `200 OK`

```json
{
  "root-id": {
    "id": "root-id",
    "name": "My Drive",
    "type": "folder",
    "fileType": null,
    "size": null,
    "color": null,
    "children": ["abc123", "def456"],
    "parentId": null
  },
  "abc123": {
    "id": "abc123",
    "name": "photo.png",
    "type": "file",
    "fileType": "png",
    "size": "2.3 MB",
    "color": null,
    "children": [],
    "parentId": "root-id"
  }
}
```

---

### 2. Create Folder

```
POST /users/{userId}/drives/{driveKey}/folders
Content-Type: application/json
```

**Request Body:**

```json
{
  "name": "New Folder",
  "parentId": "root-id"
}
```

| Field      | Type     | Required | Description                        |
|------------|----------|----------|------------------------------------|
| `name`     | `string` | Yes      | Folder name                        |
| `parentId` | `string` | Yes      | ID of the parent folder            |

**Response:** `201 Created` — returns the new `DriveItem`

---

### 3. Upload File

```
POST /users/{userId}/drives/{driveKey}/files
Content-Type: multipart/form-data
```

**Form Fields:**

| Field      | Type             | Required | Description                        |
|------------|------------------|----------|------------------------------------|
| `file`     | `File`           | Yes      | The file to upload                 |
| `parentId` | `string`         | Yes      | ID of the parent folder            |

**Response:** `201 Created` — returns the new `DriveItem`

---

### 4. Download File

```
GET /users/{userId}/drives/{driveKey}/items/{itemId}/download
```

Streams the file with auto-detected `Content-Type` and `Content-Disposition: attachment` header. Folders cannot be downloaded.

**Response:** `200 OK` — binary file stream

**Response Headers:**
- `Content-Type`: auto-detected MIME type (falls back to `application/octet-stream`)
- `Content-Disposition`: `attachment; filename="original-name.ext"`

---

### 5. Update Item (Rename / Recolor)

```
PUT /users/{userId}/drives/{driveKey}/items/{itemId}
Content-Type: application/json
```

**Request Body:**

```json
{
  "name": "Renamed Folder",
  "color": "#FF5733"
}
```

| Field   | Type      | Required | Description                                    |
|---------|-----------|----------|------------------------------------------------|
| `name`  | `string?` | No       | New name. Renames the file/folder on disk       |
| `color` | `string?` | No       | New hex color (folders only)                    |

Both fields are optional. Only provided fields are applied.

**Response:** `200 OK` — returns the updated `DriveItem`

---

### 6. Delete Item

```
DELETE /users/{userId}/drives/{driveKey}/items/{itemId}
```

Deletes a file or folder (recursively). The root folder cannot be deleted.

**Response:** `204 No Content`

---

## Plex Upload

```
POST /users/{userId}/plex/upload
Content-Type: multipart/form-data
```

Uploads a file directly to the Plex media cache.

**Form Fields:**

| Field  | Type   | Required | Description          |
|--------|--------|----------|----------------------|
| `file` | `File` | Yes      | The file to upload   |

**Response:** `200 OK`

```json
{
  "fileName": "movie.mkv",
  "size": "1.4 GB"
}
```

---

## Error Codes

| Error Code              | HTTP Status | Description                                      |
|-------------------------|-------------|--------------------------------------------------|
| `DRIVE_ACCESS_DENIED`   | `403`       | User does not have access to this drive           |
| `INVALID_DRIVE_KEY`     | `400`       | Drive key is not one of the valid keys            |
| `DRIVE_ITEM_NOT_FOUND`  | `404`       | The requested item ID does not exist              |
| `DRIVE_ITEM_CONFLICT`   | `409`       | A file/folder with that name already exists       |
| `DRIVE_ROOT_DELETION`   | `400`       | Cannot delete the root folder of a drive          |
| `DOWNLOAD_FOLDER`       | `400`       | Cannot download a folder (only files)             |
| `DRIVE_UNAVAILABLE`     | `503`       | The drive's backing filesystem is not accessible  |
