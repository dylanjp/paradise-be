package com.dylanjohnpratt.paradise.be.repository;

import com.dylanjohnpratt.paradise.be.model.DailyTaskCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DailyTaskCompletion entity persistence operations.
 */
@Repository
public interface DailyTaskCompletionRepository extends JpaRepository<DailyTaskCompletion, String> {
    
    /**
     * Finds all completion records for a daily task, ordered by date descending.
     *
     * @param dailyTaskId the daily task ID
     * @return list of completion records in descending date order
     */
    List<DailyTaskCompletion> findByDailyTaskIdOrderByCompletionDateDesc(String dailyTaskId);
    
    /**
     * Finds a completion record for a specific task and date.
     *
     * @param dailyTaskId the daily task ID
     * @param completionDate the completion date
     * @return optional containing the completion record if found
     */
    Optional<DailyTaskCompletion> findByDailyTaskIdAndCompletionDate(String dailyTaskId, LocalDate completionDate);
    
    /**
     * Deletes a completion record for a specific task and date.
     *
     * @param dailyTaskId the daily task ID
     * @param completionDate the completion date
     */
    void deleteByDailyTaskIdAndCompletionDate(String dailyTaskId, LocalDate completionDate);
    
    /**
     * Deletes all completion records for a daily task.
     *
     * @param dailyTaskId the daily task ID
     */
    void deleteByDailyTaskId(String dailyTaskId);
    
    /**
     * Finds all completion records for a collection of daily task IDs.
     *
     * @param dailyTaskIds the collection of daily task IDs
     * @return list of completion records for the specified tasks
     */
    List<DailyTaskCompletion> findByDailyTaskIdIn(Collection<String> dailyTaskIds);
    
    /**
     * Finds all completion records for a collection of daily task IDs within a date range.
     *
     * @param dailyTaskIds the collection of daily task IDs
     * @param startDate the start date (inclusive)
     * @param endDate the end date (inclusive)
     * @return list of completion records for the specified tasks within the date range
     */
    List<DailyTaskCompletion> findByDailyTaskIdInAndCompletionDateBetween(
            Collection<String> dailyTaskIds, 
            LocalDate startDate, 
            LocalDate endDate);
}
