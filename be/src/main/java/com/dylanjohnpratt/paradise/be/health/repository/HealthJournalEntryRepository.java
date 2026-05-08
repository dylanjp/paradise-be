package com.dylanjohnpratt.paradise.be.health.repository;

import com.dylanjohnpratt.paradise.be.health.model.HealthJournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link HealthJournalEntry}. Entries are upserted by the unique
 * pair (user_id, entry_date).
 */
@Repository
public interface HealthJournalEntryRepository extends JpaRepository<HealthJournalEntry, String> {

    List<HealthJournalEntry> findByUserIdOrderByEntryDateDesc(Long userId);

    Optional<HealthJournalEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);

    Optional<HealthJournalEntry> findByIdAndUserId(String id, Long userId);

    void deleteByUserId(Long userId);

    void deleteByIdAndUserId(String id, Long userId);
}
