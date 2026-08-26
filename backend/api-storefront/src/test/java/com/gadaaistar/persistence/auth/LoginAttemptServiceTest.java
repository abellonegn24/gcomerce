package com.gadaaistar.persistence.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LoginAttemptService} lockout logic.
 *
 * <p>Uses a simple in-memory stub for {@link LoginAttemptLogRepository} and a
 * hand-rolled {@link TestAccount} that implements {@link com.gadaaistar.persistence.LockableAccount}.
 * No CDI container or database needed — we inject the dependencies via reflection,
 * mirroring the pattern in {@code MpgsServiceTest}.
 */
class LoginAttemptServiceTest {

    private LoginAttemptService service;
    private CapturingLogRepository logRepo;
    private Map<Long, TestAccount> entityStore;
    private EntityManager em;

    @BeforeEach
    void setUp() throws Exception {
        service = new LoginAttemptService();
        logRepo = new CapturingLogRepository();
        entityStore = new HashMap<>();

        // Stub EntityManager: find() returns the test account from the store.
        // This simulates the REQUIRES_NEW re-fetch that happens in production.
        em = (EntityManager) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] { EntityManager.class },
                (proxy, method, args) -> {
                    if ("find".equals(method.getName()) && args.length == 2) {
                        return entityStore.get(args[1]);
                    }
                    return null;
                });

        // Inject config + repo via reflection (no CDI in a plain unit test).
        setField("enabled", true);
        setField("maxFailedAttempts", 5);
        setField("baseLockoutSeconds", 900L);
        setField("maxLockoutSeconds", 3600L);
        setField("progressive", true);

        var repoField = LoginAttemptService.class.getDeclaredField("auditLog");
        repoField.setAccessible(true);
        repoField.set(service, logRepo);

        var emField = LoginAttemptService.class.getDeclaredField("em");
        emField.setAccessible(true);
        emField.set(service, em);
    }

    private void setField(String name, Object value) throws Exception {
        var f = LoginAttemptService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @Nested
    @DisplayName("checkNotLocked")
    class CheckNotLocked {
        @Test
        @DisplayName("does nothing when lockedUntil is null")
        void noLock() {
            var acct = new TestAccount(1, "user@test.com");
            assertDoesNotThrow(() -> service.checkNotLocked(acct));
        }

        @Test
        @DisplayName("throws AccountLockedException when lockedUntil is in the future")
        void locked() {
            var acct = new TestAccount(1, "user@test.com");
            acct.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
            var ex = assertThrows(com.gadaaistar.common.exceptions.AccountLockedException.class,
                    () -> service.checkNotLocked(acct));
            assertEquals(429, ex.status());
            assertEquals("ACCOUNT_LOCKED", ex.code());
            assertTrue(ex.retryAfterSeconds() > 0);
        }

        @Test
        @DisplayName("does not throw when lockedUntil is in the past")
        void lockExpired() {
            var acct = new TestAccount(1, "user@test.com");
            acct.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
            assertDoesNotThrow(() -> service.checkNotLocked(acct));
        }

        @Test
        @DisplayName("no-op when service is disabled")
        void disabled() throws Exception {
            setField("enabled", false);
            var acct = new TestAccount(1, "user@test.com");
            acct.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
            assertDoesNotThrow(() -> service.checkNotLocked(acct));
        }
    }

    @Nested
    @DisplayName("recordFailure")
    class RecordFailure {
        @Test
        @DisplayName("increments counter but does not lock before threshold")
        void belowThreshold() {
            var acct = new TestAccount(1, "user@test.com");
            entityStore.put(1L, acct);
            for (int i = 1; i < 5; i++) {
                service.recordFailure(acct, "1.2.3.4", "ua", LoginAttemptService.AccountType.CUSTOMER);
                assertEquals(i, acct.getFailedLoginAttempts());
                assertNull(acct.getLockedUntil());
            }
        }

        @Test
        @DisplayName("locks the account at the threshold with base duration (first lockout)")
        void atThreshold() {
            var acct = new TestAccount(1, "user@test.com");
            entityStore.put(1L, acct);
            for (int i = 0; i < 5; i++) {
                service.recordFailure(acct, "1.2.3.4", "ua", LoginAttemptService.AccountType.CUSTOMER);
            }
            // After 5 failures: locked, counter reset, sequence incremented.
            assertNotNull(acct.getLockedUntil());
            assertTrue(acct.getLockedUntil().isAfter(Instant.now()));
            assertEquals(1, acct.getLockoutSequence());
            assertEquals(0, acct.getFailedLoginAttempts());
        }

        @Test
        @DisplayName("progressive doubling: 2nd lockout = 30 min, 3rd = 60 min, capped at 60 min")
        void progressiveDurations() {
            var acct = new TestAccount(1, "user@test.com");
            entityStore.put(1L, acct);

            // 1st lockout — 15 min (900s)
            triggerLockout(acct);
            long firstDuration = secondsUntil(acct.getLockedUntil());
            assertTrue(firstDuration >= 890 && firstDuration <= 910, "1st lockout ~900s, got " + firstDuration);
            assertEquals(1, acct.getLockoutSequence());

            // Simulate lockout expiry and clear lockedUntil (as the login flow would see)
            acct.setLockedUntil(null);

            // 2nd lockout — 30 min (1800s)
            triggerLockout(acct);
            long secondDuration = secondsUntil(acct.getLockedUntil());
            assertTrue(secondDuration >= 1790 && secondDuration <= 1810, "2nd lockout ~1800s, got " + secondDuration);
            assertEquals(2, acct.getLockoutSequence());
            acct.setLockedUntil(null);

            // 3rd lockout — 60 min (3600s)
            triggerLockout(acct);
            long thirdDuration = secondsUntil(acct.getLockedUntil());
            assertTrue(thirdDuration >= 3590 && thirdDuration <= 3610, "3rd lockout ~3600s, got " + thirdDuration);
            assertEquals(3, acct.getLockoutSequence());
            acct.setLockedUntil(null);

            // 4th lockout — still 60 min (capped)
            triggerLockout(acct);
            long fourthDuration = secondsUntil(acct.getLockedUntil());
            assertTrue(fourthDuration >= 3590 && fourthDuration <= 3610, "4th lockout capped at 3600s, got " + fourthDuration);
            assertEquals(4, acct.getLockoutSequence());
        }

        @Test
        @DisplayName("writes an audit log row for every failure")
        void auditLogWritten() {
            var acct = new TestAccount(1, "user@test.com");
            entityStore.put(1L, acct);
            service.recordFailure(acct, "1.2.3.4", "ua", LoginAttemptService.AccountType.ADMIN);
            assertEquals(1, logRepo.persisted.size());
            assertFalse(logRepo.persisted.get(0).success);
            assertEquals("ADMIN", logRepo.persisted.get(0).accountType);
        }

        private void triggerLockout(TestAccount acct) {
            for (int i = 0; i < 5; i++) {
                service.recordFailure(acct, "1.2.3.4", "ua", LoginAttemptService.AccountType.CUSTOMER);
            }
        }

        private long secondsUntil(Instant until) {
            return ChronoUnit.SECONDS.between(Instant.now(), until);
        }
    }

    @Nested
    @DisplayName("recordSuccess")
    class RecordSuccess {
        @Test
        @DisplayName("resets all three lockout fields")
        void resets() {
            var acct = new TestAccount(1, "user@test.com");
            entityStore.put(1L, acct);
            acct.setFailedLoginAttempts(3);
            acct.setLockedUntil(Instant.now().plus(5, ChronoUnit.MINUTES));
            acct.setLockoutSequence(2);

            service.recordSuccess(acct, "1.2.3.4", "ua", LoginAttemptService.AccountType.CUSTOMER);

            assertEquals(0, acct.getFailedLoginAttempts());
            assertNull(acct.getLockedUntil());
            assertEquals(0, acct.getLockoutSequence());
        }

        @Test
        @DisplayName("writes a success audit log row")
        void auditLogWritten() {
            var acct = new TestAccount(1, "user@test.com");
            entityStore.put(1L, acct);
            service.recordSuccess(acct, "1.2.3.4", "ua", LoginAttemptService.AccountType.CUSTOMER);
            assertEquals(1, logRepo.persisted.size());
            assertTrue(logRepo.persisted.get(0).success);
        }
    }

    @Nested
    @DisplayName("unlock")
    class Unlock {
        @Test
        @DisplayName("clears all lockout fields")
        void clears() {
            var acct = new TestAccount(1, "user@test.com");
            entityStore.put(1L, acct);
            acct.setFailedLoginAttempts(5);
            acct.setLockedUntil(Instant.now().plus(10, ChronoUnit.MINUTES));
            acct.setLockoutSequence(3);

            service.unlock(acct);

            assertEquals(0, acct.getFailedLoginAttempts());
            assertNull(acct.getLockedUntil());
            assertEquals(0, acct.getLockoutSequence());
        }
    }

    // -------------------------------------------------------------------------
    // Test doubles
    // -------------------------------------------------------------------------

    /** Minimal LockableAccount implementation for testing. */
    static class TestAccount implements com.gadaaistar.persistence.LockableAccount {
        private final Long id;
        private final String email;
        private int failedLoginAttempts;
        private Instant lockedUntil;
        private int lockoutSequence;

        TestAccount(long id, String email) {
            this.id = id;
            this.email = email;
        }

        @Override public Long getId() { return id; }
        @Override public String getEmail() { return email; }
        @Override public int getFailedLoginAttempts() { return failedLoginAttempts; }
        @Override public void setFailedLoginAttempts(int count) { this.failedLoginAttempts = count; }
        @Override public Instant getLockedUntil() { return lockedUntil; }
        @Override public void setLockedUntil(Instant until) { this.lockedUntil = until; }
        @Override public int getLockoutSequence() { return lockoutSequence; }
        @Override public void setLockoutSequence(int sequence) { this.lockoutSequence = sequence; }
    }

    /** Captures persisted LoginAttemptLog rows without a real DB. */
    static class CapturingLogRepository extends LoginAttemptLogRepository {
        final java.util.List<LoginAttemptLog> persisted = new java.util.ArrayList<>();

        @Override
        public void persist(LoginAttemptLog entity) {
            persisted.add(entity);
        }
    }
}
