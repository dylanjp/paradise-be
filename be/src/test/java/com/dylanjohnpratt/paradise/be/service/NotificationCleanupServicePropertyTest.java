package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.model.Notification;
import com.dylanjohnpratt.paradise.be.model.UserNotificationState;
import com.dylanjohnpratt.paradise.be.repository.NotificationRepository;
import com.dylanjohnpratt.paradise.be.repository.UserNotificationStateRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for NotificationCleanupService.
 * Uses in-memory repositories for testing.
 * 
 * Validates: Requirements 5.6
 */
class NotificationCleanupServicePropertyTest {

    /**
     * Feature: notification-service, Property 26: Expired Notification Cleanup
     * For any notification that has been expired for longer than the configured retention period,
     * the cleanup service SHALL permanently delete the notification and all associated
     * UserNotificationState records.
     * 
     * Validates: Requirements 5.6
     */
    @Property(tries = 100)
    @Label("Feature: notification-service, Property 26: Expired Notification Cleanup")
    void expiredNotificationCleanup(
            @ForAll @IntRange(min = 1, max = 100) int retentionDays,
            @ForAll @IntRange(min = 1, max = 50) int daysExpiredBeyondRetention,
            @ForAll @IntRange(min = 1, max = 5) int numberOfExpiredNotifications,
            @ForAll @IntRange(min = 0, max = 3) int numberOfNonExpiredNotifications,
            @ForAll @IntRange(min = 1, max = 5) int statesPerNotification
    ) {
        InMemoryNotificationRepository notificationRepo = new InMemoryNotificationRepository();
        InMemoryUserNotificationStateRepository stateRepo = new InMemoryUserNotificationStateRepository();
        
        NotificationCleanupService cleanupService = new NotificationCleanupService(notificationRepo, stateRepo);

        LocalDateTime now = LocalDateTime.now();
        
        // Create notifications that should be cleaned up (expired beyond retention)
        List<Long> expiredNotificationIds = new ArrayList<>();
        for (int i = 0; i < numberOfExpiredNotifications; i++) {
            LocalDateTime expiresAt = now.minusDays(retentionDays + daysExpiredBeyondRetention + i);
            Notification notification = createNotification("Expired " + i, expiresAt);
            notification = notificationRepo.save(notification);
            expiredNotificationIds.add(notification.getId());
            
            // Create associated user notification states
            for (int j = 0; j < statesPerNotification; j++) {
                UserNotificationState state = new UserNotificationState(notification.getId(), (long) (i * 100 + j));
                stateRepo.save(state);
            }
        }

        // Create notifications that should NOT be cleaned up
        List<Long> nonExpiredNotificationIds = new ArrayList<>();
        for (int i = 0; i < numberOfNonExpiredNotifications; i++) {
            // Some with no expiration, some with future expiration, some recently expired
            Notification notification;
            if (i % 3 == 0) {
                // No expiration
                notification = createNotification("NonExpired NoExpiry " + i, null);
            } else if (i % 3 == 1) {
                // Future expiration
                notification = createNotification("NonExpired Future " + i, now.plusDays(30));
            } else {
                // Recently expired (within retention period)
                notification = createNotification("NonExpired Recent " + i, now.minusDays(retentionDays / 2));
            }
            notification = notificationRepo.save(notification);
            nonExpiredNotificationIds.add(notification.getId());
            
            // Create associated user notification states
            for (int j = 0; j < statesPerNotification; j++) {
                UserNotificationState state = new UserNotificationState(notification.getId(), (long) (1000 + i * 100 + j));
                stateRepo.save(state);
            }
        }

        // Record initial counts
        int initialNotificationCount = (int) notificationRepo.count();
        int initialStateCount = (int) stateRepo.count();

        // Execute cleanup
        int deletedCount = cleanupService.purgeExpiredNotifications(retentionDays);

        // Verify correct number of notifications were deleted
        assertThat(deletedCount)
                .as("Should delete exactly the expired notifications past retention")
                .isEqualTo(numberOfExpiredNotifications);

        // Verify expired notifications are gone
        for (Long expiredId : expiredNotificationIds) {
            assertThat(notificationRepo.findById(expiredId))
                    .as("Expired notification %d should be deleted", expiredId)
                    .isEmpty();
        }

        // Verify non-expired notifications still exist
        for (Long nonExpiredId : nonExpiredNotificationIds) {
            assertThat(notificationRepo.findById(nonExpiredId))
                    .as("Non-expired notification %d should still exist", nonExpiredId)
                    .isPresent();
        }

        // Verify associated states for expired notifications are deleted
        for (Long expiredId : expiredNotificationIds) {
            List<UserNotificationState> states = stateRepo.findByNotificationId(expiredId);
            assertThat(states)
                    .as("States for expired notification %d should be deleted", expiredId)
                    .isEmpty();
        }

        // Verify states for non-expired notifications still exist
        for (Long nonExpiredId : nonExpiredNotificationIds) {
            List<UserNotificationState> states = stateRepo.findByNotificationId(nonExpiredId);
            assertThat(states)
                    .as("States for non-expired notification %d should still exist", nonExpiredId)
                    .hasSize(statesPerNotification);
        }

        // Verify final counts
        int expectedRemainingNotifications = numberOfNonExpiredNotifications;
        int expectedRemainingStates = numberOfNonExpiredNotifications * statesPerNotification;
        
        assertThat(notificationRepo.count())
                .as("Remaining notification count should match")
                .isEqualTo(expectedRemainingNotifications);
        
        assertThat(stateRepo.count())
                .as("Remaining state count should match")
                .isEqualTo(expectedRemainingStates);
    }

    private Notification createNotification(String subject, LocalDateTime expiresAt) {
        Notification notification = new Notification(subject, "Test message body", true);
        notification.setExpiresAt(expiresAt);
        return notification;
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

        @Override @NonNull public List<Notification> findAll() { return new ArrayList<>(notifications.values()); }
        @Override @NonNull public List<Notification> findAllById(@NonNull Iterable<Long> ids) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends Notification> List<S> saveAll(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public boolean existsById(@NonNull Long id) { return notifications.containsKey(id); }
        @Override public long count() { return notifications.size(); }
        @Override public void deleteById(@NonNull Long id) { notifications.remove(id); }
        @Override public void delete(@NonNull Notification entity) { notifications.remove(entity.getId()); }
        @Override public void deleteAll(@NonNull Iterable<? extends Notification> entities) { }
        @Override public void deleteAllById(@NonNull Iterable<? extends Long> ids) { 
            for (Long id : ids) {
                notifications.remove(id);
            }
        }
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
}
