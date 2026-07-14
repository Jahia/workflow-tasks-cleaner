package org.jahia.community.workflowtaskscleaner;

import org.assertj.core.api.Assertions;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.settings.SettingsBean;
import org.jahia.utils.DatabaseUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * S17 (G4 / U3) — the {@code IS_RUNNING} guard is NO LONGER a facade (Stage-7 fix).
 *
 * <p>The concurrency guard was moved out of {@code runClean} and into
 * {@code CleanCommand.deleteTasks()} itself, so <b>every</b> entry point honours it: the scheduled
 * Quartz job ({@link CleanBackgroundJob}) and the Karaf {@code clean} command
 * ({@link CleanCommand#execute()}) both funnel through {@code deleteTasks()}. This test proves that
 * when the shared guard is already held, neither path performs any deletion; and when it is free, the
 * path does run (the bulk SQL fires once) — confirming they are genuinely covered by the single guard.</p>
 */
public class CleanFacadeGuardTest {

    private static AtomicBoolean isRunning() throws Exception {
        Field f = CleanCommand.class.getDeclaredField("IS_RUNNING");
        f.setAccessible(true);
        return (AtomicBoolean) f.get(null);
    }

    @Before
    @After
    public void resetGuard() throws Exception {
        isRunning().set(false);
    }

    // --- Guard held -> Quartz job + Karaf command both skip (no deletion). ---
    @Test
    public void quartzJobAndKarafCommand_bothHonourTheRunningGuard() throws Exception {
        SettingsBean settings = mock(SettingsBean.class);
        when(settings.isProcessingServer()).thenReturn(true);

        isRunning().set(true); // a run is already in flight on this node

        try (MockedStatic<SettingsBean> ms = mockStatic(SettingsBean.class);
             MockedStatic<DatabaseUtils> md = mockStatic(DatabaseUtils.class)) {
            ms.when(SettingsBean::getInstance).thenReturn(settings);

            new CleanBackgroundJob().executeJahiaJob(null);
            new CleanCommand().execute();

            // Guard busy -> deleteTasks short-circuited before touching the DB on BOTH paths.
            md.verify(() -> DatabaseUtils.executeStatements(any()), never());
        }
        // The in-flight run's flag was not disturbed by the skipped invocations.
        Assertions.assertThat(isRunning().get()).isTrue();
    }

    // --- Guard free -> a single guarded path runs the bulk SQL exactly once (control). ---
    @Test
    public void guardFree_karafCommand_runsDeletionOnce() throws Exception {
        SettingsBean settings = mock(SettingsBean.class);
        when(settings.isProcessingServer()).thenReturn(true);
        JCRTemplate jcrTemplate = mock(JCRTemplate.class); // ghost phase no-op

        try (MockedStatic<SettingsBean> ms = mockStatic(SettingsBean.class);
             MockedStatic<DatabaseUtils> md = mockStatic(DatabaseUtils.class);
             MockedStatic<JCRTemplate> mj = mockStatic(JCRTemplate.class);
             MockedStatic<CleanCommand> mcc = mockStatic(CleanCommand.class, CALLS_REAL_METHODS)) {
            ms.when(SettingsBean::getInstance).thenReturn(settings);
            mj.when(JCRTemplate::getInstance).thenReturn(jcrTemplate);
            mcc.when(CleanCommand::lookupRootJahiaUser).thenReturn(mock(JahiaUser.class));

            new CleanCommand().execute();

            md.verify(() -> DatabaseUtils.executeStatements(any()), times(1));
        }
        // Guard released in finally.
        Assertions.assertThat(isRunning().get()).isFalse();
    }
}
