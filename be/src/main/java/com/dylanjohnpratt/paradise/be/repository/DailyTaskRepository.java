package com.dylanjohnpratt.paradise.be.repository;

import com.dylanjohnpratt.paradise.be.model.DailyTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for DailyTask entity persistence operations.
 */
@Repository
public interface DailyTaskRepository extends JpaRepository<DailyTask, String> {
    
    /**
     * Finds all daily tasks for a specific user.
     *
     * @param userId the user ID
     * @return list of daily tasks for the user
     */
    List<DailyTask> findByUserId(String userId);
}
