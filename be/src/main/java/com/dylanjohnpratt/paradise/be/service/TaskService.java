package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.dto.*;
import com.dylanjohnpratt.paradise.be.exception.TaskNotFoundException;
import com.dylanjohnpratt.paradise.be.model.DailyTask;
import com.dylanjohnpratt.paradise.be.model.DailyTaskCompletion;
import com.dylanjohnpratt.paradise.be.model.TodoTask;
import com.dylanjohnpratt.paradise.be.repository.DailyTaskCompletionRepository;
import com.dylanjohnpratt.paradise.be.repository.DailyTaskRepository;
import com.dylanjohnpratt.paradise.be.repository.TodoTaskRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing todo and daily tasks.
 * Handles CRUD operations for two task types: todo tasks (persistent, category-based with
 * parent-child hierarchy) and daily tasks (resettable with completion history tracking).
 * Enforces user isolation — users can only access their own tasks. Also provides
 * analytics via completion history and "perfect days" calculation.
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

    private void checkUserAccess(String userId, String requestingUserId) {
        if (!userId.equals(requestingUserId)) {
            throw new TaskNotFoundException("Access denied");
        }
    }

    /**
     * Retrieves all tasks for a user. Returns todo tasks grouped by category
     * and daily tasks as a flat list.
     *
     * @param userId           the user whose tasks to retrieve
     * @param requestingUserId the ID of the user making the request (must match userId)
     * @return a {@link UserTasksResponse} containing grouped todo tasks and daily tasks
     * @throws TaskNotFoundException if the requesting user does not match the target user
     */
    public UserTasksResponse getAllTasksForUser(String userId, String requestingUserId) {
        checkUserAccess(userId, requestingUserId);
        List<TodoTask> userTodoTasks = todoTaskRepository.findByUserId(userId);
        List<DailyTask> userDailyTasks = dailyTaskRepository.findByUserId(userId);

        Map<String, List<TodoTask>> groupedTodoTasks = userTodoTasks.stream()
                .collect(Collectors.groupingBy(
                        task -> task.getCategory() != null ? task.getCategory() : "default",
                        Collectors.toList()
                ));

        return new UserTasksResponse(groupedTodoTasks, userDailyTasks);
    }

    /**
     * Creates a new todo task for the specified user.
     * The task is initialized with completed set to false and uses the provided
     * ID, description, category, order, and optional parentId.
     *
     * @param userId           the user to create the task for
     * @param request          the task creation request
     * @param requestingUserId the ID of the user making the request (must match userId)
     * @return the persisted {@link TodoTask}
     * @throws TaskNotFoundException if the requesting user does not match the target user
     */
    public TodoTask createTodoTask(String userId, TodoTaskRequest request, String requestingUserId) {
        checkUserAccess(userId, requestingUserId);

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
     * Creates a new daily task for the specified user.
     * The task is initialized with completed set to false and createdAt set to now.
     * Daily tasks reset to incomplete each midnight via the DailyTaskResetScheduler.
     *
     * @param userId           the user to create the task for
     * @param request          the task creation request
     * @param requestingUserId the ID of the user making the request (must match userId)
     * @return the persisted {@link DailyTask}
     * @throws TaskNotFoundException if the requesting user does not match the target user
     */
    public DailyTask createDailyTask(String userId, DailyTaskRequest request, String requestingUserId) {
        checkUserAccess(userId, requestingUserId);

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
     * Updates an existing todo task with partial field updates.
     * Only the fields provided in the request (description, completed, order, parentId) are modified.
     * Validates parent-child relationships to prevent circular references.
     *
     * @param userId           the task owner's user ID
     * @param taskId           the ID of the task to update
     * @param request          the update request with optional fields
     * @param requestingUserId the ID of the user making the request (must match userId)
     * @return the updated {@link TodoTask}
     * @throws TaskNotFoundException if the task does not exist or belongs to another user
     */
    public TodoTask updateTodoTask(String userId, @NonNull String taskId, TodoTaskRequest request, String requestingUserId) {
        checkUserAccess(userId, requestingUserId);

        TodoTask task = todoTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("todo task not found: " + taskId));

        if (!task.getUserId().equals(userId)) {
            throw new TaskNotFoundException("todo task not found: " + taskId);
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

        if (request.isParentIdProvided()) {
            String newParentId = request.getParentId();
            if (newParentId != null) {
                validateParentTask(userId, newParentId, taskId);
            }
            task.setParentId(newParentId);
        }

        return todoTaskRepository.save(task);
    }

    private void validateParentTask(String userId, String parentId, String taskId) {
        if (parentId.equals(taskId)) {
            throw new IllegalArgumentException("A task cannot be its own parent");
        }

        TodoTask parentTask = todoTaskRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Parent task not found: " + parentId));

        if (!parentTask.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Parent task not found: " + parentId);
        }
    }

    /**
     * Updates an existing daily task with partial field updates.
     * When completed is set to true, a completion record is created for today's date.
     * When completed is set to false, today's completion record is removed.
     * Runs in a transaction to ensure atomicity between the task update and completion record changes.
     *
     * @param userId           the task owner's user ID
     * @param taskId           the ID of the task to update
     * @param request          the update request with optional fields
     * @param requestingUserId the ID of the user making the request (must match userId)
     * @return the updated {@link DailyTask}
     * @throws TaskNotFoundException if the task does not exist or belongs to another user
     */
    @Transactional
    public DailyTask updateDailyTask(String userId, @NonNull String taskId, DailyTaskRequest request, String requestingUserId) {
        checkUserAccess(userId, requestingUserId);

        DailyTask task = dailyTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Daily task not found: " + taskId));

        if (!task.getUserId().equals(userId)) {
            throw new TaskNotFoundException("Daily task not found: " + taskId);
        }

        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getCompleted() != null) {
            if (request.getCompleted()) {
                createCompletionRecordIfNotExists(taskId, LocalDate.now());
            } else {
                deleteCompletionRecordForDate(taskId, LocalDate.now());
            }
            task.setCompleted(request.getCompleted());
        }
        if (request.getOrder() != null) {
            task.setOrder(request.getOrder());
        }

        return dailyTaskRepository.save(task);
    }

    private void createCompletionRecordIfNotExists(String taskId, LocalDate date) {
        Optional<DailyTaskCompletion> existing = dailyTaskCompletionRepository
                .findByDailyTaskIdAndCompletionDate(taskId, date);

        if (existing.isEmpty()) {
            DailyTaskCompletion completion = new DailyTaskCompletion(taskId, date);
            dailyTaskCompletionRepository.save(completion);
        }
    }

    private void deleteCompletionRecordForDate(String taskId, LocalDate date) {
        dailyTaskCompletionRepository.deleteByDailyTaskIdAndCompletionDate(taskId, date);
    }

    /**
     * Deletes a todo task. Any child tasks that reference this task as their parent
     * are un-nested (their parentId is set to null) rather than being cascade-deleted.
     * Runs in a transaction to ensure atomicity.
     *
     * @param userId           the task owner's user ID
     * @param taskId           the ID of the task to delete
     * @param requestingUserId the ID of the user making the request (must match userId)
     * @throws TaskNotFoundException if the task does not exist or belongs to another user
     */
    @Transactional
    public void deleteTodoTask(String userId, @NonNull String taskId, String requestingUserId) {
        checkUserAccess(userId, requestingUserId);

        TodoTask task = todoTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("todo task not found: " + taskId));

        if (!task.getUserId().equals(userId)) {
            throw new TaskNotFoundException("todo task not found: " + taskId);
        }

        // Unnest children: set parentId to null for all child tasks
        List<TodoTask> childTasks = todoTaskRepository.findByUserIdAndParentId(userId, taskId);
        for (TodoTask child : childTasks) {
            child.setParentId(null);
        }
        if (!childTasks.isEmpty()) {
            todoTaskRepository.saveAll(childTasks);
        }

        todoTaskRepository.delete(task);
    }

    /**
     * Deletes a daily task and all of its associated completion history records.
     * Runs in a transaction to ensure atomicity between the task deletion and
     * completion record cleanup.
     *
     * @param userId           the task owner's user ID
     * @param taskId           the ID of the task to delete
     * @param requestingUserId the ID of the user making the request (must match userId)
     * @throws TaskNotFoundException if the task does not exist or belongs to another user
     */
    @Transactional
    public void deleteDailyTask(String userId, @NonNull String taskId, String requestingUserId) {
        checkUserAccess(userId, requestingUserId);

        DailyTask task = dailyTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Daily task not found: " + taskId));

        if (!task.getUserId().equals(userId)) {
            throw new TaskNotFoundException("Daily task not found: " + taskId);
        }

        dailyTaskCompletionRepository.deleteByDailyTaskId(taskId);

        dailyTaskRepository.delete(task);
    }

    /**
     * Retrieves the completion history for a daily task as a list of dates when the task
     * was marked complete, sorted in descending order (most recent first).
     *
     * @param userId           the task owner's user ID
     * @param taskId           the ID of the daily task
     * @param requestingUserId the ID of the user making the request (must match userId)
     * @return list of completion dates in descending order
     * @throws TaskNotFoundException if the task does not exist or belongs to another user
     */
    public List<LocalDate> getCompletionHistory(String userId, @NonNull String taskId, String requestingUserId) {
        checkUserAccess(userId, requestingUserId);

        DailyTask task = dailyTaskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Daily task not found: " + taskId));

        if (!task.getUserId().equals(userId)) {
            throw new TaskNotFoundException("Daily task not found: " + taskId);
        }

        List<DailyTaskCompletion> completions = dailyTaskCompletionRepository
                .findByDailyTaskIdOrderByCompletionDateDesc(taskId);

        return completions.stream()
                .map(DailyTaskCompletion::getCompletionDate)
                .collect(Collectors.toList());
    }

    /**
     * Calculates "perfect days" for the user in a given year — dates on which the user
     * completed every daily task that existed at that time. A task is only considered
     * applicable on dates on or after its createdAt date. Returns results sorted in
     * descending order (most recent first).
     *
     * @param userId           the task owner's user ID
     * @param year             the year to query
     * @param requestingUserId the ID of the user making the request (must match userId)
     * @return list of perfect day dates in descending order, empty if no daily tasks exist
     * @throws TaskNotFoundException if the requesting user does not match the target user
     */
    public List<LocalDate> getPerfectDays(String userId, int year, String requestingUserId) {
        checkUserAccess(userId, requestingUserId);

        List<DailyTask> userDailyTasks = dailyTaskRepository.findByUserId(userId);

        if (userDailyTasks.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDate startDate = LocalDate.of(year, 1, 1);
        LocalDate endDate;
        LocalDate today = LocalDate.now();
        if (year == today.getYear()) {
            endDate = today;
        } else {
            endDate = LocalDate.of(year, 12, 31);
        }

        List<String> taskIds = userDailyTasks.stream()
                .map(DailyTask::getId)
                .collect(Collectors.toList());

        List<DailyTaskCompletion> completions = dailyTaskCompletionRepository
                .findByDailyTaskIdInAndCompletionDateBetween(taskIds, startDate, endDate);

        if (completions.isEmpty()) {
            return Collections.emptyList();
        }

        Map<LocalDate, Set<String>> completionsByDate = completions.stream()
                .collect(Collectors.groupingBy(
                        DailyTaskCompletion::getCompletionDate,
                        Collectors.mapping(DailyTaskCompletion::getDailyTaskId, Collectors.toSet())
                ));

        List<LocalDate> perfectDays = new ArrayList<>();

        for (Map.Entry<LocalDate, Set<String>> entry : completionsByDate.entrySet()) {
            LocalDate date = entry.getKey();
            Set<String> completedTaskIds = entry.getValue();

            Set<String> applicableTaskIds = userDailyTasks.stream()
                    .filter(task -> !task.getCreatedAt().toLocalDate().isAfter(date))
                    .map(DailyTask::getId)
                    .collect(Collectors.toSet());

            if (applicableTaskIds.isEmpty()) {
                continue;
            }

            if (completedTaskIds.containsAll(applicableTaskIds)) {
                perfectDays.add(date);
            }
        }

        perfectDays.sort(Comparator.reverseOrder());

        return perfectDays;
    }
}
