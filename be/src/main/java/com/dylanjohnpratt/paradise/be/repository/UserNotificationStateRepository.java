package com.dylanjohnpratt.paradise.be.repository;

import com.dylanjohnpratt.paradise.be.model.UserNotificationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for UserNotificationState entity persistence operations.
 * Provides methods for managing per-user notification read/unread states.
 */
@Repository
public interface UserNotificationStateRepository extends JpaRepository<UserNotificationState, Long> {

    /**
     * Finds the notification state for a specific user and notification.
     * Returns at most one result due to unique constraint on (notificationId, userId).
     *
     * @param notificationId the notification ID
     * @param userId the user ID
     * @return Optional containing the state if found
     */
    Optional<UserNotificationState> findByNotificationIdAndUserId(Long notificationId, Long userId);

    /**
     * Finds all notification states for a specific user.
     * Used to get read/unread status for all notifications a user has interacted with.
     *
     * @param userId the user ID
     * @return list of notification states for the user
     */
    List<UserNotificationState> findByUserId(Long userId);

    /**
     * Finds all notification states for a specific notification.
     * Used to get all user states when processing a notification.
     *
     * @param notificationId the notification ID
     * @return list of user states for the notification
     */
    List<UserNotificationState> findByNotificationId(Long notificationId);

    /**
     * Checks if a state record exists for a user-notification pair.
     *
     * @param notificationId the notification ID
     * @param userId the user ID
     * @return true if a state record exists
     */
    boolean existsByNotificationIdAndUserId(Long notificationId, Long userId);

    /**
     * Deletes all notification states for a list of notification IDs.
     * Used by the cleanup service when purging expired notifications.
     *
     * @param notificationIds the list of notification IDs to delete states for
     */
    @Modifying
    @Query("DELETE FROM UserNotificationState s WHERE s.notificationId IN :notificationIds")
    void deleteByNotificationIdIn(@Param("notificationIds") List<Long> notificationIds);

    /**
     * Deletes all notification states for a single notification.
     * Used when a notification is permanently deleted.
     *
     * @param notificationId the notification ID
     */
    void deleteByNotificationId(Long notificationId);

    /**
     * Resets read state to unread for all users of a notification.
     * Sets read=false and readAt=null for all matching records.
     * Used when a recurring notification recurs to ensure users see it as new.
     *
     * @param notificationId the notification ID
     * @return number of records updated
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserNotificationState s SET s.read = false, s.readAt = null WHERE s.notificationId = :notificationId")
    int resetReadStateForNotification(@Param("notificationId") Long notificationId);
}
