package com.dylanjohnpratt.paradise.be.health.repository;

import com.dylanjohnpratt.paradise.be.health.model.HealthMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link HealthMetric}. Seeded metrics share the same slug across users;
 * user-created metrics have a null slug.
 */
@Repository
public interface HealthMetricRepository extends JpaRepository<HealthMetric, String> {

    List<HealthMetric> findByUserIdOrderByCreatedAtAsc(Long userId);

    Optional<HealthMetric> findByIdAndUserId(String id, Long userId);

    Optional<HealthMetric> findByUserIdAndSlug(Long userId, String slug);

    boolean existsByUserIdAndSlug(Long userId, String slug);
}
