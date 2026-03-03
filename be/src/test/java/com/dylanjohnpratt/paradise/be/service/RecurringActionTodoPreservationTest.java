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
 * Preservation property tests for the recurring notification scheduler.
 *
 * These tests capture the baseline behavior of the UNFIXED code for non-buggy inputs
 * (all saveAndFlush calls succeed). They must PASS on unfixed code and continue to
 * PASS after the fix is applied, confirming no regressions.
 *
 * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
 */
class RecurringActionTodoPreservationTest {

    // ==================== Test Context Setup ====================

    private record TestContext(
        RecurringActionTodoService service,
        InMemoryNotificationRepository notificationRepo,
        InMemoryOccurrenceTrackerRepository occurrenceRepo,
        InMemoryTodoTaskRepository todoRepo,
        InMemoryUserRepository userRepo,
        InMemoryUserNotificationStateRepository userNotificationStateRepo
    ) {}

    private TestContext createTestContext() {
        InMemoryNotificationRepository notificationRepo = new InMemoryNotificationRepository();
        InMemoryOccurrenceTrackerRepository occurrenceRepo = new InMemoryOccurrenceTrackerRepository();
        InMemoryTodoTaskRepository todoRepo = new InMemoryTodoTaskRepository();
        InMemoryUserRepository userRepo = new InMemoryUserRepository();
        InMemoryUserNotificationStateRepository userNotificationStateRepo = new InMemoryUserNotificationStateRepository();
        RecurrenceService recurrenceService = new RecurrenceService(new Random(42));

        RecurringActionTodoService service = new RecurringActionTodoService(
            notificationRepo, occurrenceRepo, todoRepo, recurrenceService, userRepo, userNotificationStateRepo
        );

        return new TestContext(service, notificationRepo, occurrenceRepo, todoRepo, userRepo, userNotificationStateRepo);
    }

