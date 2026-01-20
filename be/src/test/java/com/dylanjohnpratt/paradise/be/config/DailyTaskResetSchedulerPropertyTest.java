package com.dylanjohnpratt.paradise.be.config;

import com.dylanjohnpratt.paradise.be.model.DailyTask;
import com.dylanjohnpratt.paradise.be.model.DailyTaskCompletion;
import com.dylanjohnpratt.paradise.be.repository.DailyTaskCompletionRepository;
import com.dylanjohnpratt.paradise.be.repository.DailyTaskRepository;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.Size;

import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static java.util.Objects.requireNonNull;
import java.util.Collection;

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

        @Override
        @NonNull
        public List<DailyTask> findAll() {
            return new ArrayList<>(tasks.values());
        }

        @Override
        @NonNull
        public <S extends DailyTask> List<S> saveAll(@NonNull Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            for (S entity : entities) {
                tasks.put(entity.getId(), entity);
                result.add(entity);
            }
            return result;
        }


        // Unused methods for testing
        @Override @NonNull public List<DailyTask> findAllById(@NonNull Iterable<String> ids) { return requireNonNull(List.of()); }
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

        @Override
        public List<DailyTaskCompletion> findByDailyTaskIdIn(Collection<String> dailyTaskIds) {
            return completions.values().stream()
                    .filter(c -> dailyTaskIds.contains(c.getDailyTaskId()))
                    .toList();
        }

        @Override
        public List<DailyTaskCompletion> findByDailyTaskIdInAndCompletionDateBetween(
                Collection<String> dailyTaskIds, LocalDate startDate, LocalDate endDate) {
            return completions.values().stream()
                    .filter(c -> dailyTaskIds.contains(c.getDailyTaskId()))
                    .filter(c -> !c.getCompletionDate().isBefore(startDate) && !c.getCompletionDate().isAfter(endDate))
                    .toList();
        }

        @Override
        @NonNull
        public List<DailyTaskCompletion> findAll() {
            return new ArrayList<>(completions.values());
        }

        // Unused methods for testing
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
