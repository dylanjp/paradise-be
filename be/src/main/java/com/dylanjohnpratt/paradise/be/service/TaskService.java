package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.dto.*;
import com.dylanjohnpratt.paradise.be.exception.TaskNotFoundException;
import com.dylanjohnpratt.paradise.be.model.DailyTask;
import com.dylanjohnpratt.paradise.be.model.TodoTask;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service containing all business logic for task management operations.
 * Uses in-memory storage with ConcurrentHashMap for thread safety.
 * Designed for future replacement with repository interfaces for database persistence.
 */
@Service
public class TaskService {

    /**
     * In-memory storage for TODO tasks.
     * Structure: userId -> (category -> list of tasks)
     * This nested structure allows efficient retrieval of tasks grouped by category.
     * Can be replaced with a TodoTaskRepository interface for database persistence.
     */
    private final Map<String, Map<String, List<TodoTask>>> todoTasks = new ConcurrentHashMap<>();

    /**
     * In-memory storage for Daily tasks.
     * Structure: userId -> list of daily tasks
     * Can be replaced with a DailyTaskRepository interface for database persistence.
     */
    private final Map<String, List<DailyTask>> dailyTasks = new ConcurrentHashMap<>();

    /**
     * Retrieves all tasks (both TODO and Daily) for a specific user.
     * Returns empty collections if the user has no tasks.
     *
     * @param userId the unique identifier of the user
     * @return UserTasksResponse containing TODO tasks grouped by category and Daily tasks as a list
     */
    public UserTasksResponse getAllTasksForUser(String userId) {
        Map<String, List<TodoTask>> userTodoTasks = todoTasks.getOrDefault(userId, new HashMap<>());
        List<DailyTask> userDailyTasks = dailyTasks.getOrDefault(userId, new ArrayList<>());
        return new UserTasksResponse(userTodoTasks, userDailyTasks);
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

        todoTasks.computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(request.getCategory(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(task);

        return task;
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

        dailyTasks.computeIfAbsent(userId, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(task);

        return task;
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
        Map<String, List<TodoTask>> userTasks = todoTasks.get(userId);
        if (userTasks == null) {
            throw new TaskNotFoundException("TODO task not found: " + taskId);
        }

        for (List<TodoTask> categoryTasks : userTasks.values()) {
            for (TodoTask task : categoryTasks) {
                if (task.getId().equals(taskId)) {
                    if (request.getDescription() != null) {
                        task.setDescription(request.getDescription());
                    }
                    if (request.getCompleted() != null) {
                        task.setCompleted(request.getCompleted());
                    }
                    if (request.getOrder() != null) {
                        task.setOrder(request.getOrder());
                    }
                    return task;
                }
            }
        }

        throw new TaskNotFoundException("TODO task not found: " + taskId);
    }

    /**
     * Updates an existing Daily task for the specified user.
     * Only updates fields that are provided (non-null) in the request.
     * Throws TaskNotFoundException if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user
     * @param taskId the unique identifier of the task to update
     * @param request the task request containing optional description, completed, and order fields
     * @return the updated DailyTask
     * @throws TaskNotFoundException if the task is not found for the specified user
     */
    public DailyTask updateDailyTask(String userId, String taskId, DailyTaskRequest request) {
        List<DailyTask> userTasks = dailyTasks.get(userId);
        if (userTasks == null) {
            throw new TaskNotFoundException("Daily task not found: " + taskId);
        }

        for (DailyTask task : userTasks) {
            if (task.getId().equals(taskId)) {
                if (request.getDescription() != null) {
                    task.setDescription(request.getDescription());
                }
                if (request.getCompleted() != null) {
                    task.setCompleted(request.getCompleted());
                }
                if (request.getOrder() != null) {
                    task.setOrder(request.getOrder());
                }
                return task;
            }
        }

        throw new TaskNotFoundException("Daily task not found: " + taskId);
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
        Map<String, List<TodoTask>> userTasks = todoTasks.get(userId);
        if (userTasks == null) {
            throw new TaskNotFoundException("TODO task not found: " + taskId);
        }

        boolean found = false;
        for (List<TodoTask> categoryTasks : userTasks.values()) {
            Iterator<TodoTask> iterator = categoryTasks.iterator();
            while (iterator.hasNext()) {
                TodoTask task = iterator.next();
                if (task.getId().equals(taskId)) {
                    iterator.remove();
                    found = true;
                    break;
                }
            }
            if (found) break;
        }

        if (!found) {
            throw new TaskNotFoundException("TODO task not found: " + taskId);
        }

        // Cascade delete: remove all children with matching parentId
        for (List<TodoTask> categoryTasks : userTasks.values()) {
            categoryTasks.removeIf(task -> taskId.equals(task.getParentId()));
        }
    }

    /**
     * Deletes a Daily task for the specified user.
     * Throws TaskNotFoundException if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user
     * @param taskId the unique identifier of the task to delete
     * @throws TaskNotFoundException if the task is not found for the specified user
     */
    public void deleteDailyTask(String userId, String taskId) {
        List<DailyTask> userTasks = dailyTasks.get(userId);
        if (userTasks == null) {
            throw new TaskNotFoundException("Daily task not found: " + taskId);
        }

        boolean removed = userTasks.removeIf(task -> task.getId().equals(taskId));
        if (!removed) {
            throw new TaskNotFoundException("Daily task not found: " + taskId);
        }
    }
}
