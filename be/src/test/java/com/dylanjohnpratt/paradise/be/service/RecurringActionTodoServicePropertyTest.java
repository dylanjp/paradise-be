package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.model.*;
import com.dylanjohnpratt.paradise.be.repository.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for RecurringActionTodoService.
 * Uses jqwik to verify correctness properties across many generated inputs.
 * Uses in-memory repositories for testing.
 */
class RecurringActionTodoServicePropertyTest {

    /**
     * Creates a RecurringActionTodoService with in-memory repositories for testing.
     */
    private TestContext createTestContext() {
        InMemoryNotificationRepository notificationRepo = new InMemoryNotificationRepository();
        InMemoryOccurrenceTrackerRepository occurrenceRepo = new InMemoryOccurrenceTrackerRepository();
        InMemoryTodoTaskRepository todoRepo = new InMemoryTodoTaskRepository();
        InMemoryUserRepository userRepo = new InMemoryUserRepository();
        RecurrenceService recurrenceService = new RecurrenceService(new Random(42));
        
        RecurringActionTodoService service = new RecurringActionTodoService(
            notificationRepo, occurrenceRepo, todoRepo, recurrenceService, userRepo
        );
        
        return new TestContext(service, notificationRepo, occurrenceRepo, todoRepo, userRepo);
    }

    /**
     * Test context holding service and repositories for verification.
     */
    private record TestContext(
        RecurringActionTodoService service,
        InMemoryNotificationRepository notificationRepo,
        InMemoryOccurrenceTrackerRepository occurrenceRepo,
        InMemoryTodoTaskRepository todoRepo,
        InMemoryUserRepository userRepo
    ) {}

    /**
     * Feature: recurring-action-todo, Property 7: Target User Task Creation
     * For any recurring notification being processed:
     * - If the notification is global, the number of TODO tasks created SHALL equal the number of active users in the system
     * - If the notification is user-specific, TODO tasks SHALL be created exactly for the users in targetUserIds and no others
     * 
     * Validates: Requirements 3.1, 3.2, 3.3
     */
    @Property(tries = 100)
    @Label("Feature: recurring-action-todo, Property 7: Target User Task Creation")
    void targetUserTaskCreation(
            @ForAll @IntRange(min = 1, max = 10) int numActiveUsers,
            @ForAll @IntRange(min = 0, max = 5) int numInactiveUsers,
            @ForAll boolean isGlobal,
            @ForAll @NotBlank @Size(max = 200) String actionDescription,
            @ForAll @NotBlank @Size(max = 50) String actionCategory
    ) {
        TestContext ctx = createTestContext();
        
        // Create active users
        Set<Long> activeUserIds = new HashSet<>();
        for (int i = 1; i <= numActiveUsers; i++) {
            User user = new User("user" + i, "password", Set.of("ROLE_USER"));
            user.setId((long) i);
            user.setEnabled(true);
            ctx.userRepo.save(user);
            activeUserIds.add((long) i);
        }
        
        // Create inactive users
        for (int i = numActiveUsers + 1; i <= numActiveUsers + numInactiveUsers; i++) {
            User user = new User("user" + i, "password", Set.of("ROLE_USER"));
            user.setId((long) i);
            user.setEnabled(false);
            ctx.userRepo.save(user);
        }
        
        // Determine target users for non-global notification
        Set<Long> targetUserIds;
        if (isGlobal) {
            targetUserIds = new HashSet<>();
        } else {
            // Select a subset of active users as targets
            int numTargets = Math.max(1, numActiveUsers / 2);
            targetUserIds = activeUserIds.stream()
                .limit(numTargets)
                .collect(Collectors.toSet());
        }
        
        // Create notification with action item
        Notification notification = new Notification(
            "Test Subject",
            "Test Body",
            isGlobal,
            null, // no expiration
            targetUserIds,
            new RecurrenceRule(RecurrenceRule.RecurrenceType.DAILY, null, null).toJson(),
            new ActionItem(actionDescription, actionCategory)
        );
        notification.setId(1L);
        ctx.notificationRepo.save(notification);
        
        LocalDate today = LocalDate.now();
        
        // Create TODOs for the notification
        int todosCreated = ctx.service.createTodosForNotification(notification, today);
        
        // Verify the correct number of TODOs were created
        Set<Long> expectedTargetUsers;
        if (isGlobal) {
            expectedTargetUsers = activeUserIds;
        } else {
            expectedTargetUsers = targetUserIds;
        }
        
        assertThat(todosCreated)
            .as("Number of TODOs created should equal number of target users")
            .isEqualTo(expectedTargetUsers.size());
        
        // Verify TODOs were created for exactly the expected users
        // Note: Tasks are stored with usernames (e.g., "user1"), not numeric IDs
        List<TodoTask> allTasks = ctx.todoRepo.findAll();
        Set<String> actualUsernames = allTasks.stream()
            .map(TodoTask::getUserId)
            .collect(Collectors.toSet());
        
        // Convert expected user IDs to usernames for comparison
        Set<String> expectedUsernames = expectedTargetUsers.stream()
            .map(id -> "user" + id)
            .collect(Collectors.toSet());
        
        assertThat(actualUsernames)
            .as("TODOs should be created for exactly the expected users")
            .containsExactlyInAnyOrderElementsOf(expectedUsernames);
        
        // Verify no TODOs were created for inactive users
        for (int i = numActiveUsers + 1; i <= numActiveUsers + numInactiveUsers; i++) {
            final String inactiveUsername = "user" + i;
            assertThat(allTasks)
                .as("No TODO should be created for inactive user %s", inactiveUsername)
                .noneMatch(task -> task.getUserId().equals(inactiveUsername));
        }
    }

