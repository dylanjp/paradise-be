package com.dylanjohnpratt.paradise.be.health.service;

import com.dylanjohnpratt.paradise.be.dto.HealthJournalEntryRequest;
import com.dylanjohnpratt.paradise.be.dto.HealthJournalEntryResponse;
import com.dylanjohnpratt.paradise.be.exception.HealthAccessDeniedException;
import com.dylanjohnpratt.paradise.be.exception.HealthNotFoundException;
import com.dylanjohnpratt.paradise.be.exception.HealthValidationException;
import com.dylanjohnpratt.paradise.be.health.model.HealthJournalEntry;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetric;
import com.dylanjohnpratt.paradise.be.health.repository.HealthJournalEntryRepository;
import com.dylanjohnpratt.paradise.be.health.repository.HealthMetricRepository;
import com.dylanjohnpratt.paradise.be.model.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Business logic for the health journal resource. Entries are upserted by the
 * unique pair (userId, entryDate); POSTing twice on the same date overwrites
 * the first entry.
 */
@Service
public class HealthJournalService {

    private static final String MOOD_METRIC_SLUG = "mood";
    private static final String WEIGHT_METRIC_SLUG = "weight";

    private final HealthJournalEntryRepository journalRepository;
    private final HealthMetricRepository metricRepository;

    public HealthJournalService(
            HealthJournalEntryRepository journalRepository,
            HealthMetricRepository metricRepository) {
        this.journalRepository = journalRepository;
        this.metricRepository = metricRepository;
    }

    /**
     * Lists every journal entry owned by {@code userId}, newest first.
     */
    @Transactional(readOnly = true)
    public List<HealthJournalEntryResponse> list(String userId, User currentUser) {
        checkAccess(userId, currentUser);
        return journalRepository.findByUserIdOrderByEntryDateDesc(currentUser.getId()).stream()
                .map(HealthJournalEntryResponse::from)
                .toList();
    }

    /**
     * Upserts a journal entry on the pair (userId, entryDate).
     */
    @Transactional
    public HealthJournalEntryResponse upsert(String userId, HealthJournalEntryRequest request, User currentUser) {
        checkAccess(userId, currentUser);
        if (request == null || request.date() == null) {
            throw new HealthValidationException("date is required");
        }
        HealthJournalEntry entry = journalRepository
                .findByUserIdAndEntryDate(currentUser.getId(), request.date())
                .orElseGet(() -> {
                    HealthJournalEntry fresh = new HealthJournalEntry();
                    fresh.setUserId(currentUser.getId());
                    fresh.setEntryDate(request.date());
                    return fresh;
                });
        entry.setWeightLbs(request.weightLbs());
        entry.setBedTime(request.bedTime());
        entry.setWakeTime(request.wakeTime());
        entry.setEnergy(request.energy());
        entry.setMood(request.mood());
        entry.setThoughts(request.thoughts());
        HealthJournalEntry saved = journalRepository.save(entry);
        Long uid = currentUser.getId();
        syncMetricPoint(uid, saved.getEntryDate(), MOOD_METRIC_SLUG,
                saved.getMood() == null ? null : BigDecimal.valueOf(saved.getMood()));
        syncMetricPoint(uid, saved.getEntryDate(), WEIGHT_METRIC_SLUG, saved.getWeightLbs());
        return HealthJournalEntryResponse.from(saved);
    }

    /**
     * Deletes every journal entry for {@code userId}.
     */
    @Transactional
    public void deleteAll(String userId, User currentUser) {
        checkAccess(userId, currentUser);
        journalRepository.deleteByUserId(currentUser.getId());
        clearMetricPoints(currentUser.getId(), MOOD_METRIC_SLUG);
        clearMetricPoints(currentUser.getId(), WEIGHT_METRIC_SLUG);
    }

    /**
     * Rebuilds the user's mood and weight metric series from their journal
     * entries: clears the existing series, then re-applies one data point per
     * entry in chronological order. Idempotent — calling repeatedly converges
     * to the same state. Used by the startup seed sweep so newly added derived
     * metrics (weight, mood) backfill from pre-existing journal data.
     */
    @Transactional
    public void resyncDerivedMetricsForUser(Long userId) {
        if (userId == null) {
            return;
        }
        clearMetricPoints(userId, MOOD_METRIC_SLUG);
        clearMetricPoints(userId, WEIGHT_METRIC_SLUG);
        List<HealthJournalEntry> entries = journalRepository
                .findByUserIdOrderByEntryDateDesc(userId);
        for (HealthJournalEntry entry : entries) {
            if (entry.getMood() != null) {
                syncMetricPoint(userId, entry.getEntryDate(), MOOD_METRIC_SLUG,
                        BigDecimal.valueOf(entry.getMood()));
            }
            if (entry.getWeightLbs() != null) {
                syncMetricPoint(userId, entry.getEntryDate(), WEIGHT_METRIC_SLUG,
                        entry.getWeightLbs());
            }
        }
    }

