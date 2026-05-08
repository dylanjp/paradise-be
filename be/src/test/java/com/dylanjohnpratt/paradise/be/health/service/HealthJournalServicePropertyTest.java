package com.dylanjohnpratt.paradise.be.health.service;

import com.dylanjohnpratt.paradise.be.dto.HealthJournalEntryRequest;
import com.dylanjohnpratt.paradise.be.exception.HealthAccessDeniedException;
import com.dylanjohnpratt.paradise.be.health.model.HealthJournalEntry;
import com.dylanjohnpratt.paradise.be.health.model.HealthMetric;
import com.dylanjohnpratt.paradise.be.health.repository.HealthJournalEntryRepository;
import com.dylanjohnpratt.paradise.be.health.repository.HealthMetricRepository;
import com.dylanjohnpratt.paradise.be.model.User;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests for {@link HealthJournalService}. These pin two invariants:
 * <ol>
 *   <li><b>Upsert by (userId, entryDate)</b> — for any sequence of POSTs, the total
 *       row count equals the number of distinct entry dates, and the latest POST
 *       for each date wins.</li>
 *   <li><b>Access isolation</b> — any mismatch between the authenticated user's
 *       username and the path {@code userId} raises
 *       {@link HealthAccessDeniedException}, regardless of role.</li>
 * </ol>
 */
class HealthJournalServicePropertyTest {

    private static final Long OWNER_ID = 42L;
    private static final String OWNER_USERNAME = "owner";

    @Property(tries = 100)
    void upsert_rowCountEqualsDistinctDates_andLatestWins(
            @ForAll @From("upsertSequences") List<Upsert> upserts) {

        InMemoryJournalRepo repo = new InMemoryJournalRepo();
        HealthJournalService service = new HealthJournalService(repo, new NoopMetricRepo());
        User user = owner();

        // Track the latest thoughts value applied per date — this is the "winner".
        Map<LocalDate, String> expected = new HashMap<>();
        for (Upsert u : upserts) {
            service.upsert(
                    OWNER_USERNAME,
                    new HealthJournalEntryRequest(u.date(), null, null, null, null, null, u.thoughts()),
                    user);
            expected.put(u.date(), u.thoughts());
        }

        // Row count equals distinct dates.
        assertThat(repo.rows.values()).hasSize(expected.size());

        // Every stored row matches the last-write-wins value for its date.
        for (HealthJournalEntry row : repo.rows.values()) {
            assertThat(row.getUserId()).isEqualTo(OWNER_ID);
            assertThat(expected).containsKey(row.getEntryDate());
            assertThat(row.getThoughts()).isEqualTo(expected.get(row.getEntryDate()));
        }
    }

    @Property(tries = 100)
    void upsert_byOtherUser_throwsAccessDenied(
            @ForAll @From("otherUsernames") String otherUsername) {

        InMemoryJournalRepo repo = new InMemoryJournalRepo();
        HealthJournalService service = new HealthJournalService(repo, new NoopMetricRepo());
        User user = owner();

        // The path variable is the OWNER; but the authenticated principal is someone else.
        User impostor = userWithUsername(999L, otherUsername);

        assertThatThrownBy(() -> service.upsert(
                OWNER_USERNAME,
                new HealthJournalEntryRequest(LocalDate.of(2025, 1, 1), null, null, null, null, null, "x"),
                impostor))
                .isInstanceOf(HealthAccessDeniedException.class);

        // Nothing was written.
        assertThat(repo.rows).isEmpty();

        // And the symmetric case: owner tries to access another user's path.
        assertThatThrownBy(() -> service.upsert(
                otherUsername,
                new HealthJournalEntryRequest(LocalDate.of(2025, 1, 1), null, null, null, null, null, "x"),
                user))
                .isInstanceOf(HealthAccessDeniedException.class);
    }

    // ---- generators ----

    @Provide
    Arbitrary<List<Upsert>> upsertSequences() {
        // Small date window so repeated hits on the same date are common — exercises the upsert path.
        Arbitrary<LocalDate> dates = Arbitraries.integers().between(0, 6)
                .map(i -> LocalDate.of(2025, 1, 1).plusDays(i));
        Arbitrary<String> thoughts = Arbitraries.strings().alpha().ofMinLength(0).ofMaxLength(20);
        Arbitrary<Upsert> single = Combinators.combine(dates, thoughts).as(Upsert::new);
        return single.list().ofMinSize(1).ofMaxSize(30);
    }

