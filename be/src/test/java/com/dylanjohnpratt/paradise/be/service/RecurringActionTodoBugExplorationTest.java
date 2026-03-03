package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.model.*;
import com.dylanjohnpratt.paradise.be.repository.*;
import net.jqwik.api.*;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;

/**
 * Bug condition exploration tests for the recurring notification scheduler.
 *
 * These tests encode the EXPECTED (fixed) behavior. They are designed to FAIL
 * on the current unfixed code, confirming the bug exists:
 *
 * Bug 1: createTodosForNotification() throws TodoCreationException despite
 *         the catch block comment saying "log and continue"
 * Bug 2: Shared @Transactional boundary between scheduler and service causes
 *         full rollback when any single user's todo creation fails
 *
 * Validates: Requirements 1.1, 1.2, 1.3, 1.4
 */
class RecurringActionTodoBugExplorationTest {

    // ==================== Test Context Setup ====================

    private record TestContext(
        RecurringActionTodoService service,
        InMemoryNotificationRepository notificationRepo,
        InMemoryOccurrenceTrackerRepository occurrenceRepo,
        TodoTaskRepository todoRepo,
        InMemoryUserRepository userRepo,
        InMemoryUserNotificationStateRepository userNotificationStateRepo
    ) {}

    private TestContext createTestContext(TodoTaskRepository todoRepo) {
        InMemoryNotificationRepository notificationRepo = new InMemoryNotificationRepository();
        InMemoryOccurrenceTrackerRepository occurrenceRepo = new InMemoryOccurrenceTrackerRepository();
        InMemoryUserRepository userRepo = new InMemoryUserRepository();
        InMemoryUserNotificationStateRepository userNotificationStateRepo = new InMemoryUserNotificationStateRepository();
        RecurrenceService recurrenceService = new RecurrenceService(new Random(42));

        RecurringActionTodoService service = new RecurringActionTodoService(
            notificationRepo, occurrenceRepo, todoRepo, recurrenceService, userRepo, userNotificationStateRepo
        );

        return new TestContext(service, notificationRepo, occurrenceRepo, todoRepo, userRepo, userNotificationStateRepo);
    }

    private Notification createNotification(long id, boolean isGlobal, Set<Long> targetUserIds, String actionDesc) {
        Notification notification = new Notification(
            "Subject " + id,
            "Body " + id,
            isGlobal,
            null,
            targetUserIds,
            new RecurrenceRule(RecurrenceRule.RecurrenceType.DAILY, null, null).toJson(),
            new ActionItem(actionDesc, "TestCategory")
        );
        notification.setId(id);
        return notification;
    }

    private User createUser(long id, String username) {
        User user = new User(username, "password", Set.of("ROLE_USER"));
        user.setId(id);
        user.setEnabled(true);
        return user;
    }

    // ==================== Test 1: Single User Failure in createTodosForNotification ====================

    /**
     * Property 1: Fault Condition - TodoCreationException Propagation
     *
     * Mock saveAndFlush to throw for user #2 of 3 target users.
     * Assert that createTodosForNotification does NOT throw TodoCreationException,
     * logs the error, and returns the count of successfully created todos (expected: 2).
     *
     * On UNFIXED code this will FAIL because the method throws TodoCreationException.
     *
     * Validates: Requirements 1.1
     */
    @Property(tries = 10)
    @Label("Bug Exploration: createTodosForNotification should not throw when one user fails")
    void createTodosForNotification_shouldNotThrow_whenOneUserFails(
            @ForAll("actionDescriptions") String actionDescription
    ) {
        // User #2 will fail on saveAndFlush
        UserFailingTodoTaskRepository failingRepo = new UserFailingTodoTaskRepository("user2");
        TestContext ctx = createTestContext(failingRepo);

        // Create 3 users
        ctx.userRepo.save(createUser(1L, "user1"));
        ctx.userRepo.save(createUser(2L, "user2"));
        ctx.userRepo.save(createUser(3L, "user3"));

        // Create a targeted notification for all 3 users
        Notification notification = createNotification(1L, false, Set.of(1L, 2L, 3L), actionDescription);
        ctx.notificationRepo.save(notification);

        LocalDate today = LocalDate.now();

        // Expected behavior: no exception thrown, returns 2 (user1 and user3 succeed)
        // Bug behavior: throws TodoCreationException
        int todosCreated = ctx.service.createTodosForNotification(notification, today);

        assertThat(todosCreated)
            .as("Should return count of successfully created todos (2 of 3 users)")
            .isEqualTo(2);
    }

