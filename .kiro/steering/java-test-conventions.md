---
inclusion: fileMatch
fileMatchPattern: "be/src/test/**/*.java"
---

# Java Test Conventions

## Null Safety

When writing test helper methods that return model objects (e.g., `User`, `Notification`, `TodoTask`) which will be passed to repository `save()` or `saveAndFlush()` methods annotated with `@NonNull`, always annotate the helper method return type with `@NonNull` to avoid null-safety warnings.

```java
// Good
@NonNull
private User createUser(long id, String username) { ... }

@NonNull
private Notification createNotification(long id, ...) { ... }

// Bad — causes "Null type safety: needs unchecked conversion to conform to @NonNull" warnings
private User createUser(long id, String username) { ... }
```

The `@NonNull` annotation to use is `org.springframework.lang.NonNull`.

## Spring @Transactional and REQUIRES_NEW in Integration Tests

When the production code uses `@Transactional(propagation = Propagation.REQUIRES_NEW)`, integration tests annotated with `@Transactional` will fail because the new transaction cannot see the test's uncommitted data.

For services that use `REQUIRES_NEW`, integration tests should:
- NOT use `@Transactional` at the class level
- Use `@BeforeEach` / `@AfterEach` with `deleteAll()` for cleanup instead of relying on transaction rollback

## Service Constructor Changes Must Update Tests

When adding a new dependency to a service constructor (e.g., adding `DriveCacheManager` to `MyDriveService`), you MUST also update all test files that directly instantiate that service with `new ServiceName(...)`.

Search for all usages of the old constructor in test files and update them to include the new parameter. Tests in this project construct services directly rather than using Spring injection, so constructor signature changes will break them at compile time.

To find affected tests, search for `new ServiceName(` across `be/src/test/`.

When the new dependency is a simple component (like a cache manager), provide a real instance with a no-op/disabled configuration rather than a mock:

```java
// Good — real instance with caching disabled, avoids mock complexity
new MyDriveService(metadataRepo, props,
    new DriveCacheManager(new DriveCacheProperties(null, false, false, false, false)));

// Also acceptable — mock if the dependency has complex behavior
new MyDriveService(metadataRepo, props, mock(DriveCacheManager.class));
```