    // ==================== In-Memory Repository Implementations ====================

    /**
     * In-memory implementation of NotificationRepository for testing.
     */
    private static class InMemoryNotificationRepository implements NotificationRepository {
        private final Map<Long, Notification> notifications = new HashMap<>();
        private long idCounter = 0;

        @Override
        @NonNull
        public <S extends Notification> S save(@NonNull S notification) {
            if (notification.getId() == null) {
                notification.setId(++idCounter);
            }
            notifications.put(notification.getId(), notification);
            return notification;
        }

        @Override
        @NonNull
        public Optional<Notification> findById(@NonNull Long id) {
            return requireNonNull(Optional.ofNullable(notifications.get(id)));
        }

        @Override
        public List<Notification> findByIsGlobalTrueAndDeletedFalse() {
            return notifications.values().stream()
                .filter(n -> n.isGlobal() && !n.isDeleted())
                .toList();
        }

        @Override
        public List<Notification> findByTargetUserIdAndNotDeleted(Long userId) {
            return notifications.values().stream()
                .filter(n -> !n.isDeleted() && n.getTargetUserIds().contains(userId))
                .toList();
        }

        @Override
        public List<Notification> findAllForUser(Long userId) {
            return notifications.values().stream()
                .filter(n -> !n.isDeleted() && (n.isGlobal() || n.getTargetUserIds().contains(userId)))
                .toList();
        }

        @Override
        public List<Notification> findNonExpiredForUser(Long userId, LocalDateTime now) {
            return notifications.values().stream()
                .filter(n -> !n.isDeleted() && (n.isGlobal() || n.getTargetUserIds().contains(userId)))
                .filter(n -> n.getExpiresAt() == null || n.getExpiresAt().isAfter(now))
                .toList();
        }

        @Override
        public List<Long> findExpiredPastRetention(LocalDateTime retentionCutoff) {
            return notifications.values().stream()
                .filter(n -> n.getExpiresAt() != null && n.getExpiresAt().isBefore(retentionCutoff))
                .map(Notification::getId)
                .toList();
        }

        @Override
        public int countExpiredPastRetention(LocalDateTime retentionCutoff) {
            return (int) notifications.values().stream()
                .filter(n -> n.getExpiresAt() != null && n.getExpiresAt().isBefore(retentionCutoff))
                .count();
        }

