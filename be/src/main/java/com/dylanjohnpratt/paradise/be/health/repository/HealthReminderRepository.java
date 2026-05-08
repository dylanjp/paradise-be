package com.dylanjohnpratt.paradise.be.health.repository;

import com.dylanjohnpratt.paradise.be.health.model.HealthReminder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link HealthReminder}.
 */
@Repository
public interface HealthReminderRepository extends JpaRepository<HealthReminder, String> {

    List<HealthReminder> findByUserIdOrderByDueAtAscCreatedAtAsc(Long userId);

    Optional<HealthReminder> findByIdAndUserId(String id, Long userId);
}
