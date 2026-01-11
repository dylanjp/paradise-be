package com.dylanjohnpratt.paradise.be.config;

import com.dylanjohnpratt.paradise.be.model.DailyTask;
import com.dylanjohnpratt.paradise.be.model.DailyTaskCompletion;
import com.dylanjohnpratt.paradise.be.repository.DailyTaskCompletionRepository;
import com.dylanjohnpratt.paradise.be.repository.DailyTaskRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for DailyTaskResetScheduler.
 * Uses jqwik to verify correctness properties across many generated inputs.
 */
class DailyTaskResetSchedulerPropertyTest {

    /**
     * In-memory implementation of DailyTaskRepository for testing.
     */
    private static class InMemoryDailyTaskRepository implements DailyTaskRepository {
        private final Map<String, DailyTask> tasks = new HashMap<>();

        @Override
        public DailyTask save(DailyTask task) {
            tasks.put(task.getId(), task);
            return task;
        }

        @Override
        public Optional<DailyTask> findById(String id) {
            return Optional.ofNullable(tasks.get(id));
        }

        @Override
        public List<DailyTask> findByUserId(String userId) {
            return tasks.values().stream()
                    .filter(t -> t.getUserId().equals(userId))
                    .toList();
        }

        @Override
        public void delete(DailyTask task) {
            tasks.remove(task.getId());
        }

        @Override
        public List<DailyTask> findAll() {
            return new ArrayList<>(tasks.values());
        }

