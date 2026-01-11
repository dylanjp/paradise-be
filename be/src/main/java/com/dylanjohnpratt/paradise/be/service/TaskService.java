package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.dto.*;
import com.dylanjohnpratt.paradise.be.exception.TaskNotFoundException;
import com.dylanjohnpratt.paradise.be.model.DailyTask;
import com.dylanjohnpratt.paradise.be.model.DailyTaskCompletion;
import com.dylanjohnpratt.paradise.be.model.TodoTask;
import com.dylanjohnpratt.paradise.be.repository.DailyTaskCompletionRepository;
import com.dylanjohnpratt.paradise.be.repository.DailyTaskRepository;
import com.dylanjohnpratt.paradise.be.repository.TodoTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service containing all business logic for task management operations.
 * Uses database repositories for persistent storage of tasks per user.
 */
@Service
public class TaskService {

    private final TodoTaskRepository todoTaskRepository;
    private final DailyTaskRepository dailyTaskRepository;
    private final DailyTaskCompletionRepository dailyTaskCompletionRepository;

    public TaskService(TodoTaskRepository todoTaskRepository, DailyTaskRepository dailyTaskRepository,
                       DailyTaskCompletionRepository dailyTaskCompletionRepository) {
        this.todoTaskRepository = todoTaskRepository;
        this.dailyTaskRepository = dailyTaskRepository;
        this.dailyTaskCompletionRepository = dailyTaskCompletionRepository;
    }

    /**
     * Retrieves all tasks (both TODO and Daily) for a specific user.
     * Returns empty collections if the user has no tasks.
     *
     * @param userId the unique identifier of the user
     * @return UserTasksResponse containing TODO tasks grouped by category and Daily tasks as a list
     */
    public UserTasksResponse getAllTasksForUser(String userId) {
        List<TodoTask> userTodoTasks = todoTaskRepository.findByUserId(userId);
        List<DailyTask> userDailyTasks = dailyTaskRepository.findByUserId(userId);
        
        // Group TODO tasks by category
        Map<String, List<TodoTask>> groupedTodoTasks = userTodoTasks.stream()
                .collect(Collectors.groupingBy(
                        task -> task.getCategory() != null ? task.getCategory() : "default",
                        Collectors.toList()
                ));
        
        return new UserTasksResponse(groupedTodoTasks, userDailyTasks);
    }

    /**
     * Creates a new TODO task for the specified user.
     * Sets the userId from the path parameter and initializes completed to false.
     *
     * @param userId the unique identifier of the user
     * @param request the task request containing id, description, category, order, and optional parentId
     * @return the created TodoTask
     */
    public TodoTask createTodoTask(String userId, TodoTaskRequest request) {
        TodoTask task = new TodoTask(
                request.getId(),
                userId,
                request.getDescription(),
                request.getCategory(),
                false,
                request.getOrder() != null ? request.getOrder() : 0,
                request.getParentId()
        );

        return todoTaskRepository.save(task);
    }

    /**
     * Creates a new Daily task for the specified user.
     * Sets the userId from the path parameter, initializes completed to false,
     * and sets createdAt to the current timestamp.
     *
     * @param userId the unique identifier of the user
     * @param request the task request containing id, description, and order
     * @return the created DailyTask
     */
    public DailyTask createDailyTask(String userId, DailyTaskRequest request) {
        DailyTask task = new DailyTask(
                request.getId(),
                userId,
                request.getDescription(),
                false,
                request.getOrder() != null ? request.getOrder() : 0,
                LocalDateTime.now()
        );

        return dailyTaskRepository.save(task);
    }

    /**
     * Updates an existing TODO task for the specified user.
     * Only updates fields that are provided (non-null) in the request.
     * Throws TaskNotFoundException if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user
     * @param taskId the unique identifier of the task to update
     * @param request the task request containing optional description, completed, and order fields
     * @return the updated TodoTask
     * @throws TaskNotFoundException if the task is not found for the specified user
     */
    public TodoTask updateTodoTask(String userId, String taskId, TodoTaskRequest request) {
        TodoTask task = todoTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("TODO task not found: " + taskId));
        
