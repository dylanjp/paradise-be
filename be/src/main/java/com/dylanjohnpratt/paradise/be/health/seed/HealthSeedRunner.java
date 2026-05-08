package com.dylanjohnpratt.paradise.be.health.seed;

import com.dylanjohnpratt.paradise.be.health.service.HealthJournalService;
import com.dylanjohnpratt.paradise.be.model.User;
import com.dylanjohnpratt.paradise.be.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Startup sweep: ensures every existing user has the canonical seeded health
 * metrics. Idempotent — users who were already seeded get no-ops.
 * <p>
 * Runs after {@link com.dylanjohnpratt.paradise.be.config.DataInitializer}
 * (default order) by virtue of a later {@link Order}; the admin user is
 * created in DataInitializer and seeded here.
 */
@Component
@Order(100)
public class HealthSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HealthSeedRunner.class);

    private final UserRepository userRepository;
    private final HealthMetricSeeder metricSeeder;
    private final HealthJournalService journalService;

    public HealthSeedRunner(
            UserRepository userRepository,
            HealthMetricSeeder metricSeeder,
            HealthJournalService journalService) {
        this.userRepository = userRepository;
        this.metricSeeder = metricSeeder;
        this.journalService = journalService;
    }

    @Override
    public void run(String... args) {
        try {
            for (User user : userRepository.findAll()) {
                metricSeeder.seedFor(user.getId());
                // Backfill journal-derived metrics (mood, weight) from existing entries.
                // Idempotent — replays journal data into the metric series each boot.
                journalService.resyncDerivedMetricsForUser(user.getId());
            }
            log.info("Health metric seed sweep complete");
        } catch (Exception e) {
            // Don't block startup if the DB is temporarily unreachable — DataInitializer already
            // handles that case. We'll try again on next boot / user create.
            log.warn("Health metric seed sweep skipped: {}", e.toString());
        }
    }
}
