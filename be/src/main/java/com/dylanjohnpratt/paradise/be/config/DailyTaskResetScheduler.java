package com.dylanjohnpratt.paradise.be.config;

import com.dylanjohnpratt.paradise.be.model.DailyTask;
import com.dylanjohnpratt.paradise.be.repository.DailyTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scheduler component that resets all daily tasks to incomplete at midnight.
 * This preserves completion records while resetting the current completion status.
 */
@Component
public class DailyTaskResetScheduler {

    private static final Logger logger = LoggerFactory.getLogger(DailyTaskResetScheduler.class);

    private final DailyTaskRepository dailyTaskRepository;

    public DailyTaskResetScheduler(DailyTaskRepository dailyTaskRepository) {
        this.dailyTaskRepository = dailyTaskRepository;
    }

    /**
     * Resets all daily tasks to incomplete status at midnight.
     * This method runs at 00:00:00 every day.
     * Completion records are NOT deleted - only the completed flag is reset.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void resetDailyTasks() {
        logger.info("Starting midnight reset of daily tasks");
        
        List<DailyTask> allTasks = dailyTaskRepository.findAll();
        allTasks.forEach(task -> task.setCompleted(false));
        dailyTaskRepository.saveAll(allTasks);
        
        logger.info("Completed midnight reset of {} daily tasks", allTasks.size());
    }
}