        @Override
        public <S extends DailyTask> List<S> saveAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S entity : entities) {
                tasks.put(entity.getId(), entity);
                result.add(entity);
            }
            return result;
        }


        // Unused methods for testing
        @Override public List<DailyTask> findAllById(Iterable<String> ids) { return null; }
        @Override public boolean existsById(String s) { return false; }
        @Override public long count() { return 0; }
        @Override public void deleteById(String s) { }
        @Override public void deleteAll(Iterable<? extends DailyTask> entities) { }
        @Override public void deleteAllById(Iterable<? extends String> ids) { }
        @Override public void deleteAll() { }
        @Override public void flush() { }
        @Override public <S extends DailyTask> S saveAndFlush(S entity) { return null; }
        @Override public <S extends DailyTask> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<DailyTask> entities) { }
        @Override public void deleteAllByIdInBatch(Iterable<String> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override public DailyTask getOne(String s) { return null; }
        @Override public DailyTask getById(String s) { return null; }
        @Override public DailyTask getReferenceById(String s) { return null; }
        @Override public <S extends DailyTask> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends DailyTask> List<S> findAll(org.springframework.data.domain.Example<S> example) { return null; }
        @Override public <S extends DailyTask> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return null; }
        @Override public <S extends DailyTask> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends DailyTask> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends DailyTask> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends DailyTask, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<DailyTask> findAll(org.springframework.data.domain.Sort sort) { return null; }
        @Override public org.springframework.data.domain.Page<DailyTask> findAll(org.springframework.data.domain.Pageable pageable) { return null; }
    }

    /**
     * In-memory implementation of DailyTaskCompletionRepository for testing.
     */
    private static class InMemoryDailyTaskCompletionRepository implements DailyTaskCompletionRepository {
        private final Map<String, DailyTaskCompletion> completions = new HashMap<>();
        private int idCounter = 0;

        @Override
        public DailyTaskCompletion save(DailyTaskCompletion completion) {
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
        public Optional<DailyTaskCompletion> findById(String id) {
            return Optional.ofNullable(completions.get(id));
        }

        @Override
        public List<DailyTaskCompletion> findAll() {
            return new ArrayList<>(completions.values());
        }

        // Unused methods for testing
        @Override public List<DailyTaskCompletion> findAllById(Iterable<String> ids) { return null; }
        @Override public <S extends DailyTaskCompletion> List<S> saveAll(Iterable<S> entities) { return null; }
        @Override public boolean existsById(String s) { return false; }
        @Override public long count() { return 0; }
        @Override public void deleteById(String s) { }
        @Override public void delete(DailyTaskCompletion entity) { completions.remove(entity.getId()); }
        @Override public void deleteAll(Iterable<? extends DailyTaskCompletion> entities) { }
        @Override public void deleteAllById(Iterable<? extends String> ids) { }
        @Override public void deleteAll() { }
        @Override public void flush() { }
        @Override public <S extends DailyTaskCompletion> S saveAndFlush(S entity) { return null; }
        @Override public <S extends DailyTaskCompletion> List<S> saveAllAndFlush(Iterable<S> entities) { return null; }
        @Override public void deleteAllInBatch(Iterable<DailyTaskCompletion> entities) { }
        @Override public void deleteAllByIdInBatch(Iterable<String> ids) { }
        @Override public void deleteAllInBatch() { }
        @Override public DailyTaskCompletion getOne(String s) { return null; }
        @Override public DailyTaskCompletion getById(String s) { return null; }
        @Override public DailyTaskCompletion getReferenceById(String s) { return null; }
        @Override public <S extends DailyTaskCompletion> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends DailyTaskCompletion> List<S> findAll(org.springframework.data.domain.Example<S> example) { return null; }
        @Override public <S extends DailyTaskCompletion> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return null; }
        @Override public <S extends DailyTaskCompletion> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Pageable pageable) { return null; }
        @Override public <S extends DailyTaskCompletion> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends DailyTaskCompletion> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends DailyTaskCompletion, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
        @Override public List<DailyTaskCompletion> findAll(org.springframework.data.domain.Sort sort) { return null; }
        @Override public org.springframework.data.domain.Page<DailyTaskCompletion> findAll(org.springframework.data.domain.Pageable pageable) { return null; }
    }


    /**
     * Feature: daily-task-completion-tracking, Property 1: Reset Sets All Tasks to Incomplete
     * For any set of daily tasks with various completion states (some true, some false),
     * when the midnight reset operation executes, all tasks should have their completed
     * status set to false.
     * 
     * Validates: Requirements 1.1
     */
    @Property(tries = 100)
    @Label("Feature: daily-task-completion-tracking, Property 1: Reset Sets All Tasks to Incomplete")
    void resetSetsAllTasksToIncomplete(
            @ForAll @Size(min = 1, max = 20) List<@NotBlank @Size(max = 50) String> taskIds,
            @ForAll @Size(min = 1, max = 20) List<Boolean> completedStates
    ) {
        // Ensure we have matching sizes
        int taskCount = Math.min(taskIds.size(), completedStates.size());
        if (taskCount == 0) return;
        
        InMemoryDailyTaskRepository dailyTaskRepository = new InMemoryDailyTaskRepository();
        DailyTaskResetScheduler scheduler = new DailyTaskResetScheduler(dailyTaskRepository);
        
        // Create tasks with various completion states
        Set<String> uniqueIds = new HashSet<>();
        for (int i = 0; i < taskCount; i++) {
            String taskId = taskIds.get(i) + "-" + i; // Ensure unique IDs
            if (uniqueIds.contains(taskId)) continue;
            uniqueIds.add(taskId);
            
            DailyTask task = new DailyTask(
                    taskId,
                    "user-" + i,
                    "Task " + i,
                    completedStates.get(i),
                    i,
                    LocalDateTime.now()
            );
            dailyTaskRepository.save(task);
        }
        
        // Execute the reset
        scheduler.resetDailyTasks();
        
        // Verify all tasks are now incomplete
        List<DailyTask> allTasks = dailyTaskRepository.findAll();
        for (DailyTask task : allTasks) {
            assertThat(task.isCompleted())
                    .as("Task %s should be incomplete after reset", task.getId())
                    .isFalse();
        }
    }

    /**
     * Feature: daily-task-completion-tracking, Property 2: Reset Preserves Completion Records
     * For any set of daily tasks with associated completion records, when the midnight
     * reset operation executes, all completion records should remain unchanged in the database.
     * 
     * Validates: Requirements 1.2
     */
    @Property(tries = 100)
    @Label("Feature: daily-task-completion-tracking, Property 2: Reset Preserves Completion Records")
    void resetPreservesCompletionRecords(
            @ForAll @Size(min = 1, max = 10) List<@NotBlank @Size(max = 50) String> taskIds,
            @ForAll @Size(min = 1, max = 30) List<Integer> dayOffsets
    ) {
        InMemoryDailyTaskRepository dailyTaskRepository = new InMemoryDailyTaskRepository();
        InMemoryDailyTaskCompletionRepository completionRepository = new InMemoryDailyTaskCompletionRepository();
        DailyTaskResetScheduler scheduler = new DailyTaskResetScheduler(dailyTaskRepository);
        
        // Create tasks
        Set<String> uniqueTaskIds = new HashSet<>();
        for (int i = 0; i < taskIds.size(); i++) {
            String taskId = taskIds.get(i) + "-" + i;
            if (uniqueTaskIds.contains(taskId)) continue;
            uniqueTaskIds.add(taskId);
            
            DailyTask task = new DailyTask(
                    taskId,
                    "user-" + i,
                    "Task " + i,
                    true,
                    i,
                    LocalDateTime.now()
            );
            dailyTaskRepository.save(task);
        }
        
        // Create completion records for tasks
        LocalDate today = LocalDate.now();
        Set<String> completionKeys = new HashSet<>();
        List<DailyTaskCompletion> originalCompletions = new ArrayList<>();
        
        for (int i = 0; i < dayOffsets.size(); i++) {
            String taskId = taskIds.get(i % taskIds.size()) + "-" + (i % taskIds.size());
            LocalDate completionDate = today.minusDays(Math.abs(dayOffsets.get(i) % 365));
            String key = taskId + "-" + completionDate;
            
            if (completionKeys.contains(key)) continue;
            completionKeys.add(key);
            
            DailyTaskCompletion completion = new DailyTaskCompletion(taskId, completionDate);
            completionRepository.save(completion);
            originalCompletions.add(completion);
        }
        
        // Record the state before reset
        List<DailyTaskCompletion> completionsBefore = new ArrayList<>(completionRepository.findAll());
        int countBefore = completionsBefore.size();
        
        // Execute the reset
        scheduler.resetDailyTasks();
        
        // Verify completion records are unchanged
        List<DailyTaskCompletion> completionsAfter = completionRepository.findAll();
        
        assertThat(completionsAfter)
                .as("Completion record count should remain unchanged after reset")
                .hasSize(countBefore);
        
        // Verify each original completion still exists with same data
        for (DailyTaskCompletion original : completionsBefore) {
            Optional<DailyTaskCompletion> found = completionRepository.findByDailyTaskIdAndCompletionDate(
                    original.getDailyTaskId(), original.getCompletionDate());
            
            assertThat(found)
                    .as("Completion record for task %s on %s should still exist", 
                            original.getDailyTaskId(), original.getCompletionDate())
                    .isPresent();
            
            assertThat(found.get().getDailyTaskId())
                    .isEqualTo(original.getDailyTaskId());
            assertThat(found.get().getCompletionDate())
                    .isEqualTo(original.getCompletionDate());
        }
    }
}