    /**
     * Deletes a single journal entry by id, 404 if it doesn't belong to the user.
     */
    @Transactional
    public void delete(String userId, String entryId, User currentUser) {
        checkAccess(userId, currentUser);
        HealthJournalEntry entry = Objects.requireNonNull(journalRepository
                .findByIdAndUserId(entryId, currentUser.getId())
                .orElseThrow(() -> new HealthNotFoundException("Journal entry not found: " + entryId)));
        LocalDate entryDate = entry.getEntryDate();
        journalRepository.delete(entry);
        Long uid = currentUser.getId();
        syncMetricPoint(uid, entryDate, MOOD_METRIC_SLUG, null);
        syncMetricPoint(uid, entryDate, WEIGHT_METRIC_SLUG, null);
    }

    /**
     * Streams all journal entries for {@code userId} as CSV to the given output stream.
     * Header row: {@code date,weightLbs,bedTime,wakeTime,energy,thoughts}.
     */
    @Transactional(readOnly = true)
    public void exportCsv(String userId, OutputStream outputStream, User currentUser) {
        checkAccess(userId, currentUser);
        List<HealthJournalEntry> entries = journalRepository
                .findByUserIdOrderByEntryDateDesc(currentUser.getId());
        Writer writer = new java.io.OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        PrintWriter out = new PrintWriter(writer);
        out.println("date,weightLbs,bedTime,wakeTime,energy,mood,thoughts");
        DateTimeFormatter dateFmt = DateTimeFormatter.ISO_LOCAL_DATE;
        DateTimeFormatter timeFmt = DateTimeFormatter.ISO_LOCAL_TIME;
        for (HealthJournalEntry entry : entries) {
            out.println(String.join(",",
                    csvField(entry.getEntryDate() == null ? "" : dateFmt.format(entry.getEntryDate())),
                    csvField(entry.getWeightLbs() == null ? "" : entry.getWeightLbs().toPlainString()),
                    csvField(entry.getBedTime() == null ? "" : timeFmt.format(entry.getBedTime())),
                    csvField(entry.getWakeTime() == null ? "" : timeFmt.format(entry.getWakeTime())),
                    csvField(entry.getEnergy() == null ? "" : entry.getEnergy().toString()),
                    csvField(entry.getMood() == null ? "" : entry.getMood().toString()),
                    csvField(entry.getThoughts() == null ? "" : entry.getThoughts())
            ));
        }
        out.flush();
    }

    /**
     * Upserts a single-series data point on the user's seeded metric identified
     * by {@code slug}, keyed by the journal entry date string. If a point
     * already exists for that date its value is replaced; if {@code value} is
     * null, the point is removed. No-ops if the user has no metric for that
     * slug. Used to keep the Mood and Weight charts in sync with the journal.
     */
    private void syncMetricPoint(Long userId, LocalDate entryDate, String slug, BigDecimal value) {
        Optional<HealthMetric> metricOpt = metricRepository.findByUserIdAndSlug(userId, slug);
        if (metricOpt.isEmpty()) {
            return;
        }
        HealthMetric metric = metricOpt.get();
        String dateLabel = entryDate.toString();

        List<String> labels = metric.getLabels() == null
                ? new ArrayList<>()
                : new ArrayList<>(metric.getLabels());
        List<BigDecimal> data = metric.getData() == null
                ? new ArrayList<>()
                : new ArrayList<>(metric.getData());

        int idx = labels.indexOf(dateLabel);

        if (value == null) {
            if (idx >= 0) {
                labels.remove(idx);
                if (idx < data.size()) {
                    data.remove(idx);
                }
            }
        } else {
            if (idx >= 0) {
                if (idx < data.size()) {
                    data.set(idx, value);
                } else {
                    data.add(value);
                }
            } else {
                labels.add(dateLabel);
                data.add(value);
            }
        }

        metric.setLabels(labels);
        metric.setData(data);
        metricRepository.save(metric);
    }

    private void clearMetricPoints(Long userId, String slug) {
        Optional<HealthMetric> metricOpt = metricRepository.findByUserIdAndSlug(userId, slug);
        if (metricOpt.isEmpty()) {
            return;
        }
        HealthMetric metric = metricOpt.get();
        metric.setLabels(new ArrayList<>());
        metric.setData(new ArrayList<>());
        metricRepository.save(metric);
    }

    private void checkAccess(String userId, User currentUser) {
        if (!currentUser.getUsername().equals(userId)) {
            throw new HealthAccessDeniedException(
                    "Access denied: you can only access your own health data");
        }
    }

    /**
     * RFC 4180-style CSV escaping: quote if the field contains a comma, quote,
     * newline, or carriage return; double-up embedded quotes.
     */
    static String csvField(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
