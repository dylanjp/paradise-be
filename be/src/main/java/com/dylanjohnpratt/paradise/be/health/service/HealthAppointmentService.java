package com.dylanjohnpratt.paradise.be.health.service;

import com.dylanjohnpratt.paradise.be.dto.HealthAppointmentPatchRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthAppointmentRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthAppointmentResponse;
import com.dylanjohnpratt.paradise.be.exception.HealthAccessDeniedException;
import com.dylanjohnpratt.paradise.be.exception.HealthNotFoundException;
import com.dylanjohnpratt.paradise.be.exception.HealthValidationException;
import com.dylanjohnpratt.paradise.be.health.model.HealthAppointment;
import com.dylanjohnpratt.paradise.be.health.repository.HealthAppointmentRepository;
import com.dylanjohnpratt.paradise.be.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Business logic for medical appointments (upcoming and past visits).
 */
@Service
public class HealthAppointmentService {

    private final HealthAppointmentRepository appointmentRepository;

    public HealthAppointmentService(HealthAppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public List<HealthAppointmentResponse> list(String userId, User currentUser) {
        checkAccess(userId, currentUser);
        return appointmentRepository.findByUserIdOrderByApptDateDesc(currentUser.getId()).stream()
                .map(HealthAppointmentResponse::from)
                .toList();
    }

    @Transactional
    public HealthAppointmentResponse create(String userId, HealthAppointmentRequest request, User currentUser) {
        checkAccess(userId, currentUser);
        if (request == null || request.doctor() == null || request.doctor().isBlank()) {
            throw new HealthValidationException("doctor is required");
        }
        if (request.apptDate() == null) {
            throw new HealthValidationException("apptDate is required");
        }
        if (request.type() == null) {
            throw new HealthValidationException("type is required");
        }
        HealthAppointment appointment = new HealthAppointment();
        appointment.setUserId(currentUser.getId());
        appointment.setDoctor(request.doctor());
        appointment.setSpecialty(request.specialty());
        appointment.setApptDate(request.apptDate());
        appointment.setType(request.type());
        appointment.setNotes(request.notes());
        HealthAppointment saved = appointmentRepository.save(appointment);
        return HealthAppointmentResponse.from(saved);
    }

    @Transactional
    public HealthAppointmentResponse patch(
            String userId,
            String appointmentId,
            HealthAppointmentPatchRequest request,
            User currentUser) {
        checkAccess(userId, currentUser);
        HealthAppointment appointment = Objects.requireNonNull(appointmentRepository
                .findByIdAndUserId(appointmentId, currentUser.getId())
                .orElseThrow(() -> new HealthNotFoundException("Appointment not found: " + appointmentId)));

        if (request != null) {
            if (request.doctor() != null) {
                if (request.doctor().isBlank()) {
                    throw new HealthValidationException("doctor must not be blank");
                }
                appointment.setDoctor(request.doctor());
            }
            if (request.specialty() != null) appointment.setSpecialty(request.specialty());
            if (request.apptDate() != null) appointment.setApptDate(request.apptDate());
            if (request.type() != null) appointment.setType(request.type());
            if (request.notes() != null) appointment.setNotes(request.notes());
        }

        HealthAppointment saved = appointmentRepository.save(appointment);
        return HealthAppointmentResponse.from(saved);
    }

    @Transactional
    public void delete(String userId, String appointmentId, User currentUser) {
        checkAccess(userId, currentUser);
        HealthAppointment appointment = Objects.requireNonNull(appointmentRepository
                .findByIdAndUserId(appointmentId, currentUser.getId())
                .orElseThrow(() -> new HealthNotFoundException("Appointment not found: " + appointmentId)));
        appointmentRepository.delete(appointment);
    }

    private void checkAccess(String userId, User currentUser) {
        if (!currentUser.getUsername().equals(userId)) {
            throw new HealthAccessDeniedException(
                    "Access denied: you can only access your own health data");
        }
    }
}
