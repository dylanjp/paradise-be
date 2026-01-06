package com.dylanjohnpratt.paradise.be.dto;

import com.dylanjohnpratt.paradise.be.model.DailyTask;
import com.dylanjohnpratt.paradise.be.model.TodoTask;

import java.util.List;
import java.util.Map;

/**
 * Response DTO containing all tasks for a user.
 * TODO tasks are organized by category, Daily tasks are a flat list.
 */
public class UserTasksResponse {
    
    private Map<String, List<TodoTask>> todoTasks;  // keyed by category
    private List<DailyTask> dailyTasks;

    public UserTasksResponse() {
    }

    public UserTasksResponse(Map<String, List<TodoTask>> todoTasks, List<DailyTask> dailyTasks) {
        this.todoTasks = todoTasks;
        this.dailyTasks = dailyTasks;
    }

    public Map<String, List<TodoTask>> getTodoTasks() {
        return todoTasks;
    }

    public void setTodoTasks(Map<String, List<TodoTask>> todoTasks) {
        this.todoTasks = todoTasks;
    }

    public List<DailyTask> getDailyTasks() {
        return dailyTasks;
    }

    public void setDailyTasks(List<DailyTask> dailyTasks) {
        this.dailyTasks = dailyTasks;
    }
}
