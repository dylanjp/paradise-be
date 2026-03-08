package com.dylanjohnpratt.paradise.be.controller;

import com.dylanjohnpratt.paradise.be.dto.*;
import com.dylanjohnpratt.paradise.be.model.DailyTask;
import com.dylanjohnpratt.paradise.be.model.TodoTask;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.service.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller for task management operations.
 * Provides endpoints for creating, reading, updating, and deleting both todo tasks
 * (persistent, category-based, hierarchical) and daily tasks (resettable, completion-tracked).
 * Also exposes completion history and "perfect days" analytics for daily tasks.
 * All operations enforce user isolation — a user can only access their own tasks.
 */
@RestController
@RequestMapping("/users/{userId}/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Retrieves all tasks for the specified user.
     * Returns todo tasks grouped by category and daily tasks as a flat list.
     *
     * @param userId      the user whose tasks to retrieve
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return todo tasks grouped by category and all daily tasks
     */
    @GetMapping
    public ResponseEntity<UserTasksResponse> getAllTasks(
            @PathVariable String userId,
            @AuthenticationPrincipal User currentUser) {
        UserTasksResponse response = taskService.getAllTasksForUser(userId, currentUser.getUsername());
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new todo task for the specified user.
     * The task is initialized with completed set to false. Supports optional category,
     * ordering, and parent-child hierarchy via parentId.
     *
     * @param userId      the user to create the task for
     * @param request     the task creation request with ID, description, category, order, and parentId
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the created {@link TodoTask} with HTTP 201 Created
     */
    @PostMapping("/todo")
    public ResponseEntity<TodoTask> createTodoTask(
            @PathVariable String userId,
            @RequestBody TodoTaskRequest request,
            @AuthenticationPrincipal User currentUser) {
        TodoTask task = taskService.createTodoTask(userId, request, currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    /**
     * Creates a new daily task for the specified user.
     * Daily tasks reset to incomplete each day and track completion history over time.
     * The task is initialized with completed set to false and createdAt set to now.
     *
     * @param userId      the user to create the task for
     * @param request     the task creation request with ID, description, and order
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the created {@link DailyTask} with HTTP 201 Created
     */
    @PostMapping("/daily")
    public ResponseEntity<DailyTask> createDailyTask(
            @PathVariable String userId,
            @RequestBody DailyTaskRequest request,
            @AuthenticationPrincipal User currentUser) {
        DailyTask task = taskService.createDailyTask(userId, request, currentUser.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    /**
     * Updates an existing todo task.
     * Supports partial updates — only the fields provided in the request body are modified.
     * Can update description, completed status, order, and parentId.
     *
     * @param userId      the owner of the task
     * @param id          the task ID to update
     * @param request     the update request with optional fields
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the updated {@link TodoTask}
     */
    @PutMapping("/todo/{id}")
    public ResponseEntity<TodoTask> updateTodoTask(
            @PathVariable String userId,
            @PathVariable @NonNull String id,
            @RequestBody TodoTaskRequest request,
            @AuthenticationPrincipal User currentUser) {
        TodoTask task = taskService.updateTodoTask(userId, id, request, currentUser.getUsername());
        return ResponseEntity.ok(task);
    }

    /**
     * Updates an existing daily task.
     * Supports partial updates — only the fields provided in the request body are modified.
     * When completed is set to true, a completion record is created for today's date.
     * When completed is set to false, today's completion record is removed.
     *
     * @param userId      the owner of the task
     * @param id          the task ID to update
     * @param request     the update request with optional fields
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return the updated {@link DailyTask}
     */
    @PutMapping("/daily/{id}")
    public ResponseEntity<DailyTask> updateDailyTask(
            @PathVariable String userId,
            @PathVariable @NonNull String id,
            @RequestBody DailyTaskRequest request,
            @AuthenticationPrincipal User currentUser) {
        DailyTask task = taskService.updateDailyTask(userId, id, request, currentUser.getUsername());
        return ResponseEntity.ok(task);
    }

    /**
     * Deletes a todo task. Any child tasks that reference this task as their parent
     * are un-nested (their parentId is set to null) rather than being cascade-deleted.
     *
     * @param userId      the owner of the task
     * @param id          the task ID to delete
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return HTTP 204 No Content on success
     */
    @DeleteMapping("/todo/{id}")
    public ResponseEntity<Void> deleteTodoTask(
            @PathVariable String userId,
            @PathVariable @NonNull String id,
            @AuthenticationPrincipal User currentUser) {
        taskService.deleteTodoTask(userId, id, currentUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes a daily task and all of its associated completion history records.
     *
     * @param userId      the owner of the task
     * @param id          the task ID to delete
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return HTTP 204 No Content on success
     */
    @DeleteMapping("/daily/{id}")
    public ResponseEntity<Void> deleteDailyTask(
            @PathVariable String userId,
            @PathVariable @NonNull String id,
            @AuthenticationPrincipal User currentUser) {
        taskService.deleteDailyTask(userId, id, currentUser.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves the completion history for a daily task as a list of dates
     * when the task was marked complete, sorted in descending order (most recent first).
     *
     * @param userId      the owner of the task
     * @param id          the daily task ID
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return list of completion dates in descending order
     */
    @GetMapping("/daily/{id}/completions")
    public ResponseEntity<List<LocalDate>> getDailyTaskCompletions(
            @PathVariable String userId,
            @PathVariable @NonNull String id,
            @AuthenticationPrincipal User currentUser) {
        List<LocalDate> completions = taskService.getCompletionHistory(userId, id, currentUser.getUsername());
        return ResponseEntity.ok(completions);
    }

    /**
     * Retrieves "perfect days" for the user in a given year — dates on which the user
     * completed every daily task that existed at that time. Defaults to the current year
     * if no year parameter is provided. Rejects years before 2000 or more than one year
     * in the future.
     *
     * @param userId      the owner of the tasks
     * @param year        optional year to query (defaults to current year)
     * @param currentUser the currently authenticated user, injected by Spring Security
     * @return list of perfect day dates in descending order
     */
    @GetMapping("/daily/perfect-days")
    public ResponseEntity<List<LocalDate>> getPerfectDays(
            @PathVariable String userId,
            @RequestParam(required = false) Integer year,
            @AuthenticationPrincipal User currentUser) {

        int targetYear = (year != null) ? year : LocalDate.now().getYear();

        int currentYear = LocalDate.now().getYear();
        if (targetYear < 2000 || targetYear > currentYear + 1) {
            return ResponseEntity.badRequest().build();
        }

        List<LocalDate> perfectDays = taskService.getPerfectDays(userId, targetYear, currentUser.getUsername());
        return ResponseEntity.ok(perfectDays);
    }
}
