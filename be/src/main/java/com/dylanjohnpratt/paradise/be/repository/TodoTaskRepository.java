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
    
    /**
     * Finds all TODO tasks for a specific user that have the given parentId.
     * Used for finding child tasks when a parent task is deleted (orphan prevention).
     *
     * @param userId the user ID
     * @param parentId the parent task ID
     * @return list of TODO tasks that are children of the specified parent
     */
    List<TodoTask> findByUserIdAndParentId(String userId, String parentId);
    
    /**
     * Checks if a TODO task exists for a specific user that was created from a specific notification.
     * Used to prevent duplicate TODO creation from the same notification.
     *
     * @param userId the user ID
     * @param sourceNotificationId the source notification ID
     * @return true if a task exists from this notification for this user
     */
    boolean existsByUserIdAndSourceNotificationId(String userId, Long sourceNotificationId);
}
