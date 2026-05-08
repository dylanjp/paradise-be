package com.dylanjohnpratt.paradise.be.health.repository;

import com.dylanjohnpratt.paradise.be.health.model.HealthDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link HealthDocument}. File bytes live on disk under
 * {@code health.storage.documents}; this table holds only metadata.
 */
@Repository
public interface HealthDocumentRepository extends JpaRepository<HealthDocument, String> {

    List<HealthDocument> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<HealthDocument> findByIdAndUserId(String id, Long userId);
}
