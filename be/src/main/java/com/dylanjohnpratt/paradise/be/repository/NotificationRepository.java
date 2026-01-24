package com.dylanjohnpratt.paradise.be.repository;

import com.dylanjohnpratt.paradise.be.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for Notification entity persistence operations.
 * Provides methods for querying notifications by user targeting, global status,
 * and expiration state.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Finds all global notifications that are not deleted.
     * Used for broadcasting notifications to all users.
     *
     * @return list of non-deleted global notifications
     */
    List<Notification> findByIsGlobalTrueAndDeletedFalse();

    /**
     * Finds all notifications targeting a specific user that are not deleted.
     * Uses the targetUserIds collection to check membership.
     *
     * @param userId the user ID to find notifications for
     * @return list of non-deleted notifications targeting the user
     */
    @Query("SELECT n FROM Notification n JOIN n.targetUserIds t WHERE t = :userId AND n.deleted = false")
    List<Notification> findByTargetUserIdAndNotDeleted(@Param("userId") Long userId);

    /**
     * Finds all notifications visible to a user (either global or specifically targeted).
     * Excludes deleted notifications.
     *
     * @param userId the user ID
     * @return list of all notifications the user can see
     */
    @Query("SELECT DISTINCT n FROM Notification n LEFT JOIN n.targetUserIds t " +
           "WHERE n.deleted = false AND (n.isGlobal = true OR t = :userId)")
    List<Notification> findAllForUser(@Param("userId") Long userId);

    /**
     * Finds all non-expired notifications visible to a user.
     * Includes notifications with no expiration date (expiresAt is null).
     *
     * @param userId the user ID
     * @param now the current timestamp for expiration comparison
     * @return list of non-expired notifications the user can see
     */
    @Query("SELECT DISTINCT n FROM Notification n LEFT JOIN n.targetUserIds t " +
           "WHERE n.deleted = false AND (n.isGlobal = true OR t = :userId) " +
           "AND (n.expiresAt IS NULL OR n.expiresAt > :now)")
    List<Notification> findNonExpiredForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    /**
     * Finds notifications that have been expired for longer than the retention period.
     * These are candidates for permanent deletion by the cleanup service.
     *
     * @param retentionCutoff the cutoff timestamp (now minus retention days)
     * @return list of notification IDs eligible for cleanup
     */
    @Query("SELECT n.id FROM Notification n WHERE n.expiresAt IS NOT NULL AND n.expiresAt < :retentionCutoff")
    List<Long> findExpiredPastRetention(@Param("retentionCutoff") LocalDateTime retentionCutoff);

    /**
     * Counts notifications that have been expired for longer than the retention period.
     * Used to report cleanup candidate count before purging.
     *
     * @param retentionCutoff the cutoff timestamp (now minus retention days)
     * @return count of notifications eligible for cleanup
     */
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.expiresAt IS NOT NULL AND n.expiresAt < :retentionCutoff")
    int countExpiredPastRetention(@Param("retentionCutoff") LocalDateTime retentionCutoff);

    /**
     * Finds all active recurring notifications that have action items.
     * Used by the RecurringActionTodoService to identify notifications that should
     * generate TODO tasks on their recurrence dates.
     * 
     * Filters for:
     * - Not deleted (deleted = false)
     * - Has recurrence rule (recurrenceRuleJson is not null)
     * - Has action item with description (actionItem.description is not null and not blank)
     * - Not expired (expiresAt is null or expiresAt > now)
     *
     * @param now the current timestamp for expiration comparison
     * @return list of active recurring notifications with action items
     */
    @Query("SELECT n FROM Notification n WHERE n.deleted = false " +
           "AND n.recurrenceRuleJson IS NOT NULL " +
           "AND n.actionItem.description IS NOT NULL " +
           "AND n.actionItem.description <> '' " +
           "AND (n.expiresAt IS NULL OR n.expiresAt > :now)")
    List<Notification> findActiveRecurringNotificationsWithActionItems(@Param("now") LocalDateTime now);
}
