# Implementation Plan: MyDrive Enhancements

## Overview

Implement two enhancements to the MyDrive feature: (1) a move endpoint for relocating files and folders within a drive, and (2) server-side in-memory caching for drive contents. Tasks are ordered so that the cache infrastructure is built first (since the move operation depends on cache invalidation), followed by the move feature, then wiring and integration.

## Tasks

- [x] 1. Create DriveCacheProperties configuration and CacheEntry record
  - [x] 1.1 Create `DriveCacheProperties` record in `be/src/main/java/com/dylanjohnpratt/paradise/be/config/DriveCacheProperties.java`
    - Annotate with `@ConfigurationProperties(prefix = "drive.cache")`
    - Fields: `Duration ttl` (default PT2H), `boolean myDrive` (false), `boolean sharedDrive` (false), `boolean adminDrive` (false), `boolean mediaCache` (true)
    - Enable configuration properties scanning in `BeApplication` or a config class with `@EnableConfigurationProperties(DriveCacheProperties.class)`
    - _Requirements: 2.3, 2.8_

  - [x] 1.2 Add default cache properties to `application.properties`
    - Add `drive.cache.ttl=PT2H`, `drive.cache.media-cache=true`, and disabled entries for other drive keys
    - _Requirements: 2.3, 2.8_

  - [x] 1.3 Create `CacheEntry` record in `be/src/main/java/com/dylanjohnpratt/paradise/be/service/CacheEntry.java`
    - Fields: `Map<String, DriveItem> contents`, `Instant createdAt`
    - Method: `boolean isStale(Duration ttl)` — returns true if `Instant.now()` is after `createdAt.plus(ttl)`
    - _Requirements: 2.3, 2.4_

- [x] 2. Implement DriveCacheManager component
  - [x] 2.1 Create `DriveCacheManager` as a Spring `@Component` in `be/src/main/java/com/dylanjohnpratt/paradise/be/service/DriveCacheManager.java`
    - Inject `DriveCacheProperties`
    - Internal storage: `ConcurrentHashMap<String, CacheEntry>`
    - `String cacheKey(String userId, String driveKey)` — returns `"userId:driveKey"`
    - `boolean isEnabled(String driveKey)` — checks the per-key toggle from properties
    - `Optional<Map<String, DriveItem>> get(String userId, String driveKey)` — returns cached value if present and not stale, else empty; catches exceptions and returns empty
    - `void put(String userId, String driveKey, Map<String, DriveItem> contents)` — stores a new `CacheEntry` with `Instant.now()`
    - `void invalidate(String userId, String driveKey)` — removes the entry for the given key
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.6, 2.7_

  - [ ]* 2.2 Write property test: Cache TTL correctness (Property 6)
    - **Property 6: Cache TTL correctness**
    - Generate random TTL durations and entry ages; verify `CacheEntry.isStale()` returns correct boolean and `DriveCacheManager.get()` returns present/empty accordingly
    - Test class: `be/src/test/java/com/dylanjohnpratt/paradise/be/service/DriveCacheManagerPropertyTest.java`
    - **Validates: Requirements 2.2, 2.3, 2.4**

  - [ ]* 2.3 Write property test: Scoped cache invalidation (Property 7)
    - **Property 7: Scoped cache invalidation**
    - Generate a random set of cache entries across different `userId:driveKey` combinations, invalidate one, verify all others remain intact
    - Test class: `be/src/test/java/com/dylanjohnpratt/paradise/be/service/DriveCacheManagerPropertyTest.java`
    - **Validates: Requirements 2.1, 2.6**

  - [ ]* 2.4 Write unit tests for DriveCacheManager
    - Test class: `be/src/test/java/com/dylanjohnpratt/paradise/be/service/DriveCacheManagerTest.java`
    - Test `isEnabled` returns true for mediaCache, false for others by default
    - Test cache error fallback (get returns empty on internal error)
    - Test `isStale` boundary values (TTL - 1ms, TTL, TTL + 1ms)
    - _Requirements: 2.7, 2.8_

- [x] 3. Integrate caching into MyDriveService.getDriveContents and write operations
  - [x] 3.1 Inject `DriveCacheManager` into `MyDriveService`
    - Add constructor parameter and field
    - _Requirements: 2.1_

  - [x] 3.2 Add cache-first lookup to `getDriveContents`
    - Before filesystem traversal: if `driveCacheManager.isEnabled(driveKey)`, call `get()`; if present, return cached value
    - After filesystem traversal: if enabled, call `put()` to store result
    - Wrap cache operations in try-catch; on error, log and fall back to traversal
    - _Requirements: 2.2, 2.4, 2.7_

  - [x] 3.3 Add cache invalidation to all existing write operations
    - At the end of `createFolder`, `uploadFile`, `updateItem`, and `deleteItem`, call `driveCacheManager.invalidate(userId, driveKey)`
    - _Requirements: 2.5_

  - [ ]* 3.4 Write property test: Write operations invalidate cache (Property 8)
    - **Property 8: Write operations invalidate cache**
    - Populate cache, perform a random write operation, verify the cache entry is gone and next `getDriveContents` performs fresh traversal
    - Test class: `be/src/test/java/com/dylanjohnpratt/paradise/be/service/DriveCacheManagerPropertyTest.java`
    - **Validates: Requirements 2.5**

