package com.dylanjohnpratt.paradise.be.repository;

import com.dylanjohnpratt.paradise.be.model.ActionItem;
import com.dylanjohnpratt.paradise.be.model.Notification;
import com.dylanjohnpratt.paradise.be.model.RecurrenceRule;
import net.jqwik.api.*;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for NotificationRepository query methods.
 * Uses in-memory repository for testing.
 * 
 * Validates: Requirements 2.1
 */
class NotificationRepositoryPropertyTest {

    /**
     * Feature: recurring-action-todo, Property 4: Query Filter Correctness
     * For any set of notifications in the database, the findActiveRecurringNotificationsWithActionItems
     * query SHALL return only notifications where:
     * (a) deleted is false
     * (b) recurrenceRuleJson is not null
     * (c) actionItem.description is not null and not blank
     * (d) expiresAt is null or in the future
     * 
     * Validates: Requirements 2.1
     */
    @Property(tries = 100)
    @Label("Feature: recurring-action-todo, Property 4: Query Filter Correctness")
    void queryFilterCorrectness(
            @ForAll("notificationSets") List<NotificationTestData> testDataList
    ) {
        InMemoryNotificationRepository repository = new InMemoryNotificationRepository();
        LocalDateTime now = LocalDateTime.now();

        // Save all notifications to repository
        for (NotificationTestData testData : testDataList) {
            Notification notification = createNotification(testData);
            repository.save(notification);
        }

        // Execute the query
        List<Notification> results = repository.findActiveRecurringNotificationsWithActionItems(now);

        // Verify all returned notifications meet the criteria
        for (Notification result : results) {
            assertThat(result.isDeleted())
                    .as("Returned notification should not be deleted")
                    .isFalse();
            
            assertThat(result.getRecurrenceRuleJson())
                    .as("Returned notification should have recurrence rule")
                    .isNotNull();
            
            assertThat(result.getActionItem())
                    .as("Returned notification should have action item")
                    .isNotNull();
            
            assertThat(result.getActionItem().getDescription())
                    .as("Returned notification should have non-blank action item description")
                    .isNotNull()
                    .isNotBlank();
            
            assertThat(result.getExpiresAt() == null || result.getExpiresAt().isAfter(now))
                    .as("Returned notification should not be expired")
                    .isTrue();
        }

        // Verify all notifications that meet criteria are returned
        long expectedCount = testDataList.stream()
                .filter(td -> !td.deleted)
                .filter(td -> td.hasRecurrenceRule)
                .filter(td -> td.hasValidActionItem)
                .filter(td -> td.expiresAt == null || td.expiresAt.isAfter(now))
                .count();

        assertThat(results.size())
                .as("Query should return all notifications meeting criteria")
                .isEqualTo((int) expectedCount);
    }

    /**
     * Test data class for generating notifications with various attribute combinations.
     */
    static class NotificationTestData {
        final String subject;
        final String messageBody;
        final boolean deleted;
        final boolean hasRecurrenceRule;
        final boolean hasValidActionItem;
        final LocalDateTime expiresAt;

        NotificationTestData(String subject, String messageBody, boolean deleted,
                            boolean hasRecurrenceRule, boolean hasValidActionItem,
                            LocalDateTime expiresAt) {
            this.subject = subject;
            this.messageBody = messageBody;
            this.deleted = deleted;
            this.hasRecurrenceRule = hasRecurrenceRule;
            this.hasValidActionItem = hasValidActionItem;
            this.expiresAt = expiresAt;
        }
    }

    private Notification createNotification(NotificationTestData testData) {
        Notification notification = new Notification(testData.subject, testData.messageBody, true);
        notification.setDeleted(testData.deleted);
        notification.setExpiresAt(testData.expiresAt);

        if (testData.hasRecurrenceRule) {
            RecurrenceRule rule = new RecurrenceRule(RecurrenceRule.RecurrenceType.DAILY, null, null);
            notification.setRecurrenceRule(rule);
        }

        if (testData.hasValidActionItem) {
            ActionItem actionItem = new ActionItem("Test action description", "TestCategory");
            notification.setActionItem(actionItem);
        } else {
            // Set empty or null action item
            notification.setActionItem(null);
        }

        return notification;
    }

    @Provide
    Arbitrary<List<NotificationTestData>> notificationSets() {
        return notificationTestData().list().ofMinSize(1).ofMaxSize(20);
    }

    @Provide
    Arbitrary<NotificationTestData> notificationTestData() {
        Arbitrary<String> subjects = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50);
        Arbitrary<String> messageBodies = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100);
        Arbitrary<Boolean> deletedFlags = Arbitraries.of(true, false);
        Arbitrary<Boolean> hasRecurrenceFlags = Arbitraries.of(true, false);
        Arbitrary<Boolean> hasActionItemFlags = Arbitraries.of(true, false);
        Arbitrary<LocalDateTime> expiresAtValues = Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.integers().between(-30, 30)
                        .map(days -> LocalDateTime.now().plusDays(days))
        );

        return Combinators.combine(subjects, messageBodies, deletedFlags, 
                hasRecurrenceFlags, hasActionItemFlags, expiresAtValues)
                .as(NotificationTestData::new);
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
}
