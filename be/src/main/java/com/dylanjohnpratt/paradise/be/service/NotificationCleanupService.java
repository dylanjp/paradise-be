package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.repository.NotificationRepository;
import com.dylanjohnpratt.paradise.be.repository.UserNotificationStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for cleaning up expired notifications that have exceeded the retention period.
 * Runs as a scheduled job to permanently delete old notifications and their associated user states.
 * 
 * Requirements: 5.3, 5.6
 */
@Service
public class NotificationCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationCleanupService.class);

    private final NotificationRepository notificationRepository;
    private final UserNotificationStateRepository userNotificationStateRepository;

    @Value("${notification.cleanup.retention-days:90}")
    private int retentionDays;

    public NotificationCleanupService(NotificationRepository notificationRepository,
                                      UserNotificationStateRepository userNotificationStateRepository) {
        this.notificationRepository = notificationRepository;
        this.userNotificationStateRepository = userNotificationStateRepository;
    }

    /**
     * Scheduled cleanup job that runs daily at 2 AM.
     * Purges notifications that have been expired for longer than the retention period.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupExpiredNotifications() {
        logger.info("Starting scheduled notification cleanup with retention period of {} days", retentionDays);
        int deletedCount = purgeExpiredNotifications(retentionDays);
        logger.info("Notification cleanup completed. Deleted {} notifications", deletedCount);
    }

    /**
     * Hard deletes notifications that have been expired for longer than the specified retention period.
     * Also deletes all associated UserNotificationState records.
     * 
     * @param retentionDays the number of days after expiration before permanent deletion
     * @return the number of notifications deleted
     * 
     * Requirements: 5.3, 5.6
     */
    @Transactional
    public int purgeExpiredNotifications(int retentionDays) {
        LocalDateTime retentionCutoff = LocalDateTime.now().minusDays(retentionDays);
        
        // Find all notification IDs eligible for cleanup
        List<Long> notificationIds = notificationRepository.findExpiredPastRetention(retentionCutoff);
        
        if (notificationIds.isEmpty()) {
            logger.debug("No notifications eligible for cleanup");
            return 0;
        }

        logger.debug("Found {} notifications eligible for cleanup", notificationIds.size());

        // Delete associated UserNotificationState records first (Requirement 5.6)
        userNotificationStateRepository.deleteByNotificationIdIn(notificationIds);
        logger.debug("Deleted user notification states for {} notifications", notificationIds.size());

        // Delete the notifications
        notificationRepository.deleteAllById(notificationIds);
        logger.debug("Deleted {} notifications", notificationIds.size());

        return notificationIds.size();
    }

    /**
     * Gets the count of notifications eligible for cleanup.
     * Useful for monitoring and reporting before actual cleanup.
     * 
     * @param retentionDays the number of days after expiration before permanent deletion
     * @return the count of notifications that would be deleted
     */
    public int getCleanupCandidateCount(int retentionDays) {
        LocalDateTime retentionCutoff = LocalDateTime.now().minusDays(retentionDays);
        return notificationRepository.countExpiredPastRetention(retentionCutoff);
    }

    /**
     * Gets the configured retention period in days.
     * 
     * @return the retention period in days
     */
    public int getRetentionDays() {
        return retentionDays;
    }

    /**
     * Sets the retention period in days.
     * Primarily used for testing purposes.
     * 
     * @param retentionDays the retention period in days
     */
    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }
}