    // ==================== Test 2: Multi-Notification - createTodosForNotification throws instead of continuing ====================

    /**
     * Property 1: Fault Condition - Shared Transaction Rollback
     *
     * Set up 3 due notifications with FailingTodoTaskRepository configured to fail
     * on notification #2. Call createTodosForNotification directly for notification #2
     * and assert it does NOT throw TodoCreationException but instead returns 0.
     *
     * On UNFIXED code this will FAIL because createTodosForNotification throws
     * TodoCreationException, which in a real Spring @Transactional context would
     * mark the shared transaction as rollback-only, causing all notifications to roll back.
     *
     * Note: The actual transaction rollback (Requirements 1.2, 1.3, 1.4) cannot be
     * demonstrated without Spring's transaction infrastructure. This test confirms
     * the root cause: the errant throw that triggers the rollback chain.
     *
     * Validates: Requirements 1.2, 1.3, 1.4
     */
    @Property(tries = 10)
    @Label("Bug Exploration: createTodosForNotification should not throw for failing notification")
    void createTodosForNotification_shouldNotThrow_forFailingNotification(
            @ForAll("userCounts") int numUsers
    ) {
        // Notification #2 will fail on all users (FailingTodoTaskRepository fails by notification ID)
        FailingTodoTaskRepository failingRepo = new FailingTodoTaskRepository(2L);
        TestContext ctx = createTestContext(failingRepo);

        // Create users
        for (int i = 1; i <= numUsers; i++) {
            ctx.userRepo.save(createUser(i, "user" + i));
        }

        // Create notification #2 (the one that will fail)
        Notification failingNotification = createNotification(2L, true, new HashSet<>(), "Action 2");
        ctx.notificationRepo.save(failingNotification);

        LocalDate today = LocalDate.now();

        // Expected behavior: createTodosForNotification catches the exception, logs it,
        // and returns 0 (all users failed for this notification).
        // Bug behavior: throws TodoCreationException, which in a @Transactional context
        // would mark the transaction rollback-only, rolling back ALL notifications' work.
        int todosCreated = ctx.service.createTodosForNotification(failingNotification, today);

        assertThat(todosCreated)
            .as("Should return 0 since all users failed for this notification")
            .isEqualTo(0);
    }

    // ==================== Test 3: Single Notification Single User Failure ====================

    /**
     * Property 1: Fault Condition - Single notification, single user failure
     *
     * Single notification, single user, saveAndFlush throws. Assert that
     * createTodosForNotification does NOT throw and returns 0.
     *
     * On UNFIXED code this will FAIL because TodoCreationException propagates,
     * which in a real Spring @Transactional context would cause the entire
     * scheduler run to fail fatally.
     *
     * Validates: Requirements 1.1, 1.2
     */
    @Property(tries = 10)
    @Label("Bug Exploration: single notification single user failure should not throw")
    void singleNotification_singleUserFailure_shouldNotThrow(
            @ForAll("actionDescriptions") String actionDescription
    ) {
        // The only user will fail
        UserFailingTodoTaskRepository failingRepo = new UserFailingTodoTaskRepository("user1");
        TestContext ctx = createTestContext(failingRepo);

        ctx.userRepo.save(createUser(1L, "user1"));

        Notification notification = createNotification(1L, false, Set.of(1L), actionDescription);
        ctx.notificationRepo.save(notification);

        LocalDate today = LocalDate.now();

        // Expected: createTodosForNotification catches the exception and returns 0
        // Bug behavior: throws TodoCreationException
        int todosCreated = ctx.service.createTodosForNotification(notification, today);

        assertThat(todosCreated)
            .as("Should return 0 since the only user's todo creation failed")
            .isEqualTo(0);
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<String> actionDescriptions() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
    }

