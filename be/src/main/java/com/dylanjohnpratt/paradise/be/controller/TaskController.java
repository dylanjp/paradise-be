package com.dylanjohnpratt.paradise.be.controller;

import com.dylanjohnpratt.paradise.be.dto.*;
import com.dylanjohnpratt.paradise.be.model.DailyTask;
import com.dylanjohnpratt.paradise.be.model.TodoTask;
import com.dylanjohnpratt.paradise.be.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * REST controller handling all task-related HTTP endpoints.
 * Maps the base path /users/{userId}/tasks and delegates business logic to TaskService.
 * Extracts userId from the request path for all endpoints to ensure user-scoped operations.
 */
@RestController
@RequestMapping("/users/{userId}/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Retrieves all tasks (TODO and Daily) for the specified user.
     * Returns both task types in a single response with TODO tasks grouped by category.
     *
     * @param userId the unique identifier of the user from the path
     * @return ResponseEntity containing UserTasksResponse with 200 OK status
     */
    @GetMapping
    public ResponseEntity<UserTasksResponse> getAllTasks(@PathVariable String userId) {
        UserTasksResponse response = taskService.getAllTasksForUser(userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new TODO task for the specified user.
     * The task is associated with the user from the path and initialized with completed=false.
     *
     * @param userId the unique identifier of the user from the path
     * @param request the task request containing id, description, category, order, and optional parentId
     * @return ResponseEntity containing the created TodoTask with 201 Created status
     */
    @PostMapping("/todo")
    public ResponseEntity<TodoTask> createTodoTask(
            @PathVariable String userId,
            @RequestBody TodoTaskRequest request) {
        TodoTask task = taskService.createTodoTask(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    /**
     * Creates a new Daily task for the specified user.
     * The task is associated with the user from the path, initialized with completed=false,
     * and createdAt set to the current timestamp.
     *
     * @param userId the unique identifier of the user from the path
     * @param request the task request containing id, description, and order
     * @return ResponseEntity containing the created DailyTask with 201 Created status
     */
    @PostMapping("/daily")
    public ResponseEntity<DailyTask> createDailyTask(
            @PathVariable String userId,
            @RequestBody DailyTaskRequest request) {
        DailyTask task = taskService.createDailyTask(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    /**
     * Updates an existing TODO task for the specified user.
     * Only updates fields that are provided (non-null) in the request.
     * Returns 404 if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user from the path
     * @param id the unique identifier of the task to update
     * @param request the task request containing optional description, completed, and order fields
     * @return ResponseEntity containing the updated TodoTask with 200 OK status
     */
    @PutMapping("/todo/{id}")
    public ResponseEntity<TodoTask> updateTodoTask(
            @PathVariable String userId,
            @PathVariable String id,
            @RequestBody TodoTaskRequest request) {
        TodoTask task = taskService.updateTodoTask(userId, id, request);
        return ResponseEntity.ok(task);
    }

    /**
     * Updates an existing Daily task for the specified user.
     * Only updates fields that are provided (non-null) in the request.
     * Returns 404 if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user from the path
     * @param id the unique identifier of the task to update
     * @param request the task request containing optional description, completed, and order fields
     * @return ResponseEntity containing the updated DailyTask with 200 OK status
     */
    @PutMapping("/daily/{id}")
    public ResponseEntity<DailyTask> updateDailyTask(
            @PathVariable String userId,
            @PathVariable String id,
            @RequestBody DailyTaskRequest request) {
        DailyTask task = taskService.updateDailyTask(userId, id, request);
        return ResponseEntity.ok(task);
    }

    /**
     * Deletes a TODO task and all its children for the specified user.
     * Cascade deletes all tasks that have the deleted task's id as their parentId.
     * Returns 404 if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user from the path
     * @param id the unique identifier of the task to delete
     * @return ResponseEntity with 204 No Content status
     */
    @DeleteMapping("/todo/{id}")
    public ResponseEntity<Void> deleteTodoTask(
            @PathVariable String userId,
            @PathVariable String id) {
        taskService.deleteTodoTask(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes a Daily task for the specified user.
     * Returns 404 if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user from the path
     * @param id the unique identifier of the task to delete
     * @return ResponseEntity with 204 No Content status
     */
    @DeleteMapping("/daily/{id}")
    public ResponseEntity<Void> deleteDailyTask(
            @PathVariable String userId,
            @PathVariable String id) {
        taskService.deleteDailyTask(userId, id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves the completion history for a daily task.
     * Returns all dates when the task was marked as complete, in descending order (most recent first).
     * Returns 404 if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user from the path
     * @param id the unique identifier of the daily task
     * @return ResponseEntity containing list of completion dates with 200 OK status
     */
    @GetMapping("/daily/{id}/completions")
    public ResponseEntity<List<LocalDate>> getDailyTaskCompletions(
            @PathVariable String userId,
            @PathVariable String id) {
        List<LocalDate> completions = taskService.getCompletionHistory(userId, id);
        return ResponseEntity.ok(completions);
    }
}