        // Verify the task belongs to the user
        if (!task.getUserId().equals(userId)) {
            throw new TaskNotFoundException("TODO task not found: " + taskId);
        }
        
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getCompleted() != null) {
            task.setCompleted(request.getCompleted());
        }
        if (request.getOrder() != null) {
            task.setOrder(request.getOrder());
        }
        
        return todoTaskRepository.save(task);
    }

    /**
     * Updates an existing Daily task for the specified user.
     * Only updates fields that are provided (non-null) in the request.
     * When completed is set to true, creates a completion record for today if not exists.
     * When completed is set to false, deletes the completion record for today if exists.
     * Throws TaskNotFoundException if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user
     * @param taskId the unique identifier of the task to update
     * @param request the task request containing optional description, completed, and order fields
     * @return the updated DailyTask
     * @throws TaskNotFoundException if the task is not found for the specified user
     */
    @Transactional
    public DailyTask updateDailyTask(String userId, String taskId, DailyTaskRequest request) {
        DailyTask task = dailyTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Daily task not found: " + taskId));
        
        // Verify the task belongs to the user
        if (!task.getUserId().equals(userId)) {
            throw new TaskNotFoundException("Daily task not found: " + taskId);
        }
        
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getCompleted() != null) {
            if (request.getCompleted()) {
                // Create completion record for today if not exists
                createCompletionRecordIfNotExists(taskId, LocalDate.now());
            } else {
                // Remove completion record for today
                deleteCompletionRecordForDate(taskId, LocalDate.now());
            }
            task.setCompleted(request.getCompleted());
        }
        if (request.getOrder() != null) {
            task.setOrder(request.getOrder());
        }
        
        return dailyTaskRepository.save(task);
    }

    /**
     * Creates a completion record for the specified task and date if one doesn't already exist.
     * This ensures idempotent behavior when marking a task complete multiple times on the same day.
     *
     * @param taskId the daily task ID
     * @param date the completion date
     */
    private void createCompletionRecordIfNotExists(String taskId, LocalDate date) {
        Optional<DailyTaskCompletion> existing = dailyTaskCompletionRepository
                .findByDailyTaskIdAndCompletionDate(taskId, date);
        
        if (existing.isEmpty()) {
            DailyTaskCompletion completion = new DailyTaskCompletion(taskId, date);
            dailyTaskCompletionRepository.save(completion);
        }
    }

    /**
     * Deletes the completion record for the specified task and date if it exists.
     * Proceeds without error if no record exists.
     *
     * @param taskId the daily task ID
     * @param date the completion date to remove
     */
    private void deleteCompletionRecordForDate(String taskId, LocalDate date) {
        dailyTaskCompletionRepository.deleteByDailyTaskIdAndCompletionDate(taskId, date);
    }

    /**
     * Deletes a TODO task and all its children for the specified user.
     * Cascade deletes all tasks that have the deleted task's id as their parentId.
     * Throws TaskNotFoundException if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user
     * @param taskId the unique identifier of the task to delete
     * @throws TaskNotFoundException if the task is not found for the specified user
     */
    public void deleteTodoTask(String userId, String taskId) {
        TodoTask task = todoTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("TODO task not found: " + taskId));
        
        // Verify the task belongs to the user
        if (!task.getUserId().equals(userId)) {
            throw new TaskNotFoundException("TODO task not found: " + taskId);
        }
        
        // Delete the task
        todoTaskRepository.delete(task);
        
        // Cascade delete: find and remove all children with matching parentId
        List<TodoTask> allUserTasks = todoTaskRepository.findByUserId(userId);
        List<TodoTask> childTasks = allUserTasks.stream()
                .filter(t -> taskId.equals(t.getParentId()))
                .collect(Collectors.toList());
        
        todoTaskRepository.deleteAll(childTasks);
    }

    /**
     * Deletes a Daily task for the specified user.
     * Also deletes all associated completion records to maintain data integrity.
     * Throws TaskNotFoundException if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user
     * @param taskId the unique identifier of the task to delete
     * @throws TaskNotFoundException if the task is not found for the specified user
     */
    @Transactional
    public void deleteDailyTask(String userId, String taskId) {
        DailyTask task = dailyTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Daily task not found: " + taskId));
        
        // Verify the task belongs to the user
        if (!task.getUserId().equals(userId)) {
            throw new TaskNotFoundException("Daily task not found: " + taskId);
        }
        
        // Delete all completion records for this task first
        dailyTaskCompletionRepository.deleteByDailyTaskId(taskId);
        
        dailyTaskRepository.delete(task);
    }

    /**
     * Retrieves the completion history for a daily task.
     * Returns all dates when the task was marked as complete, in descending order (most recent first).
     * Throws TaskNotFoundException if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user
     * @param taskId the unique identifier of the daily task
     * @return list of completion dates in descending order, or empty list if no completions exist
     * @throws TaskNotFoundException if the task is not found for the specified user
     */
    public List<LocalDate> getCompletionHistory(String userId, String taskId) {
        DailyTask task = dailyTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Daily task not found: " + taskId));
        
        // Verify the task belongs to the user
        if (!task.getUserId().equals(userId)) {
            throw new TaskNotFoundException("Daily task not found: " + taskId);
        }
        
        List<DailyTaskCompletion> completions = dailyTaskCompletionRepository
                .findByDailyTaskIdOrderByCompletionDateDesc(taskId);
        
        return completions.stream()
                .map(DailyTaskCompletion::getCompletionDate)
                .collect(Collectors.toList());
    }
}