    @Provide
    Arbitrary<Integer> userCounts() {
        return Arbitraries.integers().between(1, 5);
    }

    // ==================== Custom Failing Repository: Fails by User ====================

    /**
     * TodoTaskRepository that fails when saving a todo for a specific username.
     * Used to simulate saveAndFlush throwing for a specific user.
     */
    private static class UserFailingTodoTaskRepository extends InMemoryTodoTaskRepository {
        private final String failingUsername;

        UserFailingTodoTaskRepository(String failingUsername) {
            this.failingUsername = failingUsername;
        }

        @Override
        @NonNull
        public <S extends TodoTask> S save(@NonNull S task) {
            if (task.getUserId() != null && task.getUserId().equals(failingUsername)) {
                throw new RuntimeException("Simulated DB constraint violation for user " + failingUsername);
            }
            return super.save(task);
        }
    }

    // ==================== In-Memory Repository Implementations ====================
    // (Duplicated from RecurringActionTodoServicePropertyTest since they are private inner classes)

    static class InMemoryNotificationRepository implements NotificationRepository {
        private final Map<Long, Notification> notifications = new HashMap<>();
        private long idCounter = 0;

        @Override @NonNull public <S extends Notification> S save(@NonNull S notification) {
            if (notification.getId() == null) { notification.setId(++idCounter); }
            notifications.put(notification.getId(), notification);
            return notification;
        }
        @Override @NonNull public Optional<Notification> findById(@NonNull Long id) { return requireNonNull(Optional.ofNullable(notifications.get(id))); }
        @Override public List<Notification> findByIsGlobalTrueAndDeletedFalse() {
            return notifications.values().stream().filter(n -> n.isGlobal() && !n.isDeleted()).toList();
        }
        @Override public List<Notification> findByTargetUserIdAndNotDeleted(Long userId) {
            return notifications.values().stream().filter(n -> !n.isDeleted() && n.getTargetUserIds().contains(userId)).toList();
        }
        @Override public List<Notification> findAllForUser(Long userId) {
            return notifications.values().stream().filter(n -> !n.isDeleted() && (n.isGlobal() || n.getTargetUserIds().contains(userId))).toList();
        }
        @Override public List<Notification> findNonExpiredForUser(Long userId, LocalDateTime now) {
            return notifications.values().stream()
                .filter(n -> !n.isDeleted() && (n.isGlobal() || n.getTargetUserIds().contains(userId)))
                .filter(n -> n.getExpiresAt() == null || n.getExpiresAt().isAfter(now)).toList();
        }
        @Override public List<Long> findExpiredPastRetention(LocalDateTime retentionCutoff) {
            return notifications.values().stream()
                .filter(n -> n.getExpiresAt() != null && n.getExpiresAt().isBefore(retentionCutoff))
                .map(Notification::getId).toList();
        }
        @Override public int countExpiredPastRetention(LocalDateTime retentionCutoff) {
            return (int) notifications.values().stream()
                .filter(n -> n.getExpiresAt() != null && n.getExpiresAt().isBefore(retentionCutoff)).count();
        }
        @Override public List<Notification> findActiveRecurringNotificationsWithActionItems(LocalDateTime now) {
            return notifications.values().stream()
                .filter(n -> !n.isDeleted())
                .filter(n -> n.getRecurrenceRuleJson() != null)
                .filter(n -> n.getActionItem() != null && n.getActionItem().getDescription() != null && !n.getActionItem().getDescription().isBlank())
                .filter(n -> n.getExpiresAt() == null || n.getExpiresAt().isAfter(now)).toList();
        }
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

    static class InMemoryOccurrenceTrackerRepository implements OccurrenceTrackerRepository {
        private final Map<Long, ProcessedOccurrence> occurrences = new HashMap<>();
        private long idCounter = 0;