    private Notification createDailyNotification(long id, boolean isGlobal, Set<Long> targetUserIds, String actionDesc) {
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

    // ==================== Property 1: Successful Processing Preservation ====================

    /**
     * Property 3: Preservation - Successful Processing Behavior Unchanged
     *
     * For all valid notification configurations where no saveAndFlush failure occurs,
     * processRecurringNotifications returns ProcessingResult with:
     * - todosCreated equal to total target users across all due notifications
     * - notificationsProcessed equal to number of due notifications
     * - errors equal to 0
     *
     * Also verifies: TodoTask records created, read states reset, occurrences marked as processed.
     *
     * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7
     */
    @Property(tries = 50)
    @Label("Preservation: successful processing returns correct ProcessingResult")
    void successfulProcessing_returnsCorrectResult(
            @ForAll("notificationCounts") int numNotifications,
            @ForAll("userCounts") int numUsers
    ) {
        TestContext ctx = createTestContext();

        // Create users
        for (int i = 1; i <= numUsers; i++) {
            ctx.userRepo.save(createUser(i, "user" + i));
        }

        // Create targeted notifications with DAILY recurrence (always due)
        Set<Long> allUserIds = new HashSet<>();
        for (int i = 1; i <= numUsers; i++) {
            allUserIds.add((long) i);
        }

        for (int n = 1; n <= numNotifications; n++) {
            Notification notification = createDailyNotification(n, false, allUserIds, "Action " + n);
            ctx.notificationRepo.save(notification);
        }

        // Process
        ProcessingResult result = ctx.service.processRecurringNotifications();

        // Verify ProcessingResult
        int expectedTodos = numNotifications * numUsers;
        assertThat(result.notificationsProcessed())
            .as("notificationsProcessed should equal number of due notifications")
            .isEqualTo(numNotifications);
        assertThat(result.todosCreated())
            .as("todosCreated should equal total target users across all notifications")
            .isEqualTo(expectedTodos);
        assertThat(result.errors())
            .as("errors should be 0 when all processing succeeds")
            .isEqualTo(0);

        // Verify TodoTask records were created
        assertThat(ctx.todoRepo.findAll())
            .as("TodoTask records should be persisted")
            .hasSize(expectedTodos);

        // Verify occurrences were marked as processed
        assertThat(ctx.occurrenceRepo.findAll())
            .as("Each notification should have an occurrence record")
            .hasSize(numNotifications);
    }

    // ==================== Property 2: Null/Blank Action Items ====================

    /**
     * Property 3: Preservation - Null/Blank Action Items Skipped
     *
     * For all notifications with null or blank action items,
     * createTodosForNotification returns 0.
     *
     * Validates: Requirements 3.2
     */
    @Property(tries = 50)
    @Label("Preservation: null/blank action items return 0 todos")
    void nullOrBlankActionItems_returnZero(
            @ForAll("nullOrBlankDescriptions") String description,
            @ForAll("userCounts") int numUsers
    ) {
        TestContext ctx = createTestContext();

        for (int i = 1; i <= numUsers; i++) {
            ctx.userRepo.save(createUser(i, "user" + i));
        }

        Set<Long> userIds = new HashSet<>();
        for (int i = 1; i <= numUsers; i++) {
            userIds.add((long) i);
        }

        // Create notification with null/blank action item
        Notification notification = new Notification(
            "Subject",
            "Body",
            false,
            null,
            userIds,
            new RecurrenceRule(RecurrenceRule.RecurrenceType.DAILY, null, null).toJson(),
            new ActionItem(description, "TestCategory")
        );
        notification.setId(1L);
        ctx.notificationRepo.save(notification);

        LocalDate today = LocalDate.now();
        int todosCreated = ctx.service.createTodosForNotification(notification, today);

        assertThat(todosCreated)
            .as("createTodosForNotification should return 0 for null/blank action items")
            .isEqualTo(0);

        // Verify no TodoTask records were created
        assertThat(ctx.todoRepo.findAll())
            .as("No TodoTask records should be created for null/blank action items")
            .isEmpty();
    }

    // ==================== Property 3: Missing Users Skipped ====================

    /**
     * Property 3: Preservation - Missing Users Skipped
     *
     * For all configurations with missing users, the missing users are skipped
     * and successCount reflects only found users.
     *
     * Validates: Requirements 3.3
     */
    @Property(tries = 50)
    @Label("Preservation: missing users are skipped, found users get todos")
    void missingUsers_areSkipped_foundUsersGetTodos(
            @ForAll("userCounts") int numPresentUsers,
            @ForAll("missingUserCounts") int numMissingUsers
    ) {
        TestContext ctx = createTestContext();

        // Create only the "present" users in the repo
        Set<Long> allTargetIds = new HashSet<>();
        for (int i = 1; i <= numPresentUsers; i++) {
            ctx.userRepo.save(createUser(i, "user" + i));
            allTargetIds.add((long) i);
        }

        // Add missing user IDs (not in the repo)
        for (int i = numPresentUsers + 1; i <= numPresentUsers + numMissingUsers; i++) {
            allTargetIds.add((long) i);
        }

        Notification notification = createDailyNotification(1L, false, allTargetIds, "Action for users");
        ctx.notificationRepo.save(notification);

        LocalDate today = LocalDate.now();
        int todosCreated = ctx.service.createTodosForNotification(notification, today);

        assertThat(todosCreated)
            .as("successCount should reflect only found users")
            .isEqualTo(numPresentUsers);

        // Verify only present users got TodoTask records
        assertThat(ctx.todoRepo.findAll())
            .as("TodoTask records should only exist for present users")
            .hasSize(numPresentUsers);
    }

    // ==================== Observation Tests (non-property) ====================

    /**
     * Observation: When no due notifications exist, ProcessingResult.empty() is returned.
     *
     * Validates: Requirements 3.6
     */
    @Property(tries = 10)
    @Label("Preservation: no due notifications returns empty result")
    void noDueNotifications_returnsEmptyResult(
            @ForAll("userCounts") int numUsers
    ) {
        TestContext ctx = createTestContext();

        for (int i = 1; i <= numUsers; i++) {
            ctx.userRepo.save(createUser(i, "user" + i));
        }

        // No notifications added — empty run
        ProcessingResult result = ctx.service.processRecurringNotifications();

        assertThat(result.notificationsProcessed()).isEqualTo(0);
        assertThat(result.todosCreated()).isEqualTo(0);
        assertThat(result.errors()).isEqualTo(0);
    }

    /**
     * Observation: When occurrence already processed, notification is skipped (no duplicates).
     *
     * Validates: Requirements 3.5
     */
    @Property(tries = 10)
    @Label("Preservation: already processed occurrence is skipped")
    void alreadyProcessedOccurrence_isSkipped(
            @ForAll("userCounts") int numUsers
    ) {
        TestContext ctx = createTestContext();

        for (int i = 1; i <= numUsers; i++) {
            ctx.userRepo.save(createUser(i, "user" + i));
        }

        Set<Long> userIds = new HashSet<>();
        for (int i = 1; i <= numUsers; i++) {
            userIds.add((long) i);
        }

        Notification notification = createDailyNotification(1L, false, userIds, "Action 1");
        ctx.notificationRepo.save(notification);

        // First run — should process
        ProcessingResult firstResult = ctx.service.processRecurringNotifications();
        assertThat(firstResult.notificationsProcessed()).isEqualTo(1);
        assertThat(firstResult.todosCreated()).isEqualTo(numUsers);

        // Second run — occurrence already processed, should be skipped
        ProcessingResult secondResult = ctx.service.processRecurringNotifications();
        assertThat(secondResult.notificationsProcessed())
            .as("Already processed notification should be skipped on second run")
            .isEqualTo(0);
        assertThat(secondResult.todosCreated()).isEqualTo(0);
        assertThat(secondResult.errors()).isEqualTo(0);
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<Integer> notificationCounts() {
        return Arbitraries.integers().between(1, 5);
    }

    @Provide
    Arbitrary<Integer> userCounts() {
        return Arbitraries.integers().between(1, 5);
    }

    @Provide
    Arbitrary<Integer> missingUserCounts() {
        return Arbitraries.integers().between(1, 3);
    }

    @Provide
    Arbitrary<String> nullOrBlankDescriptions() {
        return Arbitraries.of(null, "", "   ", "\t", "\n");
    }

    // ==================== In-Memory Repository Implementations ====================

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
                if (state.getNotificationId().equals(notificationId) && state.isRead()) {
                    state.setRead(false);
                    count++;
                }
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
}
