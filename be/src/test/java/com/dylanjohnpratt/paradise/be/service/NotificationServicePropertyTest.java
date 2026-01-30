package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.model.Notification;
import com.dylanjohnpratt.paradise.be.model.TodoTask;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.model.UserNotificationState;
import com.dylanjohnpratt.paradise.be.repository.NotificationRepository;
import com.dylanjohnpratt.paradise.be.repository.TodoTaskRepository;
import com.dylanjohnpratt.paradise.be.repository.UserNotificationStateRepository;
import com.dylanjohnpratt.paradise.be.repository.UserRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for NotificationService targeting and read state functionality.
 * Uses jqwik to verify correctness properties across many generated inputs.
 * Uses in-memory repositories for testing.
 * 
 * Validates: Requirements 1.1, 1.2, 1.4, 4.2, 4.3, 4.4, 4.5
 */
class NotificationServicePropertyTest {

    /**
     * Creates a NotificationService with in-memory repositories for testing.
     */
    private NotificationService createTestService() {
        return new NotificationService(
                new InMemoryNotificationRepository(),
                new InMemoryUserNotificationStateRepository(),
                new InMemoryTodoTaskRepository(),
                new InMemoryUserRepository(),
                new RecurrenceService()
        );
    }

    /**
     * Feature: notification-service, Property 1: User-Specific Notification Targeting
     * For any notification created with a specific set of user IDs, only those users
     * SHALL be able to access the notification, and all specified users SHALL have access.
     * 
     * Validates: Requirements 1.1, 1.4
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 1: User-Specific Notification Targeting")
    void userSpecificNotificationTargeting(
            @ForAll("validSubjects") String subject,
            @ForAll("validMessageBodies") String messageBody,
            @ForAll("userIdSets") Set<Long> targetUserIds,
            @ForAll @IntRange(min = 1001, max = 2000) long nonTargetedUserId
    ) {
        // Ensure nonTargetedUserId is not in the target set
        Set<Long> adjustedTargetUserIds = new HashSet<>(targetUserIds);
        adjustedTargetUserIds.remove(nonTargetedUserId);
        
        // Skip if we removed all users
        if (adjustedTargetUserIds.isEmpty()) {
            return;
        }

        NotificationService service = createTestService();

        // Create user-specific notification
        Notification notification = service.createNotification(
                subject, messageBody, false, adjustedTargetUserIds, null, null, null);

        // Verify all targeted users can access the notification
        for (Long targetUserId : adjustedTargetUserIds) {
            Optional<Notification> retrieved = service.getNotificationById(
                    notification.getId(), targetUserId);
            assertThat(retrieved)
                    .as("Targeted user %d should be able to access notification", targetUserId)
                    .isPresent();
        }

        // Verify non-targeted user cannot access the notification
        Optional<Notification> retrievedByNonTarget = service.getNotificationById(
                notification.getId(), nonTargetedUserId);
        assertThat(retrievedByNonTarget)
                .as("Non-targeted user %d should NOT be able to access notification", nonTargetedUserId)
                .isEmpty();
    }

    /**
     * Feature: notification-service, Property 2: Global Notification Visibility
     * For any notification created as global, all users in the system SHALL be able
     * to access the notification.
     * 
     * Validates: Requirements 1.2
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 2: Global Notification Visibility")
    void globalNotificationVisibility(
            @ForAll("validSubjects") String subject,
            @ForAll("validMessageBodies") String messageBody,
            @ForAll @IntRange(min = 1, max = 1000) long userId1,
            @ForAll @IntRange(min = 1001, max = 2000) long userId2,
            @ForAll @IntRange(min = 2001, max = 3000) long userId3
    ) {
        NotificationService service = createTestService();

        // Create global notification
        Notification notification = service.createNotification(
                subject, messageBody, true, null, null, null, null);

        // Verify multiple different users can all access the notification
        for (Long userId : List.of(userId1, userId2, userId3)) {
            Optional<Notification> retrieved = service.getNotificationById(
                    notification.getId(), userId);
            assertThat(retrieved)
                    .as("User %d should be able to access global notification", userId)
                    .isPresent();
        }
    }

    /**
     * Provides valid notification subjects (1-255 characters).
     */
    @Provide
    Arbitrary<String> validSubjects() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(255)
                .alpha();
    }

    /**
     * Provides valid message bodies (non-empty strings).
     */
    @Provide
    Arbitrary<String> validMessageBodies() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(1000)
                .alpha();
    }

    /**
     * Provides sets of user IDs for targeting.
     */
    @Provide
    Arbitrary<Set<Long>> userIdSets() {
        return Arbitraries.longs()
                .between(1, 1000)
                .set()
                .ofMinSize(1)
                .ofMaxSize(5);
    }


    /**
     * In-memory implementation of NotificationRepository for testing.
     */
    static class InMemoryNotificationRepository implements NotificationRepository {
        private final Map<Long, Notification> notifications = new HashMap<>();
        private long idCounter = 0;

        @Override
        @NonNull
        public <S extends Notification> S save(@NonNull S notification) {
            if (notification.getId() == null) {
                try {
                    var idField = Notification.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(notification, ++idCounter);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
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
                    .collect(Collectors.toList());
        }

        @Override
        public List<Notification> findByTargetUserIdAndNotDeleted(Long userId) {
            return notifications.values().stream()
                    .filter(n -> !n.isDeleted() && n.getTargetUserIds().contains(userId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<Notification> findAllForUser(Long userId) {
            return notifications.values().stream()
                    .filter(n -> !n.isDeleted() && (n.isGlobal() || n.getTargetUserIds().contains(userId)))
                    .collect(Collectors.toList());
        }

        @Override
        public List<Notification> findNonExpiredForUser(Long userId, LocalDateTime now) {
            return notifications.values().stream()
                    .filter(n -> !n.isDeleted() && (n.isGlobal() || n.getTargetUserIds().contains(userId)))
                    .filter(n -> n.getExpiresAt() == null || n.getExpiresAt().isAfter(now))
                    .collect(Collectors.toList());
        }

        @Override
        public List<Long> findExpiredPastRetention(LocalDateTime retentionCutoff) {
            return notifications.values().stream()
                    .filter(n -> n.getExpiresAt() != null && n.getExpiresAt().isBefore(retentionCutoff))
                    .map(Notification::getId)
                    .collect(Collectors.toList());
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
                    .filter(n -> n.getActionItem() != null 
                            && n.getActionItem().getDescription() != null 
                            && !n.getActionItem().getDescription().isBlank())
                    .filter(n -> n.getExpiresAt() == null || n.getExpiresAt().isAfter(now))
                    .collect(Collectors.toList());
        }

        // Unused methods for testing
        @Override @NonNull public List<Notification> findAll() { return new ArrayList<>(notifications.values()); }
        @Override @NonNull public List<Notification> findAllById(@NonNull Iterable<Long> ids) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends Notification> List<S> saveAll(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public boolean existsById(@NonNull Long id) { return notifications.containsKey(id); }
        @Override public long count() { return notifications.size(); }
        @Override public void deleteById(@NonNull Long id) { notifications.remove(id); }
        @Override public void delete(@NonNull Notification entity) { notifications.remove(entity.getId()); }
        @Override public void deleteAll(@NonNull Iterable<? extends Notification> entities) { }
        @Override public void deleteAllById(@NonNull Iterable<? extends Long> ids) { }
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
     * In-memory implementation of UserNotificationStateRepository for testing.
     */
    static class InMemoryUserNotificationStateRepository implements UserNotificationStateRepository {
        private final Map<Long, UserNotificationState> states = new HashMap<>();
        private long idCounter = 0;

        @Override
        @NonNull
        public <S extends UserNotificationState> S save(@NonNull S state) {
            if (state.getId() == null) {
                try {
                    var idField = UserNotificationState.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(state, ++idCounter);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            states.put(state.getId(), state);
            return state;
        }

        @Override
        @NonNull
        public Optional<UserNotificationState> findById(@NonNull Long id) {
            return requireNonNull(Optional.ofNullable(states.get(id)));
        }

        @Override
        public Optional<UserNotificationState> findByNotificationIdAndUserId(Long notificationId, Long userId) {
            return states.values().stream()
                    .filter(s -> s.getNotificationId().equals(notificationId) && s.getUserId().equals(userId))
                    .findFirst();
        }

        @Override
        public List<UserNotificationState> findByUserId(Long userId) {
            return states.values().stream()
                    .filter(s -> s.getUserId().equals(userId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<UserNotificationState> findByNotificationId(Long notificationId) {
            return states.values().stream()
                    .filter(s -> s.getNotificationId().equals(notificationId))
                    .collect(Collectors.toList());
        }

        @Override
        public boolean existsByNotificationIdAndUserId(Long notificationId, Long userId) {
            return states.values().stream()
                    .anyMatch(s -> s.getNotificationId().equals(notificationId) && s.getUserId().equals(userId));
        }

        @Override
        public void deleteByNotificationIdIn(List<Long> notificationIds) {
            states.entrySet().removeIf(entry -> notificationIds.contains(entry.getValue().getNotificationId()));
        }

        @Override
        public void deleteByNotificationId(Long notificationId) {
            states.entrySet().removeIf(entry -> entry.getValue().getNotificationId().equals(notificationId));
        }

        @Override
        public int resetReadStateForNotification(Long notificationId) {
            int count = 0;
            for (UserNotificationState state : states.values()) {
                if (state.getNotificationId().equals(notificationId)) {
                    state.markAsUnread();
                    count++;
                }
            }
            return count;
        }

        // Unused methods for testing
        @Override @NonNull public List<UserNotificationState> findAll() { return new ArrayList<>(states.values()); }
        @Override @NonNull public List<UserNotificationState> findAllById(@NonNull Iterable<Long> ids) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends UserNotificationState> List<S> saveAll(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public boolean existsById(@NonNull Long id) { return states.containsKey(id); }
        @Override public long count() { return states.size(); }
        @Override public void deleteById(@NonNull Long id) { states.remove(id); }
        @Override public void delete(@NonNull UserNotificationState entity) { states.remove(entity.getId()); }
        @Override public void deleteAll(@NonNull Iterable<? extends UserNotificationState> entities) { }
        @Override public void deleteAllById(@NonNull Iterable<? extends Long> ids) { }
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
     * In-memory implementation of TodoTaskRepository for testing.
     */
    static class InMemoryTodoTaskRepository implements TodoTaskRepository {
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
                    .collect(Collectors.toList());
        }

        @Override
        public List<TodoTask> findByUserIdAndCategory(String userId, String category) {
            return tasks.values().stream()
                    .filter(t -> t.getUserId().equals(userId) && Objects.equals(t.getCategory(), category))
                    .collect(Collectors.toList());
        }

        @Override
        public List<TodoTask> findByUserIdAndParentId(String userId, String parentId) {
            return tasks.values().stream()
                    .filter(t -> t.getUserId().equals(userId) && Objects.equals(t.getParentId(), parentId))
                    .collect(Collectors.toList());
        }

        @Override
        public boolean existsByUserIdAndSourceNotificationId(String userId, Long sourceNotificationId) {
            return tasks.values().stream()
                    .anyMatch(t -> t.getUserId().equals(userId) && 
                            Objects.equals(t.getSourceNotificationId(), sourceNotificationId));
        }

        // Unused methods for testing
        @Override @NonNull public List<TodoTask> findAll() { return new ArrayList<>(tasks.values()); }
        @Override @NonNull public List<TodoTask> findAllById(@NonNull Iterable<String> ids) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends TodoTask> List<S> saveAll(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public boolean existsById(@NonNull String id) { return tasks.containsKey(id); }
        @Override public long count() { return tasks.size(); }
        @Override public void deleteById(@NonNull String id) { tasks.remove(id); }
        @Override public void delete(@NonNull TodoTask entity) { tasks.remove(entity.getId()); }
        @Override public void deleteAll(@NonNull Iterable<? extends TodoTask> entities) { }
        @Override public void deleteAllById(@NonNull Iterable<? extends String> ids) { }
        @Override public void deleteAll() { tasks.clear(); }
        @Override public void flush() { }
        @Override @NonNull public <S extends TodoTask> S saveAndFlush(@NonNull S entity) { return save(entity); }
        @Override @NonNull public <S extends TodoTask> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public void deleteAllInBatch(@NonNull Iterable<TodoTask> entities) { }
        @Override public void deleteAllByIdInBatch(@NonNull Iterable<String> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override @NonNull public TodoTask getOne(@NonNull String id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public TodoTask getById(@NonNull String id) { throw new UnsupportedOperationException(); }
        @Override @NonNull public TodoTask getReferenceById(@NonNull String id) { throw new UnsupportedOperationException(); }
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
    static class InMemoryUserRepository implements UserRepository {
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
}


/**
 * Property-based tests for NotificationService read state management.
 * Uses in-memory repositories for testing.
 * 
 * Validates: Requirements 4.2, 4.3, 4.4, 4.5
 */
class NotificationServiceReadStatePropertyTest {

    /**
     * Creates a NotificationService with in-memory repositories for testing.
     */
    private NotificationService createTestService() {
        return new NotificationService(
                new NotificationServicePropertyTest.InMemoryNotificationRepository(),
                new NotificationServicePropertyTest.InMemoryUserNotificationStateRepository(),
                new NotificationServicePropertyTest.InMemoryTodoTaskRepository(),
                new NotificationServicePropertyTest.InMemoryUserRepository(),
                new RecurrenceService()
        );
    }

    /**
     * Feature: notification-service, Property 16: Read State Toggle Persistence
     * For any notification and user, marking as read then querying SHALL return read=true,
     * and marking as unread then querying SHALL return read=false, regardless of session boundaries.
     * 
     * Validates: Requirements 4.2, 4.3, 4.4
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 16: Read State Toggle Persistence")
    void readStateTogglePersistence(
            @ForAll("validSubjects") String subject,
            @ForAll("validMessageBodies") String messageBody,
            @ForAll @IntRange(min = 1, max = 1000) long userId
    ) {
        NotificationService service = createTestService();

        // Create a global notification so any user can access it
        Notification notification = service.createNotification(
                subject, messageBody, true, null, null, null, null);

        // Initially should be unread
        boolean initialState = service.isRead(notification.getId(), userId);
        assertThat(initialState)
                .as("Initial state should be unread")
                .isFalse();

        // Mark as read
        service.markAsRead(notification.getId(), userId);
        boolean afterMarkRead = service.isRead(notification.getId(), userId);
        assertThat(afterMarkRead)
                .as("After marking as read, state should be read")
                .isTrue();

        // Mark as unread
        service.markAsUnread(notification.getId(), userId);
        boolean afterMarkUnread = service.isRead(notification.getId(), userId);
        assertThat(afterMarkUnread)
                .as("After marking as unread, state should be unread")
                .isFalse();

        // Mark as read again to verify toggle works both ways
        service.markAsRead(notification.getId(), userId);
        boolean afterSecondMarkRead = service.isRead(notification.getId(), userId);
        assertThat(afterSecondMarkRead)
                .as("After second mark as read, state should be read")
                .isTrue();
    }

    /**
     * Feature: notification-service, Property 17: Default Unread State
     * For any newly created notification and any targeted user, the initial read state
     * SHALL be false (unread).
     * 
     * Validates: Requirements 4.5
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 17: Default Unread State")
    void defaultUnreadState(
            @ForAll("validSubjects") String subject,
            @ForAll("validMessageBodies") String messageBody,
            @ForAll @IntRange(min = 1, max = 1000) long userId
    ) {
        NotificationService service = createTestService();

        // Create a global notification
        Notification notification = service.createNotification(
                subject, messageBody, true, null, null, null, null);

        // Access the notification to trigger state creation
        service.getNotificationById(notification.getId(), userId);

        // Verify initial state is unread
        boolean isRead = service.isRead(notification.getId(), userId);
        assertThat(isRead)
                .as("Newly created notification should default to unread state")
                .isFalse();
    }

    /**
     * Provides valid notification subjects (1-255 characters).
     */
    @Provide
    Arbitrary<String> validSubjects() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(255)
                .alpha();
    }

    /**
     * Provides valid message bodies (non-empty strings).
     */
    @Provide
    Arbitrary<String> validMessageBodies() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(1000)
                .alpha();
    }
}


/**
 * Property-based tests for NotificationService TODO integration functionality.
 * Uses in-memory repositories for testing.
 * 
 * Validates: Requirements 6.2, 6.3, 6.4, 6.5, 6.6
 */
class NotificationServiceTodoIntegrationPropertyTest {

    /**
     * Creates a NotificationService with in-memory repositories for testing.
     */
    private NotificationService createTestService() {
        return new NotificationService(
                new NotificationServicePropertyTest.InMemoryNotificationRepository(),
                new NotificationServicePropertyTest.InMemoryUserNotificationStateRepository(),
                new NotificationServicePropertyTest.InMemoryTodoTaskRepository(),
                new NotificationServicePropertyTest.InMemoryUserRepository(),
                new RecurrenceService()
        );
    }

    /**
     * Feature: notification-service, Property 21: Action Item to TODO Creation
     * For any notification with an action item that is actioned by a user, a TodoTask
     * SHALL be created for that user with the action item's description and category.
     * 
     * Validates: Requirements 6.2
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 21: Action Item to TODO Creation")
    void actionItemToTodoCreation(
            @ForAll("validSubjects") String subject,
            @ForAll("validMessageBodies") String messageBody,
            @ForAll("validDescriptions") String actionDescription,
            @ForAll("validCategories") String actionCategory,
            @ForAll @IntRange(min = 1, max = 1000) long userId
    ) {
        NotificationService service = createTestService();

        // Create notification with action item
        com.dylanjohnpratt.paradise.be.model.ActionItem actionItem = 
                new com.dylanjohnpratt.paradise.be.model.ActionItem(actionDescription, actionCategory);
        
        Notification notification = service.createNotification(
                subject, messageBody, true, null, null, null, actionItem);

        // Convert action item to TODO
        String userIdString = String.valueOf(userId);
        TodoTask task = service.convertActionItemToTodo(notification.getId(), userId, userIdString);

        // Verify task was created with correct properties
        assertThat(task).isNotNull();
        assertThat(task.getDescription())
                .as("Task description should match action item description")
                .isEqualTo(actionDescription);
        assertThat(task.getCategory())
                .as("Task category should match action item category")
                .isEqualTo(actionCategory);
        assertThat(task.getUserId())
                .as("Task should belong to the user who actioned it")
                .isEqualTo(userIdString);
        assertThat(task.isCompleted())
                .as("Task should not be completed initially")
                .isFalse();
    }

    /**
     * Feature: notification-service, Property 22: TODO Notification Provenance
     * For any TodoTask created from a notification action, the task SHALL have
     * createdFromNotification=true AND sourceNotificationId set to the originating notification's ID.
     * 
     * Validates: Requirements 6.3, 6.4, 7.4
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 22: TODO Notification Provenance")
    void todoNotificationProvenance(
            @ForAll("validSubjects") String subject,
            @ForAll("validMessageBodies") String messageBody,
            @ForAll("validDescriptions") String actionDescription,
            @ForAll("validCategories") String actionCategory,
            @ForAll @IntRange(min = 1, max = 1000) long userId
    ) {
        NotificationService service = createTestService();

        // Create notification with action item
        com.dylanjohnpratt.paradise.be.model.ActionItem actionItem = 
                new com.dylanjohnpratt.paradise.be.model.ActionItem(actionDescription, actionCategory);
        
        Notification notification = service.createNotification(
                subject, messageBody, true, null, null, null, actionItem);

        // Convert action item to TODO
        String userIdString = String.valueOf(userId);
        TodoTask task = service.convertActionItemToTodo(notification.getId(), userId, userIdString);

        // Verify provenance fields
        assertThat(task.isCreatedFromNotification())
                .as("Task should have createdFromNotification=true")
                .isTrue();
        assertThat(task.getSourceNotificationId())
                .as("Task should have sourceNotificationId set to notification ID")
                .isEqualTo(notification.getId());
    }

    /**
     * Feature: notification-service, Property 23: Expired Notification Action Prevention
     * For any expired notification with an action item, attempting to convert to TODO
     * SHALL be rejected.
     * 
     * Validates: Requirements 6.5
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 23: Expired Notification Action Prevention")
    void expiredNotificationActionPrevention(
            @ForAll("validSubjects") String subject,
            @ForAll("validMessageBodies") String messageBody,
            @ForAll("validDescriptions") String actionDescription,
            @ForAll("validCategories") String actionCategory,
            @ForAll @IntRange(min = 1, max = 1000) long userId
    ) {
        NotificationService service = createTestService();

        // Create notification with action item that is already expired
        com.dylanjohnpratt.paradise.be.model.ActionItem actionItem = 
                new com.dylanjohnpratt.paradise.be.model.ActionItem(actionDescription, actionCategory);
        
        LocalDateTime expiredTime = LocalDateTime.now().minusDays(1);
        Notification notification = service.createNotification(
                subject, messageBody, true, null, expiredTime, null, actionItem);

        // Attempt to convert action item to TODO should fail
        String userIdString = String.valueOf(userId);
        assertThatThrownBy(() -> service.convertActionItemToTodo(notification.getId(), userId, userIdString))
                .as("Converting expired notification to TODO should throw exception")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");
    }

    /**
     * Feature: notification-service, Property 24: Duplicate Action Prevention
     * For any notification that a user has already actioned, attempting to action again
     * SHALL be rejected without creating a duplicate TodoTask.
     * 
     * Validates: Requirements 6.6
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 24: Duplicate Action Prevention")
    void duplicateActionPrevention(
            @ForAll("validSubjects") String subject,
            @ForAll("validMessageBodies") String messageBody,
            @ForAll("validDescriptions") String actionDescription,
            @ForAll("validCategories") String actionCategory,
            @ForAll @IntRange(min = 1, max = 1000) long userId
    ) {
        NotificationService service = createTestService();

        // Create notification with action item
        com.dylanjohnpratt.paradise.be.model.ActionItem actionItem = 
                new com.dylanjohnpratt.paradise.be.model.ActionItem(actionDescription, actionCategory);
        
        Notification notification = service.createNotification(
                subject, messageBody, true, null, null, null, actionItem);

        // First action should succeed
        String userIdString = String.valueOf(userId);
        TodoTask firstTask = service.convertActionItemToTodo(notification.getId(), userId, userIdString);
        assertThat(firstTask).isNotNull();

        // Second action should fail
        assertThatThrownBy(() -> service.convertActionItemToTodo(notification.getId(), userId, userIdString))
                .as("Duplicate action should throw exception")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already created");
    }

    /**
     * Provides valid notification subjects (1-255 characters).
     */
    @Provide
    Arbitrary<String> validSubjects() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(255)
                .alpha();
    }

    /**
     * Provides valid message bodies (non-empty strings).
     */
    @Provide
    Arbitrary<String> validMessageBodies() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(1000)
                .alpha();
    }

    /**
     * Provides valid action item descriptions (non-empty strings).
     */
    @Provide
    Arbitrary<String> validDescriptions() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(500)
                .alpha();
    }

    /**
     * Provides valid action item categories (non-empty strings).
     */
    @Provide
    Arbitrary<String> validCategories() {
        return Arbitraries.strings()
                .ofMinLength(1)
                .ofMaxLength(100)
                .alpha();
    }
}