        @Override @NonNull public <S extends ProcessedOccurrence> S save(@NonNull S occurrence) {
            if (occurrence.getId() == null) { occurrence.setId(++idCounter); }
            occurrences.put(occurrence.getId(), occurrence);
            return occurrence;
        }
        @Override public boolean existsByNotificationIdAndOccurrenceDate(Long notificationId, LocalDate occurrenceDate) {
            return occurrences.values().stream().anyMatch(o -> o.getNotificationId().equals(notificationId) && o.getOccurrenceDate().equals(occurrenceDate));
        }
        @Override @NonNull public Optional<ProcessedOccurrence> findByNotificationIdAndOccurrenceDate(Long notificationId, LocalDate occurrenceDate) {
            return requireNonNull(occurrences.values().stream().filter(o -> o.getNotificationId().equals(notificationId) && o.getOccurrenceDate().equals(occurrenceDate)).findFirst());
        }
        @Override public List<ProcessedOccurrence> findByNotificationId(Long notificationId) {
            return occurrences.values().stream().filter(o -> o.getNotificationId().equals(notificationId)).toList();
        }
        @Override @NonNull public Optional<ProcessedOccurrence> findById(@NonNull Long id) { return requireNonNull(Optional.ofNullable(occurrences.get(id))); }
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

    static class InMemoryTodoTaskRepository implements TodoTaskRepository {
        private final Map<String, TodoTask> tasks = new HashMap<>();

