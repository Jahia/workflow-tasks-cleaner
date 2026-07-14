package org.jahia.community.workflowtaskscleaner.graphql;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S15/S16/S18 (G5/G6 / F2, U3, U5, F10) — {@code runClean} concurrency guard.
 *
 * <p><b>Re-specced per Stage 4:</b> the Stage-3 {@code mockStatic(CleanCommand)} + latch approach
 * CANNOT work — {@code runClean} runs {@code deleteTasks()} on a {@code new Thread(...)}
 * ({@code WorkflowTasksCleanerMutation.java:37}) and Mockito's {@code MockedStatic} is
 * <em>thread-local</em>, so the stub is inactive on the worker thread. Instead we prove the
 * observable contract via reflective inspection of the {@code IS_RUNNING} field: the guard rejects
 * a concurrent run, and the worker's {@code finally} block resets the flag so a later run is
 * allowed. No cross-thread mock is needed.</p>
 */
public class WorkflowTasksCleanerMutationConcurrencyTest {

    private static AtomicBoolean isRunning() throws Exception {
        Field f = WorkflowTasksCleanerMutation.class.getDeclaredField("IS_RUNNING");
        f.setAccessible(true);
        return (AtomicBoolean) f.get(null);
    }

    private static void awaitGuardReset() throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (isRunning().get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    @Before
    @After
    public void resetGuard() throws Exception {
        // The real worker (unmocked Jahia singletons) fails fast and resets IS_RUNNING in finally,
        // but reset defensively so tests are isolated regardless of ordering/timing.
        awaitGuardReset();
        isRunning().set(false);
    }

    // --- S15: guard rejects a concurrent GraphQL run ---
    @Test
    public void runClean_returnsFalse_whenAnotherRunIsAlreadyInProgress() throws Exception {
        isRunning().set(true); // simulate a run already in flight

        Boolean result = new WorkflowTasksCleanerMutation().runClean();

        assertThat(result).isFalse();
        // Guard was already busy and must remain so (we did not dispatch a worker).
        assertThat(isRunning().get()).isTrue();
    }

    // --- S16: the flag is cleared even on the happy path so a later run is allowed ---
    @Test
    public void runClean_resetsGuard_soASubsequentRunIsAllowed() throws Exception {
        Boolean first = new WorkflowTasksCleanerMutation().runClean();
        assertThat(first).isTrue();

        awaitGuardReset();
        assertThat(isRunning().get()).as("worker's finally block must reset the guard").isFalse();

        Boolean second = new WorkflowTasksCleanerMutation().runClean();
        assertThat(second).isTrue();
    }

    // --- S18: runClean returns TRUE synchronously and swallows worker failures ---
    @Test
    public void runClean_returnsTrueSynchronously_andNoExceptionEscapesTheCaller() throws Exception {
        // The spawned worker hits unmocked Jahia singletons and throws; runClean must NOT propagate it.
        Boolean result = new WorkflowTasksCleanerMutation().runClean();

        assertThat(result).as("returns TRUE before/despite any worker failure").isTrue();

        awaitGuardReset();
        assertThat(isRunning().get()).as("guard reset in finally even when the worker threw").isFalse();
    }
}
