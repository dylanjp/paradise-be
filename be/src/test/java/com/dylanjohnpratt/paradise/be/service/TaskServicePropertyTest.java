package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.dto.*;
import com.dylanjohnpratt.paradise.be.exception.TaskNotFoundException;
import com.dylanjohnpratt.paradise.be.model.DailyTask;
import com.dylanjohnpratt.paradise.be.model.TodoTask;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for TaskService.
 * Uses jqwik to verify correctness properties across many generated inputs.
 */
class TaskServicePropertyTest {

    /**
     * Feature: task-management-api, Property 2: TODO Task Creation Integrity
     * For any valid CreateTodoTaskRequest, when a TODO task is created, the resulting task
     * SHALL have the provided id, description, category, order, and parentId, with userId
     * set to the requesting user and completed initialized to false.
     * 
     * Validates: Requirements 2.1, 2.2, 2.3, 2.4
     */
    @Property(tries = 100)
    @Label("Feature: task-management-api, Property 2: TODO Task Creation Integrity")
    void todoTaskCreationIntegrity(
            @ForAll @NotBlank @Size(max = 50) String userId,
            @ForAll @NotBlank @Size(max = 50) String taskId,
            @ForAll @NotBlank @Size(max = 200) String description,
            @ForAll @NotBlank @Size(max = 50) String category,
            @ForAll int order,
            @ForAll @Size(max = 50) String parentId
    ) {
        TaskService service = new TaskService();
        
        TodoTaskRequest request = new TodoTaskRequest(
                taskId, description, category, null, order, parentId
        );
        
        TodoTask created = service.createTodoTask(userId, request);
        
        assertThat(created.getId()).isEqualTo(taskId);
        assertThat(created.getUserId()).isEqualTo(userId);
        assertThat(created.getDescription()).isEqualTo(description);
        assertThat(created.getCategory()).isEqualTo(category);
        assertThat(created.getOrder()).isEqualTo(order);
        assertThat(created.getParentId()).isEqualTo(parentId);
        assertThat(created.isCompleted()).isFalse();
    }

    /**
     * Feature: task-management-api, Property 3: Daily Task Creation Integrity
     * For any valid CreateDailyTaskRequest, when a Daily task is created, the resulting task
     * SHALL have the provided id, description, and order, with userId set to the requesting user,
     * completed initialized to false, and createdAt set to a non-null timestamp.
     * 
     * Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5
     */
    @Property(tries = 100)
    @Label("Feature: task-management-api, Property 3: Daily Task Creation Integrity")
    void dailyTaskCreationIntegrity(
            @ForAll @NotBlank @Size(max = 50) String userId,
            @ForAll @NotBlank @Size(max = 50) String taskId,
            @ForAll @NotBlank @Size(max = 200) String description,
            @ForAll int order
    ) {
        TaskService service = new TaskService();
        
        DailyTaskRequest request = new DailyTaskRequest(taskId, description, null, order);
        
        DailyTask created = service.createDailyTask(userId, request);
        
        assertThat(created.getId()).isEqualTo(taskId);
        assertThat(created.getUserId()).isEqualTo(userId);
        assertThat(created.getDescription()).isEqualTo(description);
        assertThat(created.getOrder()).isEqualTo(order);
        assertThat(created.isCompleted()).isFalse();
        assertThat(created.getCreatedAt()).isNotNull();
    }

    /**
     * Feature: task-management-api, Property 4: TODO Task Update Integrity
     * For any existing TODO task and valid UpdateTodoTaskRequest, when the task is updated,
     * only the specified fields (description, completed, order) SHALL be modified while
     * other fields remain unchanged.
     * 
     * Validates: Requirements 4.1, 4.2
     */
    @Property(tries = 100)
    @Label("Feature: task-management-api, Property 4: TODO Task Update Integrity")
    void todoTaskUpdateIntegrity(
            @ForAll @NotBlank @Size(max = 50) String userId,
            @ForAll @NotBlank @Size(max = 50) String taskId,
            @ForAll @NotBlank @Size(max = 200) String originalDescription,
            @ForAll @NotBlank @Size(max = 50) String category,
            @ForAll int originalOrder,
            @ForAll @Size(max = 50) String parentId,
            @ForAll @NotBlank @Size(max = 200) String newDescription,
            @ForAll boolean newCompleted,
            @ForAll int newOrder
    ) {
        TaskService service = new TaskService();
        
        // Create the task first
        TodoTaskRequest createRequest = new TodoTaskRequest(
                taskId, originalDescription, category, null, originalOrder, parentId
        );
        service.createTodoTask(userId, createRequest);
        
        // Update the task
        TodoTaskRequest updateRequest = new TodoTaskRequest(
                null, newDescription, null, newCompleted, newOrder, null
        );
        TodoTask updated = service.updateTodoTask(userId, taskId, updateRequest);
        
        // Verify updated fields changed
        assertThat(updated.getDescription()).isEqualTo(newDescription);
        assertThat(updated.isCompleted()).isEqualTo(newCompleted);
        assertThat(updated.getOrder()).isEqualTo(newOrder);
        
        // Verify unchanged fields remain the same
        assertThat(updated.getId()).isEqualTo(taskId);
        assertThat(updated.getUserId()).isEqualTo(userId);
        assertThat(updated.getCategory()).isEqualTo(category);
        assertThat(updated.getParentId()).isEqualTo(parentId);
    }

