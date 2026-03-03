# Implementation Plan

- [x] 1. Write bug condition exploration test
  - **Property 1: Fault Condition** - TodoCreationException Propagation and Shared Transaction Rollback
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the bug exists
  - **Scoped PBT Approach**: Scope the property to cases where `saveAndFlush` throws for a specific user during `createTodosForNotification()`
  - Create test file: `be/src/test/java/com/dylanjohnpratt/paradise/be/service/RecurringActionTodoBugExplorationTest.java`
  - Use the existing in-memory repository pattern from `RecurringActionTodoServicePropertyTest.java` (reuse `FailingTodoTaskRepository` and other in-memory repos)
  - Test 1: Mock `saveAndFlush` to throw for user #2 of 3 target users. Assert that `createTodosForNotification` does NOT throw `TodoCreationException`, logs the error, and returns the count of successfully created todos (expected: 2). On UNFIXED code this will FAIL because the method throws `TodoCreationException`.
  - Test 2: Set up 3 due notifications with `FailingTodoTaskRepository` configured to fail on notification #2. Assert that `processRecurringNotifications` returns a `ProcessingResult` where notifications #1 and #3 are successfully processed. On UNFIXED code this will FAIL because the shared transaction rolls back all work.
  - Test 3: Single notification, single user, `saveAndFlush` throws. Assert the run completes without fatal error and `ProcessingResult` records the error. On UNFIXED code this will FAIL because `TodoCreationException` propagates.
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct - it proves the bug exists)
  - Document counterexamples found to understand root cause
  - Mark task complete when tests are written, run, and failure is documented
  - _Requirements: 1.1, 1.2, 1.3, 1.4_

- [x] 2. Write preservation property tests (BEFORE implementing fix)
  - **Property 2: Preservation** - Successful Processing Behavior Unchanged
  - **IMPORTANT**: Follow observation-first methodology
  - Create test file: `be/src/test/java/com/dylanjohnpratt/paradise/be/service/RecurringActionTodoPreservationTest.java`
  - Use the existing in-memory repository pattern from `RecurringActionTodoServicePropertyTest.java`
  - Observe behavior on UNFIXED code for non-buggy inputs (all `saveAndFlush` calls succeed):
  - Observe: When all users succeed, `ProcessingResult` has correct notification count, todo count, and zero errors
  - Observe: When notification has no valid action item (null/blank description), `createTodosForNotification` returns 0
  - Observe: When target user not found in DB, user is skipped and other users' todos are still created
  - Observe: When no due notifications exist, `ProcessingResult.empty()` is returned
  - Observe: When occurrence already processed, notification is skipped (no duplicates)
  - Write property-based tests (jqwik `@Property`) capturing observed behavior:
    - Property: For all valid notification configurations where no `saveAndFlush` failure occurs, `processRecurringNotifications` returns `ProcessingResult` with `todosCreated` equal to total target users across all due notifications, `notificationsProcessed` equal to number of due notifications, and `errors` equal to 0
    - Property: For all notifications with null/blank action items, `createTodosForNotification` returns 0
    - Property: For all configurations with missing users, the missing users are skipped and `successCount` reflects only found users
  - Verify tests PASS on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [x] 3. Fix for shared transaction rollback on single user failure

  - [x] 3.1 Remove errant throw from createTodosForNotification catch block
    - In `RecurringActionTodoService.java`, in the `createTodosForNotification()` method, remove `throw new TodoCreationException(notification.getId(), userId, e);` from the catch block
    - The existing `logger.error(...)` call is sufficient - the method should continue iterating over remaining users and return `successCount`
    - _Bug_Condition: isBugCondition(input) where saveAndFlush throws AND catch block re-throws TodoCreationException_
    - _Expected_Behavior: Catch block logs error and continues loop iteration without throwing_
    - _Preservation: All non-exception paths remain unchanged_
    - _Requirements: 2.1_

  - [x] 3.2 Remove @Transactional from RecurringActionTodoScheduler.processRecurringNotifications()
    - In `RecurringActionTodoScheduler.java`, remove the `@Transactional` annotation from `processRecurringNotifications()`
    - The scheduler is a coordination layer and should not own a transaction boundary
    - Remove the `import org.springframework.transaction.annotation.Transactional;` if no longer needed
    - _Bug_Condition: Scheduler @Transactional creates shared transaction boundary with service_
    - _Expected_Behavior: Scheduler delegates transactional work to service per-notification methods_
    - _Requirements: 2.2_

  - [x] 3.3 Remove @Transactional from RecurringActionTodoService.processRecurringNotifications() and extract per-notification processing
    - Remove `@Transactional` from `processRecurringNotifications()` in `RecurringActionTodoService.java`
    - Extract the notification loop body (calling `createTodosForNotification`, `resetReadStatesForNotification`, `markOccurrenceProcessed`) into a new method `processSingleNotification(Notification notification, LocalDate today)` that returns the number of todos created
    - Annotate `processSingleNotification` with `@Transactional(propagation = Propagation.REQUIRES_NEW)` so each notification gets its own independent transaction
    - Add self-injection: `@Lazy private RecurringActionTodoService self;` field with constructor or setter injection
    - Call `self.processSingleNotification(notification, today)` from the notification loop in `processRecurringNotifications()` to ensure Spring's proxy-based AOP honors the `REQUIRES_NEW` propagation
    - _Bug_Condition: Shared @Transactional(REQUIRED) boundary causes full rollback on any exception_
    - _Expected_Behavior: Each notification processed in isolated transaction; failure rolls back only that notification_
    - _Preservation: Successful notification processing (todo creation, read state reset, occurrence marking) unchanged_
    - _Requirements: 2.2, 2.3, 2.4_

  - [x] 3.4 Verify bug condition exploration test now passes
    - **Property 1: Expected Behavior** - TodoCreationException No Longer Propagates
    - **IMPORTANT**: Re-run the SAME tests from task 1 - do NOT write new tests
    - The tests from task 1 encode the expected behavior (no exception thrown, isolated failures, correct ProcessingResult)
    - When these tests pass, it confirms: `createTodosForNotification` catches exceptions and continues, and `processRecurringNotifications` isolates notification failures
    - Run bug condition exploration tests from step 1
    - **EXPECTED OUTCOME**: Tests PASS (confirms bug is fixed)
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 3.5 Verify preservation tests still pass
    - **Property 2: Preservation** - Successful Processing Behavior Still Unchanged
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - Run preservation property tests from step 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all preservation tests still pass after fix (no regressions in successful processing, skip logic, user-not-found handling, empty runs)

- [x] 4. Checkpoint - Ensure all tests pass
  - Run the full test suite to verify no regressions
  - Ensure exploration tests (task 1) now pass
  - Ensure preservation tests (task 2) still pass
  - Ensure existing tests in `RecurringActionTodoServicePropertyTest.java` and `RecurringActionTodoIntegrationTest.java` still pass
  - Ask the user if questions arise
