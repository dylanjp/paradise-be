# Recurring Scheduler Fix - Bugfix Design

## Overview

The recurring notification scheduler has two interacting defects that cause complete data loss when any single user's todo creation fails. First, `createTodosForNotification()` throws `TodoCreationException` despite a comment saying "log and continue." Second, both the scheduler and service share a single `@Transactional` boundary (Spring's default `REQUIRED` propagation), so the thrown exception marks the entire transaction rollback-only, undoing all successfully processed notifications in the run.

The fix removes the errant throw, removes `@Transactional` from the scheduler, and introduces per-notification transaction isolation via `REQUIRES_NEW` propagation on a dedicated processing method.

## Glossary

- **Bug_Condition (C)**: The condition that triggers the bug — a `TodoCreationException` is thrown inside `createTodosForNotification()` while operating within a shared transaction boundary between the scheduler and service.
- **Property (P)**: The desired behavior — individual user failures are logged and skipped, and each notification is processed in its own transaction so failures are isolated.
- **Preservation**: Existing behavior that must remain unchanged — successful notification processing (todo creation, read state reset, occurrence marking), skip logic for invalid/already-processed notifications, and manual trigger behavior.
- **RecurringActionTodoScheduler**: The `@Component` in `RecurringActionTodoScheduler.java` that runs the daily cron job and delegates to the service.
- **RecurringActionTodoService**: The `@Service` in `RecurringActionTodoService.java` containing `processRecurringNotifications()` and `createTodosForNotification()`.
- **TodoCreationException**: A custom exception in `TodoCreationException.java` thrown when a single user's todo creation fails.
- **ProcessingResult**: An immutable record tracking notification count, todo count, error count, and error messages.

## Bug Details

### Fault Condition

The bug manifests when any single user's todo creation fails (e.g., DB constraint violation, null pointer) during a scheduled run that processes multiple notifications. The `createTodosForNotification` method throws `TodoCreationException` despite the catch block comment stating "log and continue." This exception propagates into the notification loop in `processRecurringNotifications()`, where it is caught — but by then, Spring has already marked the shared transaction as rollback-only. On commit, all work from the entire run is rolled back.

**Formal Specification:**
```
FUNCTION isBugCondition(input)
  INPUT: input of type SchedulerRun (a set of due notifications with their target users)
  OUTPUT: boolean

  RETURN input.dueNotifications.size() >= 1
         AND EXISTS notification IN input.dueNotifications
             WHERE EXISTS userId IN getTargetUserIds(notification)
                 WHERE todoTaskRepository.saveAndFlush(todoFor(notification, userId)) THROWS Exception
         AND schedulerMethod.hasAnnotation(@Transactional)
         AND serviceMethod.hasAnnotation(@Transactional) WITH propagation == REQUIRED
END FUNCTION
```

### Examples

- **Example 1**: 3 notifications are due. Notification #2 has a user with a duplicate UUID constraint violation. On unfixed code: `createTodosForNotification` throws `TodoCreationException`, the shared transaction is marked rollback-only, and all 3 notifications' work (including notification #1's successfully created todos) is rolled back. Expected: notification #1 and #3 commit successfully; notification #2 logs the error and rolls back only its own work.
- **Example 2**: 1 notification is due with 5 target users. User #3 has been deleted from the database mid-run. On unfixed code: `TodoCreationException` is thrown, the entire notification's processing is rolled back including the 2 already-created todos. Expected: the error is logged, user #3 is skipped, and the other 4 todos are committed.
- **Example 3**: 5 notifications are due, all succeed. On unfixed code: works correctly (no exception path triggered). Expected: same behavior — all 5 processed and committed.
- **Edge case**: 1 notification, 1 target user, `saveAndFlush` throws. On unfixed code: the single notification's work is rolled back. Expected: the error is logged, 0 todos created for that notification, but the notification loop completes and the run finishes without a fatal error.

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- Successful todo creation for all target users of a notification must continue to create `TodoTask` records, reset read states, and mark the occurrence as processed exactly as before.
- Notifications with no valid action item must continue to be skipped with a return of 0.
- Target users not found in the database must continue to be logged and skipped without affecting other users.
- Recurrence rule filtering must continue to exclude non-due notifications.
- Already-processed occurrences must continue to be skipped to prevent duplicates.
- Empty due-notification lists must continue to result in a successful run with zero processed.
- Manual processing triggers must continue to work correctly.
- The `ProcessingResult` record must continue to accurately reflect counts and error messages.

