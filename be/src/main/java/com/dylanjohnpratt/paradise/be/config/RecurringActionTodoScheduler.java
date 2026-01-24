package com.dylanjohnpratt.paradise.be.config;

import com.dylanjohnpratt.paradise.be.service.ProcessingResult;
import com.dylanjohnpratt.paradise.be.service.RecurringActionTodoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduler component that processes recurring notifications with action items
 * and creates TODO tasks for targeted users.
 * 
 * Runs daily at a configurable time (default: 01:00 AM) to process all due
 * recurring notifications and create corresponding TODO tasks.
 * 
 * Requirements: 4.1, 4.2, 4.3, 4.4
 */
@Component
public class RecurringActionTodoScheduler {

    private static final Logger logger = LoggerFactory.getLogger(RecurringActionTodoScheduler.class);

    private final RecurringActionTodoService recurringActionTodoService;

    public RecurringActionTodoScheduler(RecurringActionTodoService recurringActionTodoService) {
        this.recurringActionTodoService = recurringActionTodoService;
    }

    /**
     * Scheduled job that runs daily to process recurring notifications.
     * Default schedule: 01:00 AM daily.
     * 
     * Logs start time, processes all due recurring notifications with action items,
     * creates TODO tasks for targeted users, and logs completion with result counts.
     */
    @Scheduled(cron = "${recurring.action.todo.cron:0 0 1 * * *}")
    @Transactional
    public void processRecurringNotifications() {
        LocalDateTime startTime = LocalDateTime.now();
        logger.info("Starting recurring action TODO processing at {}", startTime);

        try {
            ProcessingResult result = recurringActionTodoService.processRecurringNotifications();

            LocalDateTime endTime = LocalDateTime.now();
            logger.info("Completed recurring action TODO processing at {}. " +
                    "Notifications processed: {}, TODOs created: {}, Errors: {}",
                    endTime, result.notificationsProcessed(), result.todosCreated(), result.errors());

            if (result.errors() > 0) {
                logger.warn("Processing completed with {} errors. Error messages: {}",
                        result.errors(), result.errorMessages());
            }

        } catch (Exception e) {
            logger.error("Fatal error during recurring action TODO processing: {}", e.getMessage(), e);
        }
    }
}
