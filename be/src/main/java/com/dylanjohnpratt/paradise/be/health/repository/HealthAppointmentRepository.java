package com.dylanjohnpratt.paradise.be.health.repository;

import com.dylanjohnpratt.paradise.be.health.model.HealthAppointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link HealthAppointment}.
 */
@Repository
public interface HealthAppointmentRepository extends JpaRepository<HealthAppointment, String> {

    List<HealthAppointment> findByUserIdOrderByApptDateDesc(Long userId);

    Optional<HealthAppointment> findByIdAndUserId(String id, Long userId);
}