**Scope:**
All inputs that do NOT involve a `saveAndFlush` failure during todo creation should be completely unaffected by this fix. This includes:
- Runs where all notifications and all users succeed
- Runs with no due notifications
- Notifications skipped due to missing action items
- Notifications skipped due to already-processed occurrences
- Users skipped due to not being found in the database (this is already handled gracefully)

## Hypothesized Root Cause

Based on the bug description and code analysis, the two interacting root causes are:

1. **Errant throw in catch block**: In `createTodosForNotification()` (line ~228), the catch block contains `throw new TodoCreationException(notification.getId(), userId, e)` immediately after the comment "Handle individual user failures gracefully - log and continue." The throw contradicts the stated intent and causes the exception to propagate out of the per-user loop.

2. **Shared transaction boundary**: `RecurringActionTodoScheduler.processRecurringNotifications()` is annotated `@Transactional`, and `RecurringActionTodoService.processRecurringNotifications()` is also annotated `@Transactional`. Since Spring's default propagation is `REQUIRED`, the service method joins the scheduler's transaction. When `TodoCreationException` propagates through the service method's `@Transactional` proxy (even though it's caught in the notification loop), Spring marks the transaction as rollback-only. The scheduler's `@Transactional` then attempts to commit but finds the rollback-only flag, causing a full rollback.

3. **No per-notification isolation**: There is no transaction boundary around individual notification processing. All notifications share one transaction, so a single failure poisons the entire batch.

## Correctness Properties

Property 1: Fault Condition - User-Level Failure Does Not Throw

_For any_ input where `todoTaskRepository.saveAndFlush()` throws an exception for a specific user during `createTodosForNotification()`, the fixed method SHALL catch the exception, log it, and continue iterating over remaining target users without throwing `TodoCreationException`, returning the count of successfully created todos.

**Validates: Requirements 2.1**

Property 2: Fault Condition - Per-Notification Transaction Isolation

_For any_ scheduler run where multiple notifications are due and one notification's processing fails, the fixed system SHALL commit all successfully processed notifications independently, rolling back only the failed notification's changes.

**Validates: Requirements 2.2, 2.3, 2.4**

Property 3: Preservation - Successful Processing Unchanged

_For any_ input where no exceptions occur during todo creation, the fixed code SHALL produce exactly the same result as the original code: the same `TodoTask` records created, the same read states reset, the same occurrences marked as processed, and the same `ProcessingResult` returned.

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7**

## Fix Implementation

### Changes Required

Assuming our root cause analysis is correct:

**File**: `be/src/main/java/com/dylanjohnpratt/paradise/be/config/RecurringActionTodoScheduler.java`

**Function**: `processRecurringNotifications()`

**Specific Changes**:
1. **Remove `@Transactional`**: Remove the `@Transactional` annotation from the scheduler's `processRecurringNotifications()` method. The scheduler should not own a transaction — it is a coordination layer that delegates transactional work to the service.

---

**File**: `be/src/main/java/com/dylanjohnpratt/paradise/be/service/RecurringActionTodoService.java`

**Function**: `createTodosForNotification()`

**Specific Changes**:
2. **Remove the throw statement**: In the catch block of the per-user loop inside `createTodosForNotification()`, remove `throw new TodoCreationException(notification.getId(), userId, e);`. The existing logging is sufficient. The method should continue iterating and return the `successCount`.

**Function**: `processRecurringNotifications()`

**Specific Changes**:
3. **Extract per-notification processing into a new method**: Create a new method (e.g., `processSingleNotification(Notification notification, LocalDate today)`) that contains the body of the notification loop (calling `createTodosForNotification`, `resetReadStatesForNotification`, `markOccurrenceProcessed`). Annotate this new method with `@Transactional(propagation = Propagation.REQUIRES_NEW)` so each notification gets its own independent transaction.

4. **Remove `@Transactional` from `processRecurringNotifications()`**: The outer method should not be transactional. It iterates over notifications and delegates to the per-notification method. Note: for Spring's proxy-based AOP to work, the new method must be called through the proxy (e.g., via self-injection or extracting it to a separate `@Service` class). The simplest approach is self-injection.

5. **Self-injection for proxy invocation**: Inject the service into itself (e.g., `@Lazy private RecurringActionTodoService self;`) and call `self.processSingleNotification(...)` from the loop to ensure the `@Transactional(propagation = REQUIRES_NEW)` annotation is honored by Spring's proxy.

## Testing Strategy

### Validation Approach

The testing strategy follows a two-phase approach: first, surface counterexamples that demonstrate the bug on unfixed code, then verify the fix works correctly and preserves existing behavior.

### Exploratory Fault Condition Checking