    @Provide
    Arbitrary<String> otherUsernames() {
        return Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20)
                .filter(s -> !OWNER_USERNAME.equals(s));
    }

    // ---- fixtures ----

    private static User owner() {
        return userWithUsername(OWNER_ID, OWNER_USERNAME);
    }

    private static User userWithUsername(long id, String username) {
        User u = new User(username, "x", new HashSet<>(Set.of("ROLE_USER")));
        u.setId(id);
        return u;
    }

    record Upsert(LocalDate date, String thoughts) {}

    /**
     * HashMap-backed in-memory {@link HealthJournalEntryRepository}. Only the
     * methods used by {@link HealthJournalService} are implemented; the rest
     * throw {@link UnsupportedOperationException}.
     */
    private static class InMemoryJournalRepo implements HealthJournalEntryRepository {
        final Map<String, HealthJournalEntry> rows = new HashMap<>();

        @Override
        public List<HealthJournalEntry> findByUserIdOrderByEntryDateDesc(Long userId) {
            return rows.values().stream()
                    .filter(e -> userId.equals(e.getUserId()))
                    .sorted((a, b) -> b.getEntryDate().compareTo(a.getEntryDate()))
                    .toList();
        }

        @Override
        public Optional<HealthJournalEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate) {
            return rows.values().stream()
                    .filter(e -> userId.equals(e.getUserId()) && entryDate.equals(e.getEntryDate()))
                    .findFirst();
        }

        @Override
        public Optional<HealthJournalEntry> findByIdAndUserId(String id, Long userId) {
            HealthJournalEntry e = rows.get(id);
            return Optional.ofNullable(e).filter(x -> userId.equals(x.getUserId()));
        }

        @Override
        public void deleteByUserId(Long userId) {
            rows.entrySet().removeIf(e -> userId.equals(e.getValue().getUserId()));
        }

        @Override
        public void deleteByIdAndUserId(String id, Long userId) {
            HealthJournalEntry e = rows.get(id);
            if (e != null && userId.equals(e.getUserId())) rows.remove(id);
        }

        @Override
        @NonNull
        public <S extends HealthJournalEntry> S save(@NonNull S entity) {
            if (entity.getId() == null) {
                // Mirror the UUID strategy used in production.
                try {
                    java.lang.reflect.Field f = HealthJournalEntry.class.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(entity, UUID.randomUUID().toString());
                } catch (ReflectiveOperationException ex) {
                    throw new RuntimeException(ex);
                }
            }
            rows.put(entity.getId(), entity);
            return entity;
        }

        @Override
        public void delete(@NonNull HealthJournalEntry entity) {
            rows.remove(entity.getId());
        }

        // -- Unused JpaRepository surface --
        @Override @NonNull public Optional<HealthJournalEntry> findById(@NonNull String id) { return Objects.requireNonNull(Optional.ofNullable(rows.get(id))); }
        @Override public boolean existsById(@NonNull String s) { return rows.containsKey(s); }
        @Override @NonNull public List<HealthJournalEntry> findAll() { return new ArrayList<>(rows.values()); }
        @Override @NonNull public List<HealthJournalEntry> findAllById(@NonNull Iterable<String> ids) { throw uoe(); }
        @Override public long count() { return rows.size(); }
        @Override public void deleteById(@NonNull String s) { rows.remove(s); }
        @Override public void deleteAll(@NonNull Iterable<? extends HealthJournalEntry> entities) { throw uoe(); }
        @Override public void deleteAllById(@NonNull Iterable<? extends String> ids) { throw uoe(); }
        @Override public void deleteAll() { rows.clear(); }
        @Override @NonNull public <S extends HealthJournalEntry> List<S> saveAll(@NonNull Iterable<S> entities) { throw uoe(); }
        @Override public void flush() { }
        @Override @NonNull public <S extends HealthJournalEntry> S saveAndFlush(@NonNull S entity) { return save(entity); }
        @Override @NonNull public <S extends HealthJournalEntry> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) { throw uoe(); }
        @Override public void deleteAllInBatch(@NonNull Iterable<HealthJournalEntry> entities) { throw uoe(); }
        @Override public void deleteAllByIdInBatch(@NonNull Iterable<String> ids) { throw uoe(); }
        @Override public void deleteAllInBatch() { rows.clear(); }
        @Override @NonNull public HealthJournalEntry getOne(@NonNull String s) { throw uoe(); }
        @Override @NonNull public HealthJournalEntry getById(@NonNull String s) { throw uoe(); }
        @Override @NonNull public HealthJournalEntry getReferenceById(@NonNull String s) { throw uoe(); }
        @Override @NonNull public <S extends HealthJournalEntry> Optional<S> findOne(@NonNull Example<S> example) { throw uoe(); }
        @Override @NonNull public <S extends HealthJournalEntry> List<S> findAll(@NonNull Example<S> example) { throw uoe(); }
        @Override @NonNull public <S extends HealthJournalEntry> List<S> findAll(@NonNull Example<S> example, @NonNull Sort sort) { throw uoe(); }
        @Override @NonNull public <S extends HealthJournalEntry> Page<S> findAll(@NonNull Example<S> example, @NonNull Pageable pageable) { throw uoe(); }
        @Override public <S extends HealthJournalEntry> long count(@NonNull Example<S> example) { throw uoe(); }
        @Override public <S extends HealthJournalEntry> boolean exists(@NonNull Example<S> example) { throw uoe(); }
        @Override @NonNull public <S extends HealthJournalEntry, R> R findBy(@NonNull Example<S> example, @NonNull Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw uoe(); }
        @Override @NonNull public List<HealthJournalEntry> findAll(@NonNull Sort sort) { throw uoe(); }
        @Override @NonNull public Page<HealthJournalEntry> findAll(@NonNull Pageable pageable) { throw uoe(); }

        private static UnsupportedOperationException uoe() { return new UnsupportedOperationException("not used in property tests"); }

        // satisfy spotbugs: reference fields we don't actually need
        @SuppressWarnings("unused") private final AtomicReference<Collection<Object>> _unused = new AtomicReference<>();
    }

    /**
     * Stub {@link HealthMetricRepository} that reports no seeded mood metric,
     * so {@link HealthJournalService}'s mood-sync path is a no-op. These tests
     * predate the mood field and don't assert on graph state.
     */
    @SuppressWarnings("null")
    private static class NoopMetricRepo implements HealthMetricRepository {
        @Override @NonNull public List<HealthMetric> findByUserIdOrderByCreatedAtAsc(Long userId) { return List.of(); }
        @Override @NonNull public Optional<HealthMetric> findByIdAndUserId(String id, Long userId) { return Optional.empty(); }
        @Override @NonNull public Optional<HealthMetric> findByUserIdAndSlug(Long userId, String slug) { return Optional.empty(); }
        @Override public boolean existsByUserIdAndSlug(Long userId, String slug) { return false; }

        // -- Unused JpaRepository surface --
        @Override @NonNull public <S extends HealthMetric> S save(@NonNull S entity) { throw uoe(); }
        @Override public void delete(@NonNull HealthMetric entity) { throw uoe(); }
        @Override @NonNull public Optional<HealthMetric> findById(@NonNull String id) { return Optional.empty(); }
        @Override public boolean existsById(@NonNull String s) { return false; }
        @Override @NonNull public List<HealthMetric> findAll() { return List.of(); }
        @Override @NonNull public List<HealthMetric> findAllById(@NonNull Iterable<String> ids) { throw uoe(); }
        @Override public long count() { return 0; }
        @Override public void deleteById(@NonNull String s) { throw uoe(); }
        @Override public void deleteAll(@NonNull Iterable<? extends HealthMetric> entities) { throw uoe(); }
        @Override public void deleteAllById(@NonNull Iterable<? extends String> ids) { throw uoe(); }
        @Override public void deleteAll() { throw uoe(); }
        @Override @NonNull public <S extends HealthMetric> List<S> saveAll(@NonNull Iterable<S> entities) { throw uoe(); }
        @Override public void flush() { }
        @Override @NonNull public <S extends HealthMetric> S saveAndFlush(@NonNull S entity) { throw uoe(); }
        @Override @NonNull public <S extends HealthMetric> List<S> saveAllAndFlush(@NonNull Iterable<S> entities) { throw uoe(); }
        @Override public void deleteAllInBatch(@NonNull Iterable<HealthMetric> entities) { throw uoe(); }
        @Override public void deleteAllByIdInBatch(@NonNull Iterable<String> ids) { throw uoe(); }
        @Override public void deleteAllInBatch() { throw uoe(); }
        @Override @NonNull public HealthMetric getOne(@NonNull String s) { throw uoe(); }
        @Override @NonNull public HealthMetric getById(@NonNull String s) { throw uoe(); }
        @Override @NonNull public HealthMetric getReferenceById(@NonNull String s) { throw uoe(); }
        @Override @NonNull public <S extends HealthMetric> Optional<S> findOne(@NonNull Example<S> example) { throw uoe(); }
        @Override @NonNull public <S extends HealthMetric> List<S> findAll(@NonNull Example<S> example) { throw uoe(); }
        @Override @NonNull public <S extends HealthMetric> List<S> findAll(@NonNull Example<S> example, @NonNull Sort sort) { throw uoe(); }
        @Override @NonNull public <S extends HealthMetric> Page<S> findAll(@NonNull Example<S> example, @NonNull Pageable pageable) { throw uoe(); }
        @Override public <S extends HealthMetric> long count(@NonNull Example<S> example) { throw uoe(); }
        @Override public <S extends HealthMetric> boolean exists(@NonNull Example<S> example) { throw uoe(); }
        @Override @NonNull public <S extends HealthMetric, R> R findBy(@NonNull Example<S> example, @NonNull Function<FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { throw uoe(); }
        @Override @NonNull public List<HealthMetric> findAll(@NonNull Sort sort) { throw uoe(); }
        @Override @NonNull public Page<HealthMetric> findAll(@NonNull Pageable pageable) { throw uoe(); }

        private static UnsupportedOperationException uoe() { return new UnsupportedOperationException("not used in property tests"); }
    }
}