    /**
     * Feature: task-management-api, Property 5: Daily Task Update Integrity
     * For any existing Daily task and valid UpdateDailyTaskRequest, when the task is updated,
     * only the specified fields (description, completed, order) SHALL be modified while
     * other fields (id, userId, createdAt) remain unchanged.
     * 
     * Validates: Requirements 5.1, 5.2
     */
    @Property(tries = 100)
    @Label("Feature: task-management-api, Property 5: Daily Task Update Integrity")
    void dailyTaskUpdateIntegrity(
            @ForAll @NotBlank @Size(max = 50) String userId,
            @ForAll @NotBlank @Size(max = 50) String taskId,
            @ForAll @NotBlank @Size(max = 200) String originalDescription,
            @ForAll int originalOrder,
            @ForAll @NotBlank @Size(max = 200) String newDescription,
            @ForAll boolean newCompleted,
            @ForAll int newOrder
    ) {
        TaskService service = new TaskService();
        
        // Create the task first
        DailyTaskRequest createRequest = new DailyTaskRequest(
                taskId, originalDescription, null, originalOrder
        );
        DailyTask created = service.createDailyTask(userId, createRequest);
        var originalCreatedAt = created.getCreatedAt();
        
        // Update the task
        DailyTaskRequest updateRequest = new DailyTaskRequest(
                null, newDescription, newCompleted, newOrder
        );
        DailyTask updated = service.updateDailyTask(userId, taskId, updateRequest);
        
        // Verify updated fields changed
        assertThat(updated.getDescription()).isEqualTo(newDescription);
        assertThat(updated.isCompleted()).isEqualTo(newCompleted);
        assertThat(updated.getOrder()).isEqualTo(newOrder);
        
        // Verify unchanged fields remain the same
        assertThat(updated.getId()).isEqualTo(taskId);
        assertThat(updated.getUserId()).isEqualTo(userId);
        assertThat(updated.getCreatedAt()).isEqualTo(originalCreatedAt);
    }

    /**
     * Feature: task-management-api, Property 1: User Isolation
     * For any two distinct users and any task operation (create, read, update, delete),
     * a user SHALL only be able to access and modify their own tasks, never tasks
     * belonging to another user.
     * 
     * Validates: Requirements 1.3, 4.4, 5.4, 6.4, 7.3, 8.2, 8.3
     */
    @Property(tries = 100)
    @Label("Feature: task-management-api, Property 1: User Isolation")
    void userIsolation(
            @ForAll @NotBlank @Size(max = 50) String user1Id,
            @ForAll @NotBlank @Size(max = 50) String user2Id,
            @ForAll @NotBlank @Size(max = 50) String todoTaskId,
            @ForAll @NotBlank @Size(max = 50) String dailyTaskId,
            @ForAll @NotBlank @Size(max = 200) String description,
            @ForAll @NotBlank @Size(max = 50) String category,
            @ForAll int order
    ) {
        // Skip if users are the same (we need distinct users for isolation test)
        Assume.that(!user1Id.equals(user2Id));
        
        TaskService service = new TaskService();
        
        // User1 creates tasks
        TodoTaskRequest todoRequest = new TodoTaskRequest(
                todoTaskId, description, category, null, order, null
        );
        DailyTaskRequest dailyRequest = new DailyTaskRequest(
                dailyTaskId, description, null, order
        );
        
        service.createTodoTask(user1Id, todoRequest);
        service.createDailyTask(user1Id, dailyRequest);
        
        // Verify User2 cannot see User1's tasks via getAllTasksForUser
        UserTasksResponse user2Tasks = service.getAllTasksForUser(user2Id);
        Map<String, List<TodoTask>> user2TodoTasks = user2Tasks.getTodoTasks();
        List<DailyTask> user2DailyTasks = user2Tasks.getDailyTasks();
        
        // User2's task lists should be empty (no access to User1's tasks)
        assertThat(user2TodoTasks).isEmpty();
        assertThat(user2DailyTasks).isEmpty();
        
        // Verify User1 can see their own tasks
        UserTasksResponse user1Tasks = service.getAllTasksForUser(user1Id);
        assertThat(user1Tasks.getTodoTasks()).isNotEmpty();
        assertThat(user1Tasks.getDailyTasks()).isNotEmpty();
        
        // Verify User2 cannot update User1's TODO task
        TodoTaskRequest updateTodoRequest = new TodoTaskRequest(
                null, "modified", null, true, 999, null
        );
        assertThatThrownBy(() -> service.updateTodoTask(user2Id, todoTaskId, updateTodoRequest))
                .isInstanceOf(TaskNotFoundException.class);
        
        // Verify User2 cannot update User1's Daily task
        DailyTaskRequest updateDailyRequest = new DailyTaskRequest(
                null, "modified", true, 999
        );
        assertThatThrownBy(() -> service.updateDailyTask(user2Id, dailyTaskId, updateDailyRequest))
                .isInstanceOf(TaskNotFoundException.class);
        
        // Verify User2 cannot delete User1's TODO task
        assertThatThrownBy(() -> service.deleteTodoTask(user2Id, todoTaskId))
                .isInstanceOf(TaskNotFoundException.class);
        
        // Verify User2 cannot delete User1's Daily task
        assertThatThrownBy(() -> service.deleteDailyTask(user2Id, dailyTaskId))
                .isInstanceOf(TaskNotFoundException.class);
        
        // Verify User1's tasks are still intact after User2's failed attempts
        UserTasksResponse user1TasksAfter = service.getAllTasksForUser(user1Id);
        assertThat(user1TasksAfter.getTodoTasks().get(category))
                .anyMatch(task -> task.getId().equals(todoTaskId));
        assertThat(user1TasksAfter.getDailyTasks())
                .anyMatch(task -> task.getId().equals(dailyTaskId));
    }
}
