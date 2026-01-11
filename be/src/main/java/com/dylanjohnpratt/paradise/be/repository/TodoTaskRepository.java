package com.dylanjohnpratt.paradise.be.repository;

import com.dylanjohnpratt.paradise.be.model.TodoTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for TodoTask entity persistence operations.
 */
@Repository
public interface TodoTaskRepository extends JpaRepository<TodoTask, String> {
    
    /**
     * Finds all TODO tasks for a specific user.
     *
     * @param userId the user ID
     * @return list of TODO tasks for the user
     */
    List<TodoTask> findByUserId(String userId);
    
    /**
     * Finds all TODO tasks for a specific user and category.
     *
     * @param userId the user ID
     * @param category the category
     * @return list of TODO tasks for the user in the category
     */
    List<TodoTask> findByUserIdAndCategory(String userId, String category);
}
