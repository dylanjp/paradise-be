# Requirements Document

## Introduction

This document defines the requirements for two enhancements to the MyDrive feature in the Paradise backend application. The first enhancement adds the ability to move files and folders between directories within a drive. The second enhancement introduces server-side caching for drive contents (the flat map response) to reduce page load times, particularly for the mediaCache drive.

## Glossary

- **MyDrive_Service**: The backend service (`MyDriveService`) responsible for managing file and folder operations across all drive types.
- **MyDrive_Controller**: The REST controller (`MyDriveController`) that exposes drive operations as HTTP endpoints.
- **Drive_Item**: A file or folder within a virtual drive, represented as a `DriveItem` record with id, name, type, fileType, size, color, children, and parentId.
- **Flat_Map**: A `Map<String, DriveItem>` response keyed by item ID that represents the full contents of a drive, returned by the `getDriveContents` endpoint.
- **Drive_Key**: An enum identifying the type of drive: `myDrive`, `sharedDrive`, `adminDrive`, or `mediaCache`.
- **Drive_Cache**: A server-side in-memory cache that stores pre-computed Flat_Map responses for drives to avoid repeated filesystem traversal.
- **Move_Request**: A DTO containing the item ID to move and the destination parent folder ID.
- **Cache_TTL**: The time-to-live duration for cached drive contents before the cache entry is considered stale and must be refreshed.

## Requirements

### Requirement 1: Move Files and Folders

**User Story:** As a drive user, I want to move files and folders to different locations within my drive, so that I can organize my content without having to re-upload or recreate items.

#### Acceptance Criteria

1. WHEN a valid move request is received, THE MyDrive_Controller SHALL expose a PUT endpoint at `/users/{userId}/drives/{driveKey}/items/{itemId}/move` that accepts a Move_Request body containing the destination `parentId`.
2. WHEN a move request is received, THE MyDrive_Service SHALL relocate the file or folder on the filesystem from its current parent directory to the directory identified by the destination `parentId`.
3. WHEN a move request is received, THE MyDrive_Service SHALL update the Drive_Item's `parentId` to reflect the new parent folder and return the updated Drive_Item.
4. WHEN a move request specifies a destination `parentId` that does not exist, THE MyDrive_Service SHALL throw a `DriveItemNotFoundException`.
5. WHEN a move request specifies an item ID that does not exist, THE MyDrive_Service SHALL throw a `DriveItemNotFoundException`.
6. WHEN a move request would result in a name conflict with an existing sibling in the destination folder, THE MyDrive_Service SHALL throw a `DriveItemConflictException`.
7. WHEN a move request targets the drive root folder as the item to move, THE MyDrive_Service SHALL throw a `DriveRootDeletionException` to prevent moving the root.
8. WHEN a move request attempts to move a folder into one of its own descendants, THE MyDrive_Service SHALL throw a `DriveItemConflictException` to prevent circular nesting.
9. THE MyDrive_Service SHALL enforce the same permission checks for move operations as for other write operations using the existing `checkPermission` method.
10. WHILE the Drive_Key is `mediaCache`, THE MyDrive_Service SHALL reject move requests because mediaCache is read-only.

### Requirement 2: Server-Side Drive Contents Caching

**User Story:** As a drive user, I want the mediaCache drive contents page to load faster, so that I do not have to wait for the full filesystem traversal every time I open the mediaCache drive.

#### Acceptance Criteria

1. THE MyDrive_Service SHALL maintain a Drive_Cache that stores Flat_Map responses keyed by a composite of `userId` and `driveKey`.
2. WHEN a `getDriveContents` request is received for a cache-enabled drive and a valid cache entry exists, THE MyDrive_Service SHALL return the cached Flat_Map without performing a filesystem traversal.
3. THE Drive_Cache SHALL use a configurable Cache_TTL with a default value of 2 hours, after which cached entries are considered stale.
4. WHEN a cached entry has exceeded the Cache_TTL, THE MyDrive_Service SHALL perform a fresh filesystem traversal, update the cache, and return the new Flat_Map.
5. WHEN a write operation is performed on a cache-enabled drive (file upload, folder creation, item deletion, item rename, or item move), THE MyDrive_Service SHALL invalidate the cache entry for that drive so the next read returns fresh data.
6. WHEN the cache is invalidated for a specific drive, THE MyDrive_Service SHALL only invalidate the entry for that specific `userId` and `driveKey` combination, leaving other cache entries intact.
7. IF the cache encounters an error during retrieval, THEN THE MyDrive_Service SHALL fall back to a direct filesystem traversal and log the error.
8. THE Drive_Cache SHALL be configurable via application properties to enable or disable caching per Drive_Key, with `mediaCache` enabled by default and all other drive keys disabled by default.
