package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.dto.*;
import com.dylanjohnpratt.paradise.be.exception.TaskNotFoundException;
import com.dylanjohnpratt.paradise.be.model.DailyTask;
import com.dylanjohnpratt.paradise.be.model.DailyTaskCompletion;
import com.dylanjohnpratt.paradise.be.model.TodoTask;
import com.dylanjohnpratt.paradise.be.repository.DailyTaskCompletionRepository;
import com.dylanjohnpratt.paradise.be.repository.DailyTaskRepository;
import com.dylanjohnpratt.paradise.be.repository.TodoTaskRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;

import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.*;

import static java.util.Objects.requireNonNull;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for TaskService.
 * Uses jqwik to verify correctness properties across many generated inputs.
 * Uses in-memory repositories for testing.
 */
class TaskServicePropertyTest {

    /**
     * Creates a TaskService with in-memory repositories for testing.
     */
    private TaskService createTestService() {
        return new TaskService(new InMemoryTodoTaskRepository(), new InMemoryDailyTaskRepository(), new InMemoryDailyTaskCompletionRepository());
    }

    /**
     * In-memory implementation of TodoTaskRepository for testing.
     */
    private static class InMemoryTodoTaskRepository implements TodoTaskRepository {
        private final Map<String, TodoTask> tasks = new HashMap<>();

        @Override
        @NonNull
        public <S extends TodoTask> S save(@NonNull S task) {
            tasks.put(task.getId(), task);
            return task;
        }

        @Override
        @NonNull
        public Optional<TodoTask> findById(@NonNull String id) {
            return requireNonNull(Optional.ofNullable(tasks.get(id)));
        }

        @Override
        public List<TodoTask> findByUserId(String userId) {
            return tasks.values().stream()
                    .filter(t -> t.getUserId().equals(userId))
                    .toList();
        }

        @Override
        public List<TodoTask> findByUserIdAndCategory(String userId, String category) {
            return tasks.values().stream()
                    .filter(t -> t.getUserId().equals(userId) && 
                                 Objects.equals(t.getCategory(), category))
                    .toList();
        }

        @Override
        public List<TodoTask> findByUserIdAndParentId(String userId, String parentId) {
            return tasks.values().stream()
                    .filter(t -> t.getUserId().equals(userId) && 
                                 Objects.equals(t.getParentId(), parentId))
                    .toList();
        }

        @Override
        public void delete(@NonNull TodoTask task) {
            tasks.remove(task.getId());
        }

        @Override
        public void deleteAll(@NonNull Iterable<? extends TodoTask> entities) {
            entities.forEach(task -> tasks.remove(task.getId()));
        }

