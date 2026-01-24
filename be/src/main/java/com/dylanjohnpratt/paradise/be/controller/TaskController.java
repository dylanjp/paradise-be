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
 * REST controller handling all task-related HTTP endpoints.
 * Maps the base path /users/{userId}/tasks and delegates business logic to TaskService.
 * Extracts userId from the request path for all endpoints to ensure user-scoped operations.
 * Admins can access any user's tasks; regular users can only access their own.
 */
@RestController
@RequestMapping("/users/{userId}/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * Checks if the authenticated user has the ADMIN role.
     */
    private boolean isAdmin(User user) {
        return user.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Retrieves all tasks (TODO and Daily) for the specified user.
     * Returns both task types in a single response with TODO tasks grouped by category.
     *
     * @param userId the unique identifier of the user from the path
     * @param currentUser the authenticated user
     * @return ResponseEntity containing UserTasksResponse with 200 OK status
     */
    @GetMapping
    public ResponseEntity<UserTasksResponse> getAllTasks(
            @PathVariable String userId,
            @AuthenticationPrincipal User currentUser) {
        UserTasksResponse response = taskService.getAllTasksForUser(
                userId, 
                currentUser.getUsername(), 
                isAdmin(currentUser));
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new TODO task for the specified user.
     * The task is associated with the user from the path and initialized with completed=false.
     *
     * @param userId the unique identifier of the user from the path
     * @param request the task request containing id, description, category, order, and optional parentId
     * @param currentUser the authenticated user
     * @return ResponseEntity containing the created TodoTask with 201 Created status
     */
    @PostMapping("/todo")
    public ResponseEntity<TodoTask> createTodoTask(
            @PathVariable String userId,
            @RequestBody TodoTaskRequest request,
            @AuthenticationPrincipal User currentUser) {
        TodoTask task = taskService.createTodoTask(
                userId, 
                request, 
                currentUser.getUsername(), 
                isAdmin(currentUser));
        return ResponseEntity.status(HttpStatus.CREATED).body(task);
    }

    /**
     * Creates a new Daily task for the specified user.
     * The task is associated with the user from the path, initialized with completed=false,
     * and createdAt set to the current timestamp.
     *
     * @param userId the unique identifier of the user from the path
     * @param request the task request containing id, description, and order
     * @param currentUser the authenticated user
     * @return ResponseEntity containing the created DailyTask with 201 Created status
     */
    @PostMapping("/daily")
    public ResponseEntity<DailyTask> createDailyTask(
            @PathVariable String userId,
            @RequestBody DailyTaskRequest request,
            @AuthenticationPrincipal User currentUser) {
        DailyTask task = taskService.createDailyTask(
                userId, 
                request, 
                currentUser.getUsername(), 
                isAdmin(currentUser));
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
     * @param currentUser the authenticated user
     * @return ResponseEntity containing the updated TodoTask with 200 OK status
     */
    @PutMapping("/todo/{id}")
    public ResponseEntity<TodoTask> updateTodoTask(
            @PathVariable String userId,
            @PathVariable @NonNull String id,
            @RequestBody TodoTaskRequest request,
            @AuthenticationPrincipal User currentUser) {
        TodoTask task = taskService.updateTodoTask(
                userId, 
                id, 
                request, 
                currentUser.getUsername(), 
                isAdmin(currentUser));
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
     * @param currentUser the authenticated user
     * @return ResponseEntity containing the updated DailyTask with 200 OK status
     */
    @PutMapping("/daily/{id}")
    public ResponseEntity<DailyTask> updateDailyTask(
            @PathVariable String userId,
            @PathVariable @NonNull String id,
            @RequestBody DailyTaskRequest request,
            @AuthenticationPrincipal User currentUser) {
        DailyTask task = taskService.updateDailyTask(
                userId, 
                id, 
                request, 
                currentUser.getUsername(), 
                isAdmin(currentUser));
        return ResponseEntity.ok(task);
    }

    /**
     * Deletes a TODO task and all its children for the specified user.
     * Cascade deletes all tasks that have the deleted task's id as their parentId.
     * Returns 404 if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user from the path
     * @param id the unique identifier of the task to delete
     * @param currentUser the authenticated user
     * @return ResponseEntity with 204 No Content status
     */
    @DeleteMapping("/todo/{id}")
    public ResponseEntity<Void> deleteTodoTask(
            @PathVariable String userId,
            @PathVariable @NonNull String id,
            @AuthenticationPrincipal User currentUser) {
        taskService.deleteTodoTask(
                userId, 
                id, 
                currentUser.getUsername(), 
                isAdmin(currentUser));
        return ResponseEntity.noContent().build();
    }

    /**
     * Deletes a Daily task for the specified user.
     * Returns 404 if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user from the path
     * @param id the unique identifier of the task to delete
     * @param currentUser the authenticated user
     * @return ResponseEntity with 204 No Content status
     */
    @DeleteMapping("/daily/{id}")
    public ResponseEntity<Void> deleteDailyTask(
            @PathVariable String userId,
            @PathVariable @NonNull String id,
            @AuthenticationPrincipal User currentUser) {
        taskService.deleteDailyTask(
                userId, 
                id, 
                currentUser.getUsername(), 
                isAdmin(currentUser));
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves the completion history for a daily task.
     * Returns all dates when the task was marked as complete, in descending order (most recent first).
     * Returns 404 if the task doesn't exist or belongs to a different user.
     *
     * @param userId the unique identifier of the user from the path
     * @param id the unique identifier of the daily task
     * @param currentUser the authenticated user
     * @return ResponseEntity containing list of completion dates with 200 OK status
     */
    @GetMapping("/daily/{id}/completions")
    public ResponseEntity<List<LocalDate>> getDailyTaskCompletions(
            @PathVariable String userId,
            @PathVariable @NonNull String id,
            @AuthenticationPrincipal User currentUser) {
        List<LocalDate> completions = taskService.getCompletionHistory(
                userId, 
                id, 
                currentUser.getUsername(), 
                isAdmin(currentUser));
        return ResponseEntity.ok(completions);
    }

    /**
     * Retrieves all "perfect days" for the specified user in a given year.
     * A perfect day is a date where the user completed ALL daily tasks that existed on that date.
     * Returns dates in descending order (most recent first).
     *
     * @param userId the unique identifier of the user from the path
     * @param year the year to retrieve perfect days for (optional, defaults to current year)
     * @param currentUser the authenticated user
     * @return ResponseEntity containing list of perfect day dates with 200 OK status
     *         or 400 Bad Request if year is invalid
     */
    @GetMapping("/daily/perfect-days")
    public ResponseEntity<List<LocalDate>> getPerfectDays(
            @PathVariable String userId,
            @RequestParam(required = false) Integer year,
            @AuthenticationPrincipal User currentUser) {
        
        // Default to current year if not provided (Requirement 1.5)
        int targetYear = (year != null) ? year : LocalDate.now().getYear();
        
        // Validate year is within reasonable range (Requirement 1.7)
        int currentYear = LocalDate.now().getYear();
        if (targetYear < 2000 || targetYear > currentYear + 1) {
            return ResponseEntity.badRequest().build();
        }
        
        List<LocalDate> perfectDays = taskService.getPerfectDays(
                userId, 
                targetYear, 
                currentUser.getUsername(), 
                isAdmin(currentUser));
        return ResponseEntity.ok(perfectDays);
    }
}
