package com.dylanjohnpratt.paradise.be.repository;

import com.dylanjohnpratt.paradise.be.model.ProcessedOccurrence;
import net.jqwik.api.*;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for OccurrenceTrackerRepository.
 * Uses in-memory repository for testing.
 * 
 * Validates: Requirements 1.1, 1.2, 1.3
 */
class OccurrenceTrackerRepositoryPropertyTest {

    /**
     * Feature: recurring-action-todo, Property 1: Occurrence Tracking Round-Trip
     * For any notification ID and occurrence date, if the occurrence is marked as processed,
     * then checking if that occurrence was processed SHALL return true. Conversely, for any
     * notification ID and occurrence date that has not been marked as processed, checking
     * SHALL return false.
     * 
     * Validates: Requirements 1.1, 1.3
     */
    @Property(tries = 100)
    @Label("Feature: recurring-action-todo, Property 1: Occurrence Tracking Round-Trip")
    void occurrenceTrackingRoundTrip(
            @ForAll("notificationIds") long notificationId,
            @ForAll("occurrenceDates") LocalDate occurrenceDate,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 0, max = 100) int todosCreated
    ) {
        InMemoryOccurrenceTrackerRepository repository = new InMemoryOccurrenceTrackerRepository();

        // Initially should not exist
        boolean existsBefore = repository.existsByNotificationIdAndOccurrenceDate(notificationId, occurrenceDate);
        assertThat(existsBefore)
                .as("Occurrence should not exist before being saved")
                .isFalse();

        // Save the occurrence
        ProcessedOccurrence occurrence = new ProcessedOccurrence(notificationId, occurrenceDate, todosCreated);
        repository.save(occurrence);

        // Now should exist
        boolean existsAfter = repository.existsByNotificationIdAndOccurrenceDate(notificationId, occurrenceDate);
        assertThat(existsAfter)
                .as("Occurrence should exist after being saved")
                .isTrue();

        // Should be retrievable
        Optional<ProcessedOccurrence> retrieved = repository.findByNotificationIdAndOccurrenceDate(notificationId, occurrenceDate);
        assertThat(retrieved)
                .as("Occurrence should be retrievable after being saved")
                .isPresent();
        assertThat(retrieved.get().getNotificationId())
                .as("Retrieved occurrence should have correct notification ID")
                .isEqualTo(notificationId);
        assertThat(retrieved.get().getOccurrenceDate())
                .as("Retrieved occurrence should have correct occurrence date")
                .isEqualTo(occurrenceDate);
        assertThat(retrieved.get().getTodosCreated())
                .as("Retrieved occurrence should have correct todos created count")
                .isEqualTo(todosCreated);
    }

    /**
     * Feature: recurring-action-todo, Property 2: Occurrence Uniqueness
     * For any notification ID and occurrence date combination, the system SHALL store
     * at most one ProcessedOccurrence record. Attempting to mark the same occurrence
     * as processed multiple times SHALL not create duplicate records.
     * 
     * Validates: Requirements 1.2
     */
    @Property(tries = 100)
    @Label("Feature: recurring-action-todo, Property 2: Occurrence Uniqueness")
    void occurrenceUniqueness(
            @ForAll("notificationIds") long notificationId,
            @ForAll("occurrenceDates") LocalDate occurrenceDate,
            @ForAll @net.jqwik.api.constraints.IntRange(min = 1, max = 5) int saveAttempts
    ) {
        InMemoryOccurrenceTrackerRepository repository = new InMemoryOccurrenceTrackerRepository();

        // Save the same occurrence multiple times
        for (int i = 0; i < saveAttempts; i++) {
            ProcessedOccurrence occurrence = new ProcessedOccurrence(notificationId, occurrenceDate, i);
            repository.save(occurrence);
        }

        // Should only have one record for this notification/date combination
        List<ProcessedOccurrence> allForNotification = repository.findByNotificationId(notificationId);
        long countForDate = allForNotification.stream()
                .filter(o -> o.getOccurrenceDate().equals(occurrenceDate))
                .count();
        
        assertThat(countForDate)
                .as("Should have exactly one record for notification %d and date %s after %d save attempts",
                        notificationId, occurrenceDate, saveAttempts)
                .isEqualTo(1);
    }

    @Provide
    Arbitrary<Long> notificationIds() {
        return Arbitraries.longs().between(1, 10000);
    }

    @Provide
    Arbitrary<LocalDate> occurrenceDates() {
        return Arbitraries.integers().between(-365, 365)
                .map(offset -> LocalDate.now().plusDays(offset));
    }

    /**
     * In-memory implementation of OccurrenceTrackerRepository for testing.
     */
    static class InMemoryOccurrenceTrackerRepository implements OccurrenceTrackerRepository {
        private final Map<Long, ProcessedOccurrence> occurrences = new HashMap<>();
        private final Map<String, Long> uniqueKeyToId = new HashMap<>();
        private long idCounter = 0;

        private String uniqueKey(Long notificationId, LocalDate occurrenceDate) {
            return notificationId + ":" + occurrenceDate;
        }

        @Override
        @NonNull
        public <S extends ProcessedOccurrence> S save(@NonNull S occurrence) {
            String key = uniqueKey(occurrence.getNotificationId(), occurrence.getOccurrenceDate());
            
            // Check if already exists - update instead of insert
            if (uniqueKeyToId.containsKey(key)) {
                Long existingId = uniqueKeyToId.get(key);
                occurrence.setId(existingId);
                occurrences.put(existingId, occurrence);
            } else {
                // New record
                if (occurrence.getId() == null) {
                    occurrence.setId(++idCounter);
                }
                occurrences.put(occurrence.getId(), occurrence);
                uniqueKeyToId.put(key, occurrence.getId());
            }
            return occurrence;
        }

        @Override
        public boolean existsByNotificationIdAndOccurrenceDate(Long notificationId, LocalDate occurrenceDate) {
            return uniqueKeyToId.containsKey(uniqueKey(notificationId, occurrenceDate));
        }

        @Override
        @NonNull
        public Optional<ProcessedOccurrence> findByNotificationIdAndOccurrenceDate(Long notificationId, LocalDate occurrenceDate) {
            String key = uniqueKey(notificationId, occurrenceDate);
            Long id = uniqueKeyToId.get(key);
            return requireNonNull(id != null ? Optional.ofNullable(occurrences.get(id)) : Optional.empty());
        }

        @Override
        @NonNull
        public List<ProcessedOccurrence> findByNotificationId(Long notificationId) {
            return occurrences.values().stream()
                    .filter(o -> o.getNotificationId().equals(notificationId))
                    .collect(Collectors.toList());
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
        @Override public void deleteAll(@NonNull Iterable<? extends ProcessedOccurrence> entities) { }
        @Override public void deleteAllById(@NonNull Iterable<? extends Long> ids) { }
        @Override public void deleteAll() { occurrences.clear(); uniqueKeyToId.clear(); }
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
}
