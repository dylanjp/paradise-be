# Bugfix Requirements Document

## Introduction

The recurring notification scheduler fails to process all due notifications when run via the scheduled job. If any single user's todo creation fails (e.g., DB constraint violation), the exception propagates and Spring marks the shared transaction as rollback-only. This causes all successfully processed notifications in the same run to be rolled back, making it appear as though only the first notification (or none) was processed. Manual runs may succeed because they operate under different data conditions or transaction contexts that don't trigger the failure path.

The root cause is twofold: (1) the `createTodosForNotification` method throws `TodoCreationException` despite a comment indicating it should log and continue, and (2) nested `@Transactional` annotations on both the scheduler and service share a single transaction, so any exception marks the entire transaction for rollback.

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN a user's todo creation fails inside `createTodosForNotification()` THEN the system throws a `TodoCreationException` despite the catch block comment stating "log and continue", causing the exception to propagate up the call stack.

1.2 WHEN `TodoCreationException` propagates within the shared `@Transactional` context between `RecurringActionTodoScheduler.processRecurringNotifications()` and `RecurringActionTodoService.processRecurringNotifications()` THEN the system marks the entire transaction as rollback-only, even though the exception is caught in the notification processing loop.

1.3 WHEN the transaction completes after being marked rollback-only THEN the system rolls back all successfully processed notifications and their created todo tasks from the entire scheduler run, not just the notification that failed.

1.4 WHEN multiple recurring notifications are due and any one of them encounters a user-level todo creation failure THEN the system effectively processes zero notifications because the transaction rollback undoes all prior work in that run.

### Expected Behavior (Correct)

2.1 WHEN a user's todo creation fails inside `createTodosForNotification()` THEN the system SHALL log the error and continue processing the remaining target users for that notification without throwing an exception.

2.2 WHEN processing multiple due notifications THEN the system SHALL isolate each notification's processing in its own transaction boundary so that a failure in one notification does not affect the others.

2.3 WHEN one notification's processing fails entirely THEN the system SHALL continue processing the remaining due notifications, and only the failed notification's changes SHALL be rolled back.

2.4 WHEN multiple recurring notifications are due and one encounters a failure THEN the system SHALL successfully commit all other notifications that were processed without error.

### Unchanged Behavior (Regression Prevention)

3.1 WHEN all target users' todo creation succeeds for a notification THEN the system SHALL CONTINUE TO create todo tasks, reset read states, and mark the occurrence as processed exactly as before.

3.2 WHEN a notification has no valid action item THEN the system SHALL CONTINUE TO skip todo creation and return 0 for that notification.

3.3 WHEN a target user is not found in the database THEN the system SHALL CONTINUE TO log a warning and skip that user without affecting other users.

3.4 WHEN the recurrence rule evaluation determines a notification is not due THEN the system SHALL CONTINUE TO filter it out and not process it.

3.5 WHEN an occurrence has already been processed for today's date THEN the system SHALL CONTINUE TO skip that notification to prevent duplicate processing.

3.6 WHEN the scheduler runs and there are no due notifications THEN the system SHALL CONTINUE TO complete successfully with zero notifications processed.

3.7 WHEN manual processing is triggered THEN the system SHALL CONTINUE TO process all due notifications correctly.
