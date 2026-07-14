package org.jahia.community.workflowtaskscleaner;

import org.jahia.community.workflowtaskscleaner.graphql.WorkflowTasksCleanerMutation;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * S17 (G4 / U3, D6, F6) — the {@code IS_RUNNING} guard is a FACADE.
 *
 * <p>Only {@code runClean} consults {@code IS_RUNNING}. Both the scheduled Quartz job
 * ({@link CleanBackgroundJob}) and the Karaf {@code clean} command ({@link CleanCommand#execute()})
 * call {@code CleanCommand.deleteTasks()} directly, bypassing the guard entirely. This proves the
 * U3 false-mutual-exclusion and D6 (identical deletion logic). Both paths run synchronously on the
 * test thread, so the {@link MockedStatic} stub is active. Stage-7 must move the guard into
 * {@code deleteTasks()} itself (or a cluster-wide lock).</p>
 */
public class CleanFacadeGuardTest {

    private static AtomicBoolean isRunning() throws Exception {
        Field f = WorkflowTasksCleanerMutation.class.getDeclaredField("IS_RUNNING");
        f.setAccessible(true);
        return (AtomicBoolean) f.get(null);
    }

    @Before
    public void setGuardBusy() throws Exception {
        // Simulate "a GraphQL run is already in flight".
        isRunning().set(true);
    }

    @After
    public void clearGuard() throws Exception {
        isRunning().set(false);
    }

    @Test
    public void quartzJobAndKarafCommand_bothBypassTheRunningGuard() throws Exception {
        try (MockedStatic<CleanCommand> mc = mockStatic(CleanCommand.class)) {
            mc.when(CleanCommand::deleteTasks).thenAnswer(inv -> null);

            new CleanBackgroundJob().executeJahiaJob(null);
            new CleanCommand().execute();

            // Both invoked deleteTasks() despite IS_RUNNING == true.
            mc.verify(CleanCommand::deleteTasks, times(2));
        }
    }
}
