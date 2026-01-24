package com.dylanjohnpratt.paradise.be.repository;

import com.dylanjohnpratt.paradise.be.model.ProcessedOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for ProcessedOccurrence entity persistence operations.
 * Provides methods for tracking which recurring notification occurrences have been processed.
 */
@Repository
public interface OccurrenceTrackerRepository extends JpaRepository<ProcessedOccurrence, Long> {

    /**
     * Checks if an occurrence has already been processed.
     *
     * @param notificationId the notification ID
     * @param occurrenceDate the occurrence date
     * @return true if a record exists for this notification and date
     */
    boolean existsByNotificationIdAndOccurrenceDate(Long notificationId, LocalDate occurrenceDate);

    /**
     * Finds a processed occurrence by notification ID and date.
     *
     * @param notificationId the notification ID
     * @param occurrenceDate the occurrence date
     * @return the processed occurrence if found
     */
    Optional<ProcessedOccurrence> findByNotificationIdAndOccurrenceDate(Long notificationId, LocalDate occurrenceDate);

    /**
     * Finds all processed occurrences for a notification.
     *
     * @param notificationId the notification ID
     * @return list of processed occurrences for the notification
     */
    List<ProcessedOccurrence> findByNotificationId(Long notificationId);
}
