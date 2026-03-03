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