        // Unused methods for testing
        @Override @NonNull public List<TodoTask> findAll() { return new ArrayList<>(tasks.values()); }
        @Override @NonNull public List<TodoTask> findAllById(@NonNull Iterable<String> ids) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends TodoTask> List<S> saveAll(@NonNull Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            entities.forEach(entity -> {
                tasks.put(entity.getId(), entity);
                result.add(entity);
            });
            return result;
        }
        @Override public boolean existsById(@NonNull String s) { return false; }
        @Override public long count() { return 0; }
        @Override public void deleteById(@NonNull String s) { }
        @Override public void deleteAllById(@NonNull Iterable<? extends String> ids) { }
        @Override public void deleteAll() { }
        @Override public void flush() { }
        @Override @NonNull public <S extends TodoTask> S saveAndFlush(@NonNull S entity) { throw new UnsupportedOperationException(); }
        @Override @NonNull public <S extends TodoTask> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public void deleteAllInBatch(@NonNull Iterable<TodoTask> entities) { }
        @Override public void deleteAllByIdInBatch(@NonNull Iterable<String> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override @NonNull public TodoTask getOne(@NonNull String s) { throw new UnsupportedOperationException(); }
        @Override @NonNull public TodoTask getById(@NonNull String s) { throw new UnsupportedOperationException(); }
        @Override @NonNull public TodoTask getReferenceById(@NonNull String s) { throw new UnsupportedOperationException(); }
        @Override @NonNull public <S extends TodoTask> Optional<S> findOne(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(Optional.empty()); }
        @Override @NonNull public <S extends TodoTask> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends TodoTask> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends TodoTask> org.springframework.data.domain.Page<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends TodoTask> long count(@NonNull org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends TodoTask> boolean exists(@NonNull org.springframework.data.domain.Example<S> example) { return false; }
        @Override @NonNull public <S extends TodoTask, R> R findBy(@NonNull org.springframework.data.domain.Example<S> example, @NonNull java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override @NonNull public List<TodoTask> findAll(@NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public org.springframework.data.domain.Page<TodoTask> findAll(@NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
    }

    /**
     * In-memory implementation of DailyTaskRepository for testing.
     */
    private static class InMemoryDailyTaskRepository implements DailyTaskRepository {
        private final Map<String, DailyTask> tasks = new HashMap<>();

        @Override
        @NonNull
        public <S extends DailyTask> S save(@NonNull S task) {
            tasks.put(task.getId(), task);
            return task;
        }

        @Override
        @NonNull
        public Optional<DailyTask> findById(@NonNull String id) {
            return requireNonNull(Optional.ofNullable(tasks.get(id)));
        }

        @Override
        public List<DailyTask> findByUserId(String userId) {
            return tasks.values().stream()
                    .filter(t -> t.getUserId().equals(userId))
                    .toList();
        }

        @Override
        public void delete(@NonNull DailyTask task) {
            tasks.remove(task.getId());
        }

        // Unused methods for testing
        @Override @NonNull public List<DailyTask> findAll() { return new ArrayList<>(tasks.values()); }
        @Override @NonNull public List<DailyTask> findAllById(@NonNull Iterable<String> ids) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends DailyTask> List<S> saveAll(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public boolean existsById(@NonNull String s) { return false; }
        @Override public long count() { return 0; }
        @Override public void deleteById(@NonNull String s) { }
        @Override public void deleteAll(@NonNull Iterable<? extends DailyTask> entities) { }
        @Override public void deleteAllById(@NonNull Iterable<? extends String> ids) { }
        @Override public void deleteAll() { }
        @Override public void flush() { }
        @Override @NonNull public <S extends DailyTask> S saveAndFlush(@NonNull S entity) { throw new UnsupportedOperationException(); }
        @Override @NonNull public <S extends DailyTask> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public void deleteAllInBatch(@NonNull Iterable<DailyTask> entities) { }
        @Override public void deleteAllByIdInBatch(@NonNull Iterable<String> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override @NonNull public DailyTask getOne(@NonNull String s) { throw new UnsupportedOperationException(); }
        @Override @NonNull public DailyTask getById(@NonNull String s) { throw new UnsupportedOperationException(); }
        @Override @NonNull public DailyTask getReferenceById(@NonNull String s) { throw new UnsupportedOperationException(); }
        @Override @NonNull public <S extends DailyTask> Optional<S> findOne(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(Optional.empty()); }
        @Override @NonNull public <S extends DailyTask> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends DailyTask> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends DailyTask> org.springframework.data.domain.Page<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends DailyTask> long count(@NonNull org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends DailyTask> boolean exists(@NonNull org.springframework.data.domain.Example<S> example) { return false; }
        @Override @NonNull public <S extends DailyTask, R> R findBy(@NonNull org.springframework.data.domain.Example<S> example, @NonNull java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override @NonNull public List<DailyTask> findAll(@NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public org.springframework.data.domain.Page<DailyTask> findAll(@NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
    }

    /**
     * In-memory implementation of DailyTaskCompletionRepository for testing.
     */
    private static class InMemoryDailyTaskCompletionRepository implements DailyTaskCompletionRepository {
        private final Map<String, DailyTaskCompletion> completions = new HashMap<>();
        private int idCounter = 0;

        @Override
        @NonNull
        public <S extends DailyTaskCompletion> S save(@NonNull S completion) {
            if (completion.getId() == null) {
                completion.setId("completion-" + (++idCounter));
            }
            completions.put(completion.getId(), completion);
            return completion;
        }

        @Override
        public List<DailyTaskCompletion> findByDailyTaskIdOrderByCompletionDateDesc(String dailyTaskId) {
            return completions.values().stream()
                    .filter(c -> c.getDailyTaskId().equals(dailyTaskId))
                    .sorted((a, b) -> b.getCompletionDate().compareTo(a.getCompletionDate()))
                    .toList();
        }

        @Override
        public Optional<DailyTaskCompletion> findByDailyTaskIdAndCompletionDate(String dailyTaskId, LocalDate completionDate) {
            return completions.values().stream()
                    .filter(c -> c.getDailyTaskId().equals(dailyTaskId) && c.getCompletionDate().equals(completionDate))
                    .findFirst();
        }

        @Override
        public void deleteByDailyTaskIdAndCompletionDate(String dailyTaskId, LocalDate completionDate) {
            completions.entrySet().removeIf(entry -> 
                    entry.getValue().getDailyTaskId().equals(dailyTaskId) && 
                    entry.getValue().getCompletionDate().equals(completionDate));
        }

        @Override
        public void deleteByDailyTaskId(String dailyTaskId) {
            completions.entrySet().removeIf(entry -> entry.getValue().getDailyTaskId().equals(dailyTaskId));
        }

        @Override
        @NonNull
        public Optional<DailyTaskCompletion> findById(@NonNull String id) {
            return requireNonNull(Optional.ofNullable(completions.get(id)));
        }

        // Unused methods for testing
        @Override @NonNull public List<DailyTaskCompletion> findAll() { return new ArrayList<>(completions.values()); }
        @Override @NonNull public List<DailyTaskCompletion> findAllById(@NonNull Iterable<String> ids) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends DailyTaskCompletion> List<S> saveAll(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public boolean existsById(@NonNull String s) { return false; }
        @Override public long count() { return 0; }
        @Override public void deleteById(@NonNull String s) { }
        @Override public void delete(@NonNull DailyTaskCompletion entity) { completions.remove(entity.getId()); }
        @Override public void deleteAll(@NonNull Iterable<? extends DailyTaskCompletion> entities) { }
        @Override public void deleteAllById(@NonNull Iterable<? extends String> ids) { }
        @Override public void deleteAll() { }
        @Override public void flush() { }
        @Override @NonNull public <S extends DailyTaskCompletion> S saveAndFlush(@NonNull S entity) { throw new UnsupportedOperationException(); }
        @Override @NonNull public <S extends DailyTaskCompletion> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) { return requireNonNull(List.of()); }
        @Override public void deleteAllInBatch(@NonNull Iterable<DailyTaskCompletion> entities) { }
        @Override public void deleteAllByIdInBatch(@NonNull Iterable<String> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override @NonNull public DailyTaskCompletion getOne(@NonNull String s) { throw new UnsupportedOperationException(); }
        @Override @NonNull public DailyTaskCompletion getById(@NonNull String s) { throw new UnsupportedOperationException(); }
        @Override @NonNull public DailyTaskCompletion getReferenceById(@NonNull String s) { throw new UnsupportedOperationException(); }
        @Override @NonNull public <S extends DailyTaskCompletion> Optional<S> findOne(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(Optional.empty()); }
        @Override @NonNull public <S extends DailyTaskCompletion> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends DailyTaskCompletion> List<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public <S extends DailyTaskCompletion> org.springframework.data.domain.Page<S> findAll(@NonNull org.springframework.data.domain.Example<S> example, @NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
        @Override public <S extends DailyTaskCompletion> long count(@NonNull org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends DailyTaskCompletion> boolean exists(@NonNull org.springframework.data.domain.Example<S> example) { return false; }
        @Override @NonNull public <S extends DailyTaskCompletion, R> R findBy(@NonNull org.springframework.data.domain.Example<S> example, @NonNull java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw new UnsupportedOperationException(); }
        @Override @NonNull public List<DailyTaskCompletion> findAll(@NonNull org.springframework.data.domain.Sort sort) { return requireNonNull(List.of()); }
        @Override @NonNull public org.springframework.data.domain.Page<DailyTaskCompletion> findAll(@NonNull org.springframework.data.domain.Pageable pageable) { throw new UnsupportedOperationException(); }
    }

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
        TaskService service = createTestService();
        
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
        TaskService service = createTestService();
        
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
        TaskService service = createTestService();
        
        // Create the task first
        TodoTaskRequest createRequest = new TodoTaskRequest(
                taskId, originalDescription, category, null, originalOrder, parentId
        );
        service.createTodoTask(userId, createRequest);
        
        // Update the task
        TodoTaskRequest updateRequest = new TodoTaskRequest(
                null, newDescription, null, newCompleted, newOrder, null
        );
        TodoTask updated = service.updateTodoTask(userId, requireNonNull(taskId), updateRequest);
        
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
        TaskService service = createTestService();
        
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
        DailyTask updated = service.updateDailyTask(userId, requireNonNull(taskId), updateRequest);
        
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
        
        TaskService service = createTestService();
        
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
        assertThatThrownBy(() -> service.updateTodoTask(user2Id, requireNonNull(todoTaskId), updateTodoRequest))
                .isInstanceOf(TaskNotFoundException.class);
        
        // Verify User2 cannot update User1's Daily task
        DailyTaskRequest updateDailyRequest = new DailyTaskRequest(
                null, "modified", true, 999
        );
        assertThatThrownBy(() -> service.updateDailyTask(user2Id, requireNonNull(dailyTaskId), updateDailyRequest))
                .isInstanceOf(TaskNotFoundException.class);
        
        // Verify User2 cannot delete User1's TODO task
        assertThatThrownBy(() -> service.deleteTodoTask(user2Id, requireNonNull(todoTaskId)))
                .isInstanceOf(TaskNotFoundException.class);
        
        // Verify User2 cannot delete User1's Daily task
        assertThatThrownBy(() -> service.deleteDailyTask(user2Id, requireNonNull(dailyTaskId)))
                .isInstanceOf(TaskNotFoundException.class);
        
        // Verify User1's tasks are still intact after User2's failed attempts
        UserTasksResponse user1TasksAfter = service.getAllTasksForUser(user1Id);
        assertThat(user1TasksAfter.getTodoTasks().get(category))
                .anyMatch(task -> task.getId().equals(todoTaskId));
        assertThat(user1TasksAfter.getDailyTasks())
                .anyMatch(task -> task.getId().equals(dailyTaskId));
    }

    /**
     * Feature: daily-task-completion-tracking, Property 5: Completion History Returns All Dates in Descending Order
     * For any daily task with completion records, querying the completion history should return
     * all recorded dates, and each date in the list should be greater than or equal to the
     * following date (descending order).
     * 
     * Validates: Requirements 3.1, 3.2
     */
    @Property(tries = 100)
    @Label("Feature: daily-task-completion-tracking, Property 5: Completion History Returns All Dates in Descending Order")
    void completionHistoryReturnsAllDatesInDescendingOrder(
            @ForAll @NotBlank @Size(max = 50) String userId,
            @ForAll @NotBlank @Size(max = 50) String taskId,
            @ForAll @NotBlank @Size(max = 200) String description,
            @ForAll @Size(min = 0, max = 10) List<@NotBlank @Size(max = 10) String> dateOffsets
    ) {
        TaskService service = createTestService();
        
        // Create a daily task
        DailyTaskRequest createRequest = new DailyTaskRequest(taskId, description, null, 0);
        service.createDailyTask(userId, createRequest);
        
        // Generate unique completion dates from offsets (days before today)
        LocalDate today = LocalDate.now();
        Set<LocalDate> uniqueDates = new HashSet<>();
        for (int i = 0; i < dateOffsets.size(); i++) {
            uniqueDates.add(today.minusDays(i));
        }
        List<LocalDate> expectedDates = new ArrayList<>(uniqueDates);
        
        // Create completion records for each date by directly adding to repository
        // We need to access the repository through the service's internal state
        // Instead, we'll mark the task complete on different dates by simulating the behavior
        InMemoryDailyTaskCompletionRepository completionRepo = new InMemoryDailyTaskCompletionRepository();
        InMemoryDailyTaskRepository dailyRepo = new InMemoryDailyTaskRepository();
        InMemoryTodoTaskRepository todoRepo = new InMemoryTodoTaskRepository();
        
        TaskService testService = new TaskService(todoRepo, dailyRepo, completionRepo);
        
        // Create the task
        DailyTaskRequest request = new DailyTaskRequest(taskId, description, null, 0);
        testService.createDailyTask(userId, request);
        
        // Add completion records directly to the repository
        for (LocalDate date : expectedDates) {
            DailyTaskCompletion completion = new DailyTaskCompletion(taskId, date);
            completionRepo.save(completion);
        }
        
        // Get completion history
        List<LocalDate> history = testService.getCompletionHistory(userId, requireNonNull(taskId));
        
        // Verify all dates are returned
        assertThat(history).containsExactlyInAnyOrderElementsOf(expectedDates);
        
        // Verify descending order (each date >= next date)
        for (int i = 0; i < history.size() - 1; i++) {
            assertThat(history.get(i))
                    .as("Date at index %d should be >= date at index %d", i, i + 1)
                    .isAfterOrEqualTo(history.get(i + 1));
        }
    }

    /**
     * Feature: daily-task-completion-tracking, Property 5 (Edge Case): Empty Completion History
     * For any daily task with no completion records, querying the completion history
     * should return an empty list.
     * 
     * Validates: Requirements 3.5
     */
    @Property(tries = 100)
    @Label("Feature: daily-task-completion-tracking, Property 5: Empty Completion History Returns Empty List")
    void emptyCompletionHistoryReturnsEmptyList(
            @ForAll @NotBlank @Size(max = 50) String userId,
            @ForAll @NotBlank @Size(max = 50) String taskId,
            @ForAll @NotBlank @Size(max = 200) String description
    ) {
        TaskService service = createTestService();
        
        // Create a daily task without any completions
        DailyTaskRequest createRequest = new DailyTaskRequest(taskId, description, null, 0);
        service.createDailyTask(userId, createRequest);
        
        // Get completion history
        List<LocalDate> history = service.getCompletionHistory(userId, requireNonNull(taskId));
        
        // Verify empty list is returned
        assertThat(history).isEmpty();
    }
}