        @Override
        public List<Notification> findActiveRecurringNotificationsWithActionItems(LocalDateTime now) {
            return notifications.values().stream()
                .filter(n -> !n.isDeleted())
                .filter(n -> n.getRecurrenceRuleJson() != null)
                .filter(n -> n.getActionItem() != null && 
                            n.getActionItem().getDescription() != null && 
                            !n.getActionItem().getDescription().isBlank())
                .filter(n -> n.getExpiresAt() == null || n.getExpiresAt().isAfter(now))
                .toList();
        }

        // Unused methods for testing
        @Override @NonNull public List<Notification> findAll() { return new ArrayList<>(notifications.values()); }
        @Override @NonNull public List<Notification> findAllById(@NonNull Iterable<Long> ids) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends Notification> List<S> saveAll(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public boolean existsById(@NonNull Long id) { return notifications.containsKey(id); }
        @Override public long count() { return notifications.size(); }
        @Override public void deleteById(@NonNull Long id) { notifications.remove(id); }
        @Override public void delete(@NonNull Notification entity) { notifications.remove(entity.getId()); }
        @Override public void deleteAllById(@NonNull Iterable<? extends Long> ids) { }
        @Override public void deleteAll(@NonNull Iterable<? extends Notification> entities) { }
        @Override public void deleteAll() { notifications.clear(); }
        @Override public void flush() { }
        @Override @NonNull public <S extends Notification> S saveAndFlush(@NonNull S entity) { return save(entity); }
        @Override @NonNull public <S extends Notification> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public void deleteAllInBatch(@NonNull Iterable<Notification> entities) { }
        @Override public void deleteAllByIdInBatch(@NonNull Iterable<Long> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override @NonNull public Notification getOne(@NonNull Long id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public Notification getById(@NonNull Long id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public Notification getReferenceById(@NonNull Long id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public <S extends Notification> Optional<S> findOne(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(Optional.empty()); }
        @Override @NonNull public <S extends Notification> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends Notification> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends Notification> org.springframework.data.domain.Page<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends Notification> long count(@NonNull org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends Notification> boolean exists(@NonNull org.springframework.data.domain.Example<S> example) { return false; }
        @Override @NonNull public <S extends Notification, R> R findBy(@NonNull org.springframework.data.domain.Example<S> example, @NonNull java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override @NonNull public List<Notification> findAll(@NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public org.springframework.data.domain.Page<Notification> findAll(@NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
    }


    /**
     * In-memory implementation of OccurrenceTrackerRepository for testing.
     */
    private static class InMemoryOccurrenceTrackerRepository implements OccurrenceTrackerRepository {
        private final Map<Long, ProcessedOccurrence> occurrences = new HashMap<>();
        private long idCounter = 0;

        @Override
        @NonNull
        public <S extends ProcessedOccurrence> S save(@NonNull S occurrence) {
            if (occurrence.getId() == null) {
                occurrence.setId(++idCounter);
            }
            occurrences.put(occurrence.getId(), occurrence);
            return occurrence;
        }

        @Override
        public boolean existsByNotificationIdAndOccurrenceDate(Long notificationId, LocalDate occurrenceDate) {
            return occurrences.values().stream()
                .anyMatch(o -> o.getNotificationId().equals(notificationId) && 
                              o.getOccurrenceDate().equals(occurrenceDate));
        }

        @Override
        @NonNull
        public Optional<ProcessedOccurrence> findByNotificationIdAndOccurrenceDate(Long notificationId, LocalDate occurrenceDate) {
            return requireNonNull(occurrences.values().stream()
                .filter(o -> o.getNotificationId().equals(notificationId) && 
                            o.getOccurrenceDate().equals(occurrenceDate))
                .findFirst());
        }

        @Override
        public List<ProcessedOccurrence> findByNotificationId(Long notificationId) {
            return occurrences.values().stream()
                .filter(o -> o.getNotificationId().equals(notificationId))
                .toList();
        }

        @Override
        @NonNull
        public Optional<ProcessedOccurrence> findById(@NonNull Long id) {
            return requireNonNull(Optional.ofNullable(occurrences.get(id)));
        }

        // Unused methods for testing
        @Override @NonNull public List<ProcessedOccurrence> findAll() { return new ArrayList<>(occurrences.values()); }
        @Override @NonNull public List<ProcessedOccurrence> findAllById(@NonNull Iterable<Long> ids) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends ProcessedOccurrence> List<S> saveAll(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public boolean existsById(@NonNull Long id) { return occurrences.containsKey(id); }
        @Override public long count() { return occurrences.size(); }
        @Override public void deleteById(@NonNull Long id) { occurrences.remove(id); }
        @Override public void delete(@NonNull ProcessedOccurrence entity) { occurrences.remove(entity.getId()); }
        @Override public void deleteAllById(@NonNull Iterable<? extends Long> ids) { }
        @Override public void deleteAll(@NonNull Iterable<? extends ProcessedOccurrence> entities) { }
        @Override public void deleteAll() { occurrences.clear(); }
        @Override public void flush() { }
        @Override @NonNull public <S extends ProcessedOccurrence> S saveAndFlush(@NonNull S entity) { return save(entity); }
        @Override @NonNull public <S extends ProcessedOccurrence> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public void deleteAllInBatch(@NonNull Iterable<ProcessedOccurrence> entities) { }
        @Override public void deleteAllByIdInBatch(@NonNull Iterable<Long> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override @NonNull public ProcessedOccurrence getOne(@NonNull Long id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public ProcessedOccurrence getById(@NonNull Long id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public ProcessedOccurrence getReferenceById(@NonNull Long id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public <S extends ProcessedOccurrence> Optional<S> findOne(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(Optional.empty()); }
        @Override @NonNull public <S extends ProcessedOccurrence> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends ProcessedOccurrence> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends ProcessedOccurrence> org.springframework.data.domain.Page<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends ProcessedOccurrence> long count(@NonNull org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends ProcessedOccurrence> boolean exists(@NonNull org.springframework.data.domain.Example<S> example) { return false; }
        @Override @NonNull public <S extends ProcessedOccurrence, R> R findBy(@NonNull org.springframework.data.domain.Example<S> example, @NonNull java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override @NonNull public List<ProcessedOccurrence> findAll(@NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public org.springframework.data.domain.Page<ProcessedOccurrence> findAll(@NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
    }

    /**
     * In-memory implementation of TodoTaskRepository for testing.
     */
    private static class InMemoryTodoTaskRepository implements TodoTaskRepository {
        private final Map<String, TodoTask> tasks = new HashMap<>();

        @Override
        @NonNull
        public <S extends TodoTask> S save(@NonNull S task) {
            tasks.put(task.getId(), task);
            return task;
        }

        @Override
        @NonNull
        public Optional<TodoTask> findById(@NonNull String id) {
            return requireNonNull(Optional.ofNullable(tasks.get(id)));
        }

        @Override
        public List<TodoTask> findByUserId(String userId) {
            return tasks.values().stream()
                .filter(t -> t.getUserId().equals(userId))
                .toList();
        }

        @Override
        public List<TodoTask> findByUserIdAndCategory(String userId, String category) {
            return tasks.values().stream()
                .filter(t -> t.getUserId().equals(userId) && 
                            Objects.equals(t.getCategory(), category))
                .toList();
        }

        @Override
        public List<TodoTask> findByUserIdAndParentId(String userId, String parentId) {
            return tasks.values().stream()
                .filter(t -> t.getUserId().equals(userId) && 
                            Objects.equals(t.getParentId(), parentId))
                .toList();
        }

        @Override
        public boolean existsByUserIdAndSourceNotificationId(String userId, Long sourceNotificationId) {
            return tasks.values().stream()
                .anyMatch(t -> t.getUserId().equals(userId) && 
                              Objects.equals(t.getSourceNotificationId(), sourceNotificationId));
        }

        @Override
        public void delete(@NonNull TodoTask task) {
            tasks.remove(task.getId());
        }

        @Override
        public void deleteAll(@NonNull Iterable<? extends TodoTask> entities) {
            entities.forEach(task -> tasks.remove(task.getId()));
        }

        // Unused methods for testing
        @Override @NonNull public List<TodoTask> findAll() { return new ArrayList<>(tasks.values()); }
        @Override @NonNull public List<TodoTask> findAllById(@NonNull Iterable<String> ids) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends TodoTask> List<S> saveAll(@NonNull Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            entities.forEach(entity -> {
                tasks.put(entity.getId(), entity);
                result.add(entity);
            });
            return result;
        }
        @Override public boolean existsById(@NonNull String s) { return tasks.containsKey(s); }
        @Override public long count() { return tasks.size(); }
        @Override public void deleteById(@NonNull String s) { tasks.remove(s); }
        @Override public void deleteAllById(@NonNull Iterable<? extends String> ids) { }
        @Override public void deleteAll() { tasks.clear(); }
        @Override public void flush() { }
        @Override @NonNull public <S extends TodoTask> S saveAndFlush(@NonNull S entity) { return save(entity); }
        @Override @NonNull public <S extends TodoTask> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public void deleteAllInBatch(@NonNull Iterable<TodoTask> entities) { }
        @Override public void deleteAllByIdInBatch(@NonNull Iterable<String> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override @NonNull public TodoTask getOne(@NonNull String s) { throw new UnsupportedOperationException(); }
        @Override @NonNull public TodoTask getById(@NonNull String s) { throw new UnsupportedOperationException(); }
        @Override @NonNull public TodoTask getReferenceById(@NonNull String s) { throw new UnsupportedOperationException(); }
        @Override @NonNull public <S extends TodoTask> Optional<S> findOne(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(Optional.empty()); }
        @Override @NonNull public <S extends TodoTask> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends TodoTask> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends TodoTask> org.springframework.data.domain.Page<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends TodoTask> long count(@NonNull org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends TodoTask> boolean exists(@NonNull org.springframework.data.domain.Example<S> example) { return false; }
        @Override @NonNull public <S extends TodoTask, R> R findBy(@NonNull org.springframework.data.domain.Example<S> example, @NonNull java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override @NonNull public List<TodoTask> findAll(@NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public org.springframework.data.domain.Page<TodoTask> findAll(@NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
    }

    /**
     * In-memory implementation of UserRepository for testing.
     */
    private static class InMemoryUserRepository implements UserRepository {
        private final Map<Long, User> users = new HashMap<>();
        private long idCounter = 0;

        @Override
        @NonNull
        public <S extends User> S save(@NonNull S user) {
            if (user.getId() == null) {
                user.setId(++idCounter);
            }
            users.put(user.getId(), user);
            return user;
        }

        @Override
        @NonNull
        public Optional<User> findById(@NonNull Long id) {
            return requireNonNull(Optional.ofNullable(users.get(id)));
        }

        @Override
        public Optional<User> findByUsername(String username) {
            return users.values().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
        }

        @Override
        public boolean existsByUsername(String username) {
            return users.values().stream()
                .anyMatch(u -> u.getUsername().equals(username));
        }

        // Unused methods for testing
        @Override @NonNull public List<User> findAll() { return new ArrayList<>(users.values()); }
        @Override @NonNull public List<User> findAllById(@NonNull Iterable<Long> ids) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends User> List<S> saveAll(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public boolean existsById(@NonNull Long id) { return users.containsKey(id); }
        @Override public long count() { return users.size(); }
        @Override public void deleteById(@NonNull Long id) { users.remove(id); }
        @Override public void delete(@NonNull User entity) { users.remove(entity.getId()); }
        @Override public void deleteAllById(@NonNull Iterable<? extends Long> ids) { }
        @Override public void deleteAll(@NonNull Iterable<? extends User> entities) { }
        @Override public void deleteAll() { users.clear(); }
        @Override public void flush() { }
        @Override @NonNull public <S extends User> S saveAndFlush(@NonNull S entity) { return save(entity); }
        @Override @NonNull public <S extends User> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public void deleteAllInBatch(@NonNull Iterable<User> entities) { }
        @Override public void deleteAllByIdInBatch(@NonNull Iterable<Long> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override @NonNull public User getOne(@NonNull Long id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public User getById(@NonNull Long id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public User getReferenceById(@NonNull Long id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public <S extends User> Optional<S> findOne(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(Optional.empty()); }
        @Override @NonNull public <S extends User> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends User> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends User> org.springframework.data.domain.Page<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends User> long count(@NonNull org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends User> boolean exists(@NonNull org.springframework.data.domain.Example<S> example) { return false; }
        @Override @NonNull public <S extends User, R> R findBy(@NonNull org.springframework.data.domain.Example<S> example, @NonNull java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override @NonNull public List<User> findAll(@NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public org.springframework.data.domain.Page<User> findAll(@NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
    }

    // ==================== Property Tests for Idempotency and Error Resilience ====================

    /**
     * Feature: recurring-action-todo, Property 10: Processing Idempotency
     * For any recurring notification and occurrence date, running the scheduler N times (where N >= 1)
     * SHALL produce the same final state as running it exactly once:
     * - The same set of TODO tasks SHALL exist
     * - Exactly one ProcessedOccurrence record SHALL exist for that notification and date
     * - An occurrence SHALL only be marked as processed after all TODO tasks for that occurrence are successfully created
     * 
     * Validates: Requirements 5.1, 5.4, 5.5
     */
    @Property(tries = 100)
    @Label("Feature: recurring-action-todo, Property 10: Processing Idempotency")
    void processingIdempotency(
            @ForAll @IntRange(min = 1, max = 5) int numActiveUsers,
            @ForAll @IntRange(min = 1, max = 3) int numRuns,
            @ForAll @NotBlank @Size(max = 200) String actionDescription,
            @ForAll @NotBlank @Size(max = 50) String actionCategory
    ) {
        TestContext ctx = createTestContext();
        
        // Create active users
        Set<Long> activeUserIds = new HashSet<>();
        for (int i = 1; i <= numActiveUsers; i++) {
            User user = new User("user" + i, "password", Set.of("ROLE_USER"));
            user.setId((long) i);
            user.setEnabled(true);
            ctx.userRepo.save(user);
            activeUserIds.add((long) i);
        }
        
        // Create a recurring notification with action item (DAILY recurrence always triggers)
        Notification notification = new Notification(
            "Test Subject",
            "Test Body",
            true, // global
            null, // no expiration
            new HashSet<>(),
            new RecurrenceRule(RecurrenceRule.RecurrenceType.DAILY, null, null).toJson(),
            new ActionItem(actionDescription, actionCategory)
        );
        notification.setId(1L);
        ctx.notificationRepo.save(notification);
        
        // Run processing first time
        ProcessingResult firstResult = ctx.service.processRecurringNotifications();
        
        // Run processing additional times
        for (int run = 1; run < numRuns; run++) {
            ctx.service.processRecurringNotifications();
        }
        
        // Verify: exactly one ProcessedOccurrence record exists
        List<ProcessedOccurrence> occurrences = ctx.occurrenceRepo.findByNotificationId(1L);
        assertThat(occurrences)
            .as("Exactly one ProcessedOccurrence record should exist after %d runs", numRuns)
            .hasSize(1);
        
        // Verify: the same set of TODO tasks exists (no duplicates)
        List<TodoTask> allTasks = ctx.todoRepo.findAll();
        assertThat(allTasks)
            .as("Number of TODO tasks should equal number of active users (no duplicates)")
            .hasSize(numActiveUsers);
        
        // Verify: TODOs were created for exactly the active users
        // Note: Tasks are stored with usernames (e.g., "user1"), not numeric IDs
        Set<String> actualUsernames = allTasks.stream()
            .map(TodoTask::getUserId)
            .collect(Collectors.toSet());
        Set<String> expectedUsernames = activeUserIds.stream()
            .map(id -> "user" + id)
            .collect(Collectors.toSet());
        assertThat(actualUsernames)
            .as("TODOs should be created for exactly the active users")
            .containsExactlyInAnyOrderElementsOf(expectedUsernames);
        
        // Verify: first run created the expected number of TODOs
        assertThat(firstResult.todosCreated())
            .as("First run should create TODOs for all active users")
            .isEqualTo(numActiveUsers);
        
        // Verify: first run processed exactly one notification
        assertThat(firstResult.notificationsProcessed())
            .as("First run should process exactly one notification")
            .isEqualTo(1);
    }

    /**
     * Feature: recurring-action-todo, Property 9: Error Resilience
     * For any set of notifications being processed where some operations fail:
     * - If TODO creation fails for one user, TODO tasks SHALL still be created for other users of the same notification
     * - If processing fails for one notification, other notifications SHALL still be processed
     * - The total number of successfully created tasks SHALL equal the sum of tasks created for all non-failing operations
     * 
     * Validates: Requirements 4.5, 5.2, 5.3
     */
    @Property(tries = 100)
    @Label("Feature: recurring-action-todo, Property 9: Error Resilience")
    void errorResilience(
            @ForAll @IntRange(min = 2, max = 5) int numNotifications,
            @ForAll @IntRange(min = 1, max = 3) int numActiveUsers,
            @ForAll @IntRange(min = 0, max = 2) int failingNotificationIndex
    ) {
        // Adjust failing index to be within bounds
        int actualFailingIndex = failingNotificationIndex % numNotifications;
        
        // Create test context with a failing notification repository
        InMemoryNotificationRepository notificationRepo = new InMemoryNotificationRepository();
        InMemoryOccurrenceTrackerRepository occurrenceRepo = new InMemoryOccurrenceTrackerRepository();
        FailingTodoTaskRepository todoRepo = new FailingTodoTaskRepository(actualFailingIndex + 1);
        InMemoryUserRepository userRepo = new InMemoryUserRepository();
        RecurrenceService recurrenceService = new RecurrenceService(new Random(42));
        
        RecurringActionTodoService service = new RecurringActionTodoService(
            notificationRepo, occurrenceRepo, todoRepo, recurrenceService, userRepo
        );
        
        // Create active users
        for (int i = 1; i <= numActiveUsers; i++) {
            User user = new User("user" + i, "password", Set.of("ROLE_USER"));
            user.setId((long) i);
            user.setEnabled(true);
            userRepo.save(user);
        }
        
        // Create multiple notifications with action items (DAILY recurrence always triggers)
        for (int i = 1; i <= numNotifications; i++) {
            Notification notification = new Notification(
                "Test Subject " + i,
                "Test Body " + i,
                true, // global
                null, // no expiration
                new HashSet<>(),
                new RecurrenceRule(RecurrenceRule.RecurrenceType.DAILY, null, null).toJson(),
                new ActionItem("Action " + i, "Category")
            );
            notification.setId((long) i);
            notificationRepo.save(notification);
        }
        
        // Run processing
        ProcessingResult result = service.processRecurringNotifications();
        
        // Verify: errors were recorded for the failing notification
        assertThat(result.errors())
            .as("Should have recorded errors for the failing notification")
            .isGreaterThanOrEqualTo(1);
        
        // Verify: other notifications were still processed
        int expectedSuccessfulNotifications = numNotifications - 1;
        assertThat(result.notificationsProcessed())
            .as("Should have processed %d notifications successfully", expectedSuccessfulNotifications)
            .isEqualTo(expectedSuccessfulNotifications);
        
        // Verify: TODOs were created for successful notifications
        int expectedTodos = expectedSuccessfulNotifications * numActiveUsers;
        assertThat(result.todosCreated())
            .as("Should have created %d TODOs for successful notifications", expectedTodos)
            .isEqualTo(expectedTodos);
        
        // Verify: ProcessedOccurrence records exist only for successful notifications
        long processedCount = occurrenceRepo.findAll().size();
        assertThat(processedCount)
            .as("Should have %d ProcessedOccurrence records for successful notifications", expectedSuccessfulNotifications)
            .isEqualTo(expectedSuccessfulNotifications);
    }

    /**
     * In-memory TodoTaskRepository that fails for a specific notification ID.
     * Used to test error resilience.
     */
    private static class FailingTodoTaskRepository extends InMemoryTodoTaskRepository {
        private final long failingNotificationId;

        FailingTodoTaskRepository(long failingNotificationId) {
            this.failingNotificationId = failingNotificationId;
        }

        @Override
        @NonNull
        public <S extends TodoTask> S save(@NonNull S task) {
            if (task.getSourceNotificationId() != null && 
                task.getSourceNotificationId().equals(failingNotificationId)) {
                throw new RuntimeException("Simulated failure for notification " + failingNotificationId);
            }
            return super.save(task);
        }
    }
}
