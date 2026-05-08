package com.dylanjohnpratt.paradise.be.health.service;

import com.dylanjohnpratt.paradise.be.dto.HealthReminderPatchRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthReminderRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthReminderResponse;
import com.dylanjohnpratt.paradise.be.exception.HealthAccessDeniedException;
import com.dylanjohnpratt.paradise.be.exception.HealthNotFoundException;
import com.dylanjohnpratt.paradise.be.exception.HealthValidationException;
import com.dylanjohnpratt.paradise.be.health.model.HealthReminder;
import com.dylanjohnpratt.paradise.be.health.repository.HealthReminderRepository;
import com.dylanjohnpratt.paradise.be.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Business logic for user-owned health reminders.
 */
@Service
public class HealthReminderService {

    private final HealthReminderRepository reminderRepository;

    public HealthReminderService(HealthReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    @Transactional(readOnly = true)
    public List<HealthReminderResponse> list(String userId, User currentUser) {
        checkAccess(userId, currentUser);
        return reminderRepository.findByUserIdOrderByDueAtAscCreatedAtAsc(currentUser.getId()).stream()
                .map(HealthReminderResponse::from)
                .toList();
    }

    @Transactional
    public HealthReminderResponse create(String userId, HealthReminderRequest request, User currentUser) {
        checkAccess(userId, currentUser);
        if (request == null || request.title() == null || request.title().isBlank()) {
            throw new HealthValidationException("title is required");
        }
        HealthReminder reminder = new HealthReminder();
        reminder.setUserId(currentUser.getId());
        reminder.setTitle(request.title());
        reminder.setDescription(request.description());
        reminder.setDueAt(request.dueAt());
        reminder.setCompleted(Boolean.TRUE.equals(request.completed()));
        HealthReminder saved = reminderRepository.save(reminder);
        return HealthReminderResponse.from(saved);
    }

    @Transactional
    public HealthReminderResponse patch(
            String userId,
            String reminderId,
            HealthReminderPatchRequest request,
            User currentUser) {
        checkAccess(userId, currentUser);
        HealthReminder reminder = Objects.requireNonNull(reminderRepository
                .findByIdAndUserId(reminderId, currentUser.getId())
                .orElseThrow(() -> new HealthNotFoundException("Reminder not found: " + reminderId)));

        if (request != null) {
            if (request.title() != null) {
                if (request.title().isBlank()) {
                    throw new HealthValidationException("title must not be blank");
                }
                reminder.setTitle(request.title());
            }
            if (request.description() != null) reminder.setDescription(request.description());
            if (request.dueAt() != null) reminder.setDueAt(request.dueAt());
            if (request.completed() != null) reminder.setCompleted(request.completed());
        }

        HealthReminder saved = reminderRepository.save(reminder);
        return HealthReminderResponse.from(saved);
    }

    @Transactional
    public void delete(String userId, String reminderId, User currentUser) {
        checkAccess(userId, currentUser);
        HealthReminder reminder = Objects.requireNonNull(reminderRepository
                .findByIdAndUserId(reminderId, currentUser.getId())
                .orElseThrow(() -> new HealthNotFoundException("Reminder not found: " + reminderId)));
        reminderRepository.delete(reminder);
    }

    private void checkAccess(String userId, User currentUser) {
        if (!currentUser.getUsername().equals(userId)) {
            throw new HealthAccessDeniedException(
                    "Access denied: you can only access your own health data");
        }
    }
}