- [x] 4. Checkpoint
  - Ensure all tests pass, ask the user if questions arise.

- [x] 5. Create MoveRequest DTO and implement moveItem service method
  - [x] 5.1 Create `MoveRequest` record in `be/src/main/java/com/dylanjohnpratt/paradise/be/dto/MoveRequest.java`
    - Field: `@NotBlank String parentId`
    - _Requirements: 1.1_

  - [x] 5.2 Implement `moveItem` method in `MyDriveService`
    - Parse drive key, call `checkPermission(key, userId, currentUser, true)` — this also rejects mediaCache writes
    - Reject if `itemId` equals `"root"` → throw `DriveRootDeletionException`
    - Resolve source item path and destination parent path via `resolveItemPath`
    - Validate source exists (throw `DriveItemNotFoundException` if not)
    - Validate destination parent exists (throw `DriveItemNotFoundException` if not)
    - Check circular nesting: walk from destination up to root, if source is an ancestor → throw `DriveItemConflictException`
    - Check name conflict: if destination folder already contains a child with the same filename → throw `DriveItemConflictException`
    - Perform `Files.move(sourcePath, destParentPath.resolve(sourcePath.getFileName()))`
    - Invalidate cache via `driveCacheManager.invalidate(userId, driveKey)`
    - Return updated `DriveItem` with new `parentId`
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 2.5_

  - [ ]* 5.3 Write property test: Move correctness (Property 1)
    - **Property 1: Move correctness — filesystem relocation and parentId update**
    - Generate a random drive tree on a temp filesystem, pick a random item and valid destination, call `moveItem`, verify filesystem state and returned `parentId`
    - Test class: `be/src/test/java/com/dylanjohnpratt/paradise/be/service/MyDriveServiceMovePropertyTest.java`
    - **Validates: Requirements 1.2, 1.3**

  - [ ]* 5.4 Write property test: Non-existent ID rejection (Property 2)
    - **Property 2: Non-existent ID rejection**
    - Generate random non-existent IDs (UUIDs not in the tree), call `moveItem`, assert `DriveItemNotFoundException`
    - Test class: `be/src/test/java/com/dylanjohnpratt/paradise/be/service/MyDriveServiceMovePropertyTest.java`
    - **Validates: Requirements 1.4, 1.5**

  - [ ]* 5.5 Write property test: Name conflict rejection (Property 3)
    - **Property 3: Name conflict rejection**
    - Generate a tree where the destination already has a child with the same name, call `moveItem`, assert `DriveItemConflictException` and filesystem unchanged
    - Test class: `be/src/test/java/com/dylanjohnpratt/paradise/be/service/MyDriveServiceMovePropertyTest.java`
    - **Validates: Requirements 1.6**

  - [ ]* 5.6 Write property test: Circular nesting prevention (Property 4)
    - **Property 4: Circular nesting prevention**
    - Generate a random tree, pick a folder and one of its descendants as destination, call `moveItem`, assert `DriveItemConflictException`
    - Test class: `be/src/test/java/com/dylanjohnpratt/paradise/be/service/MyDriveServiceMovePropertyTest.java`
    - **Validates: Requirements 1.8**

  - [ ]* 5.7 Write property test: Permission enforcement on move (Property 5)
    - **Property 5: Permission enforcement on move**
    - Generate random user/driveKey combos, verify `moveItem` throws the same exception as `checkPermission` would for write access
    - Test class: `be/src/test/java/com/dylanjohnpratt/paradise/be/service/MyDriveServiceMovePropertyTest.java`
    - **Validates: Requirements 1.9, 1.10**

  - [ ]* 5.8 Write unit tests for moveItem
    - Test class: `be/src/test/java/com/dylanjohnpratt/paradise/be/service/MyDriveServiceMoveTest.java`
    - Test move root item → `DriveRootDeletionException`
    - Test move on mediaCache → `DriveAccessDeniedException`
    - Test move item to its current parent (no-op) → succeeds without error
    - _Requirements: 1.7, 1.10_

- [x] 6. Add move endpoint to MyDriveController
  - [x] 6.1 Add `PUT /items/{itemId}/move` endpoint in `MyDriveController`
    - Accept `@PathVariable userId, driveKey, itemId`, `@Valid @RequestBody MoveRequest`, `@AuthenticationPrincipal User`
    - Call `validateDriveKey`, delegate to `myDriveService.moveItem`, return `ResponseEntity.ok(item)`
    - _Requirements: 1.1_

  - [ ]* 6.2 Write controller integration test for move endpoint
    - Test class: `be/src/test/java/com/dylanjohnpratt/paradise/be/controller/MyDriveControllerMoveTest.java`
    - Verify PUT routing, 200 response on success, 404/409/400/403 on error scenarios
    - _Requirements: 1.1, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10_

- [x] 7. Final checkpoint
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests use jqwik 1.9.2 (already in project dependencies)
- Unit tests validate specific examples and edge cases
