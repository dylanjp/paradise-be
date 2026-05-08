package com.dylanjohnpratt.paradise.be.health.seed;

import com.dylanjohnpratt.paradise.be.health.model.HealthJournalEntry;
import com.dylanjohnpratt.paradise.be.health.model.HealthReminder;
import com.dylanjohnpratt.paradise.be.health.repository.HealthJournalEntryRepository;
import com.dylanjohnpratt.paradise.be.health.repository.HealthReminderRepository;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Development-only seeder that populates a handful of journal entries and
 * reminders for every existing user. Active only under the {@code dev-seed}
 * Spring profile — will NOT run in prod, test, or default boots.
 * <p>
 * Idempotent by existence check: entries that already exist on a date are
 * skipped, and reminders with a matching title are skipped.
 */
@Component
@Profile("dev-seed")
@Order(200)
public class HealthDevSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HealthDevSeedRunner.class);

    private final UserRepository userRepository;
    private final HealthJournalEntryRepository journalRepository;
    private final HealthReminderRepository reminderRepository;

    public HealthDevSeedRunner(
            UserRepository userRepository,
            HealthJournalEntryRepository journalRepository,
            HealthReminderRepository reminderRepository) {
        this.userRepository = userRepository;
        this.journalRepository = journalRepository;
        this.reminderRepository = reminderRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (User user : userRepository.findAll()) {
            seedJournal(user);
            seedReminders(user);
        }
        log.info("Dev seed sweep complete ({} users)", userRepository.count());
    }

    private void seedJournal(User user) {
        LocalDate today = LocalDate.now();
        seedJournalEntry(user, today.minusDays(2),
                new BigDecimal("182.40"), LocalTime.of(23, 10), LocalTime.of(7, 15),
                (short) 7, "Felt sharp after the 30-minute walk.");
        seedJournalEntry(user, today.minusDays(1),
                new BigDecimal("182.10"), LocalTime.of(23, 45), LocalTime.of(6, 55),
                (short) 5, "Slept short but deep.");
        seedJournalEntry(user, today,
                new BigDecimal("181.80"), LocalTime.of(23, 30), LocalTime.of(7, 0),
                (short) 8, "Strong focus today.");
    }

    private void seedJournalEntry(User user, LocalDate date, BigDecimal weight,
                                   LocalTime bed, LocalTime wake, Short energy, String thoughts) {
        if (journalRepository.findByUserIdAndEntryDate(user.getId(), date).isPresent()) {
            return;
        }
        HealthJournalEntry entry = new HealthJournalEntry();
        entry.setUserId(user.getId());
        entry.setEntryDate(date);
        entry.setWeightLbs(weight);
        entry.setBedTime(bed);
        entry.setWakeTime(wake);
        entry.setEnergy(energy);
        entry.setThoughts(thoughts);
        journalRepository.save(entry);
    }

    private void seedReminders(User user) {
        seedReminder(user, "Refill testosterone prescription",
                "Call pharmacy before Friday to restock.",
                LocalDateTime.now().plusDays(3).withHour(9).withMinute(0), false);
        seedReminder(user, "Drink 3L water today",
                "Aim to finish the large bottle twice.",
                LocalDateTime.now().withHour(21).withMinute(0), false);
        seedReminder(user, "Log blood pressure reading",
                "Morning and evening — add to the BP metric.",
                LocalDateTime.now().plusDays(1).withHour(7).withMinute(30), false);
    }

    private void seedReminder(User user, String title, String description,
                              LocalDateTime dueAt, boolean completed) {
        boolean alreadyExists = reminderRepository
                .findByUserIdOrderByDueAtAscCreatedAtAsc(user.getId())
                .stream()
                .anyMatch(r -> title.equals(r.getTitle()));
        if (alreadyExists) {
            return;
        }
        HealthReminder reminder = new HealthReminder();
        reminder.setUserId(user.getId());
        reminder.setTitle(title);
        reminder.setDescription(description);
        reminder.setDueAt(dueAt);
        reminder.setCompleted(completed);
        reminderRepository.save(reminder);
    }
}