        @Override @NonNull public <S extends TodoTask> S save(@NonNull S task) { tasks.put(task.getId(), task); return task; }
        @Override @NonNull public Optional<TodoTask> findById(@NonNull String id) { return requireNonNull(Optional.ofNullable(tasks.get(id))); }
        @Override public List<TodoTask> findByUserId(String userId) { return tasks.values().stream().filter(t -> t.getUserId().equals(userId)).toList(); }
        @Override public List<TodoTask> findByUserIdAndCategory(String userId, String category) {
            return tasks.values().stream().filter(t -> t.getUserId().equals(userId) && Objects.equals(t.getCategory(), category)).toList();
        }
        @Override public List<TodoTask> findByUserIdAndParentId(String userId, String parentId) {
            return tasks.values().stream().filter(t -> t.getUserId().equals(userId) && Objects.equals(t.getParentId(), parentId)).toList();
        }
        @Override public boolean existsByUserIdAndSourceNotificationId(String userId, Long sourceNotificationId) {
            return tasks.values().stream().anyMatch(t -> t.getUserId().equals(userId) && Objects.equals(t.getSourceNotificationId(), sourceNotificationId));
        }
        @Override public void delete(@NonNull TodoTask task) { tasks.remove(task.getId()); }
        @Override public void deleteAll(@NonNull Iterable<? extends TodoTask> entities) { entities.forEach(task -> tasks.remove(task.getId())); }
        @Override @NonNull public List<TodoTask> findAll() { return new ArrayList<>(tasks.values()); }
        @Override @NonNull public List<TodoTask> findAllById(@NonNull Iterable<String> ids) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends TodoTask> List<S> saveAll(@NonNull Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            entities.forEach(entity -> { tasks.put(entity.getId(), entity); result.add(entity); });
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

    static class InMemoryUserRepository implements UserRepository {
        private final Map<Long, User> users = new HashMap<>();
        private long idCounter = 0;

        @Override @NonNull public <S extends User> S save(@NonNull S user) {
            if (user.getId() == null) { user.setId(++idCounter); }
            users.put(user.getId(), user);
            return user;
        }
        @Override @NonNull public Optional<User> findById(@NonNull Long id) { return requireNonNull(Optional.ofNullable(users.get(id))); }
        @Override public Optional<User> findByUsername(String username) { return users.values().stream().filter(u -> u.getUsername().equals(username)).findFirst(); }
        @Override public boolean existsByUsername(String username) { return users.values().stream().anyMatch(u -> u.getUsername().equals(username)); }
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

    static class InMemoryUserNotificationStateRepository implements UserNotificationStateRepository {
        private final Map<Long, UserNotificationState> states = new HashMap<>();
        private long idCounter = 0;

        @Override @NonNull public <S extends UserNotificationState> S save(@NonNull S state) {
            if (state.getId() == null) { state.setId(++idCounter); }
            states.put(state.getId(), state);
            return state;
        }
        @Override @NonNull public Optional<UserNotificationState> findById(@NonNull Long id) { return requireNonNull(Optional.ofNullable(states.get(id))); }
        @Override public Optional<UserNotificationState> findByNotificationIdAndUserId(Long notificationId, Long userId) {
            return states.values().stream().filter(s -> s.getNotificationId().equals(notificationId) && s.getUserId().equals(userId)).findFirst();
        }
        @Override public List<UserNotificationState> findByUserId(Long userId) { return states.values().stream().filter(s -> s.getUserId().equals(userId)).toList(); }
        @Override public List<UserNotificationState> findByNotificationId(Long notificationId) { return states.values().stream().filter(s -> s.getNotificationId().equals(notificationId)).toList(); }
        @Override public boolean existsByNotificationIdAndUserId(Long notificationId, Long userId) {
            return states.values().stream().anyMatch(s -> s.getNotificationId().equals(notificationId) && s.getUserId().equals(userId));
        }
        @Override public void deleteByNotificationIdIn(List<Long> notificationIds) { states.entrySet().removeIf(e -> notificationIds.contains(e.getValue().getNotificationId())); }
        @Override public void deleteByNotificationId(Long notificationId) { states.entrySet().removeIf(e -> e.getValue().getNotificationId().equals(notificationId)); }
        @Override public int resetReadStateForNotification(Long notificationId) {
            int count = 0;
            for (UserNotificationState state : states.values()) {
                if (state.getNotificationId().equals(notificationId)) { state.markAsUnread(); count++; }
            }
            return count;
        }
        @Override @NonNull public List<UserNotificationState> findAll() { return new ArrayList<>(states.values()); }
        @Override @NonNull public List<UserNotificationState> findAllById(@NonNull Iterable<Long> ids) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends UserNotificationState> List<S> saveAll(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public boolean existsById(@NonNull Long id) { return states.containsKey(id); }
        @Override public long count() { return states.size(); }
        @Override public void deleteById(@NonNull Long id) { states.remove(id); }
        @Override public void delete(@NonNull UserNotificationState entity) { states.remove(entity.getId()); }
        @Override public void deleteAllById(@NonNull Iterable<? extends Long> ids) { }
        @Override public void deleteAll(@NonNull Iterable<? extends UserNotificationState> entities) { }
        @Override public void deleteAll() { states.clear(); }
        @Override public void flush() { }
        @Override @NonNull public <S extends UserNotificationState> S saveAndFlush(@NonNull S entity) { return save(entity); }
        @Override @NonNull public <S extends UserNotificationState> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public void deleteAllInBatch(@NonNull Iterable<UserNotificationState> entities) { }
        @Override public void deleteAllByIdInBatch(@NonNull Iterable<Long> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override @NonNull public UserNotificationState getOne(@NonNull Long id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public UserNotificationState getById(@NonNull Long id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public UserNotificationState getReferenceById(@NonNull Long id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public <S extends UserNotificationState> Optional<S> findOne(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(Optional.empty()); }
        @Override @NonNull public <S extends UserNotificationState> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends UserNotificationState> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends UserNotificationState> org.springframework.data.domain.Page<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends UserNotificationState> long count(@NonNull org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends UserNotificationState> boolean exists(@NonNull org.springframework.data.domain.Example<S> example) { return false; }
        @Override @NonNull public <S extends UserNotificationState, R> R findBy(@NonNull org.springframework.data.domain.Example<S> example, @NonNull java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override @NonNull public List<UserNotificationState> findAll(@NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public org.springframework.data.domain.Page<UserNotificationState> findAll(@NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
    }

    /**
     * FailingTodoTaskRepository that fails for a specific notification ID.
     * Reuses the pattern from RecurringActionTodoServicePropertyTest.
     */
    static class FailingTodoTaskRepository extends InMemoryTodoTaskRepository {
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