**Goal**: Surface counterexamples that demonstrate the bug BEFORE implementing the fix. Confirm or refute the root cause analysis. If we refute, we will need to re-hypothesize.

**Test Plan**: Write tests that mock `todoTaskRepository.saveAndFlush()` to throw an exception for a specific user, then verify the behavior of both `createTodosForNotification()` and the full `processRecurringNotifications()` flow. Run these tests on the UNFIXED code to observe failures.

**Test Cases**:
1. **Single User Failure in createTodosForNotification**: Mock `saveAndFlush` to throw for user #2 of 3. Assert that `TodoCreationException` is thrown (will confirm Issue 1 on unfixed code).
2. **Multi-Notification Rollback**: Set up 3 due notifications, mock a failure in notification #2. Assert that notifications #1 and #3's todos are NOT persisted (will confirm Issue 2 on unfixed code — shared transaction rollback).
3. **Exception Propagation Through Service**: Verify that `TodoCreationException` thrown in `createTodosForNotification` propagates through the `@Transactional` service method and marks the transaction rollback-only.
4. **Single Notification Single User Failure**: 1 notification, 1 user, `saveAndFlush` throws. Verify the entire run produces 0 results and the error is recorded.

**Expected Counterexamples**:
- `createTodosForNotification` throws `TodoCreationException` instead of logging and continuing
- All notifications' todos are rolled back when any single user's creation fails
- Possible causes: errant throw in catch block, shared `@Transactional` boundary

### Fix Checking

**Goal**: Verify that for all inputs where the bug condition holds, the fixed function produces the expected behavior.

**Pseudocode:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := processRecurringNotifications_fixed(input)
  ASSERT createTodosForNotification does NOT throw TodoCreationException
  ASSERT failed user is logged and skipped
  ASSERT other users' todos are created
  ASSERT other notifications are committed independently
  ASSERT result.errors() counts only the failed notifications
  ASSERT result.notificationsProcessed() counts all attempted notifications
END FOR
```

### Preservation Checking

**Goal**: Verify that for all inputs where the bug condition does NOT hold, the fixed function produces the same result as the original function.

**Pseudocode:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT processRecurringNotifications_original(input) = processRecurringNotifications_fixed(input)
END FOR
```

**Testing Approach**: Property-based testing is recommended for preservation checking because:
- It generates many test cases automatically across the input domain (varying numbers of notifications, users, action items)
- It catches edge cases that manual unit tests might miss (empty user sets, null action items, already-processed occurrences)
- It provides strong guarantees that behavior is unchanged for all non-buggy inputs

**Test Plan**: Observe behavior on UNFIXED code first for all-success scenarios, then write property-based tests capturing that behavior and verify it holds after the fix.

**Test Cases**:
1. **Successful Processing Preservation**: Verify that when all `saveAndFlush` calls succeed, the same `ProcessingResult` is returned (same notification count, todo count, zero errors).
2. **Skip Logic Preservation**: Verify that notifications with no valid action item, already-processed occurrences, and non-due notifications continue to be filtered/skipped identically.
3. **User Not Found Preservation**: Verify that users not found in the database are still logged and skipped without affecting the todo count.
4. **Empty Run Preservation**: Verify that a run with no due notifications returns `ProcessingResult.empty()`.

### Unit Tests

- Test `createTodosForNotification` with a mocked `saveAndFlush` failure: verify no exception is thrown, error is logged, and `successCount` reflects only successful users.
- Test `createTodosForNotification` with all users succeeding: verify same behavior as before.
- Test `createTodosForNotification` with no valid action item: verify returns 0.
- Test `processSingleNotification` transaction isolation: verify `@Transactional(propagation = REQUIRES_NEW)` is present.
- Test that `RecurringActionTodoScheduler.processRecurringNotifications()` does NOT have `@Transactional`.

### Property-Based Tests

- Generate random sets of notifications (1-20) with random numbers of target users (0-10), randomly inject `saveAndFlush` failures, and verify: (a) non-failing notifications are committed, (b) failing notifications are rolled back independently, (c) `ProcessingResult` accurately reflects counts.
- Generate random all-success scenarios with varying notification/user counts and verify the `ProcessingResult` matches the expected totals (preservation).
- Generate random notification configurations with edge cases (null action items, empty user sets, already-processed flags) and verify skip/filter behavior is unchanged.

### Integration Tests

- Test full scheduler run with an in-memory database: insert multiple notifications, cause one to fail via a constraint violation, and verify the others are persisted.
- Test that the cron-triggered `processRecurringNotifications` completes without a fatal error when individual notifications fail.
- Test manual trigger path continues to work identically after the fix.
