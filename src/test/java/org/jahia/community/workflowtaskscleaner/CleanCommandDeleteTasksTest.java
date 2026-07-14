package org.jahia.community.workflowtaskscleaner;

import org.jahia.services.content.JCRTemplate;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.settings.SettingsBean;
import org.jahia.utils.DatabaseUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * S9/S10/S12 (G7/G3 / F1, F6, U1, U4, U7) — {@code deleteTasks()} processing-server gating and the
 * ghost phase's independence from bulk-SQL failure.
 *
 * <p>S9/S12 drive the real {@code deleteTasks()} via {@code mockStatic(CleanCommand, CALLS_REAL_METHODS)}
 * and stub only the {@code lookupRootJahiaUser()} seam, so the ghost phase is entered without touching
 * the unmockable {@code JCRUserNode} chain; {@code JCRTemplate.doExecute} is a no-op (callback not run),
 * proving the phase is reached without asserting the abort logic (that is S13's job).</p>
 */
public class CleanCommandDeleteTasksTest {

    @SuppressWarnings("unchecked")
    private static List<String> sqlStmts() throws Exception {
        Field f = CleanCommand.class.getDeclaredField("SQL_STMTS");
        f.setAccessible(true);
        return (List<String>) f.get(null);
    }

    @Before
    @After
    public void resetGuard() throws Exception {
        // Stage-7 (U3): deleteTasks() now acquires the shared IS_RUNNING guard; reset it so a
        // leaked flag from another test can't make deleteTasks() short-circuit here.
        Field f = CleanCommand.class.getDeclaredField("IS_RUNNING");
        f.setAccessible(true);
        ((AtomicBoolean) f.get(null)).set(false);
    }

    // --- S9: processing server -> SQL + ghost phase run once ---
    @Test
    public void deleteTasks_onProcessingServer_runsBulkSqlAndGhostPhaseOnce() throws Exception {
        SettingsBean settings = mock(SettingsBean.class);
        when(settings.isProcessingServer()).thenReturn(true);
        JCRTemplate jcrTemplate = mock(JCRTemplate.class); // doExecute no-op: callback never invoked

        try (MockedStatic<SettingsBean> ms = mockStatic(SettingsBean.class);
             MockedStatic<DatabaseUtils> md = mockStatic(DatabaseUtils.class);
             MockedStatic<JCRTemplate> mj = mockStatic(JCRTemplate.class);
             MockedStatic<CleanCommand> mcc = mockStatic(CleanCommand.class, CALLS_REAL_METHODS)) {
            ms.when(SettingsBean::getInstance).thenReturn(settings);
            mj.when(JCRTemplate::getInstance).thenReturn(jcrTemplate);
            mcc.when(CleanCommand::lookupRootJahiaUser).thenReturn(mock(JahiaUser.class));

            CleanCommand.deleteTasks();

            md.verify(() -> DatabaseUtils.executeStatements(sqlStmts()), times(1));
            mj.verify(JCRTemplate::getInstance, times(1)); // ghost phase reached
        }
    }

    // --- S10: non-processing server -> full no-op ---
    @Test
    public void deleteTasks_onNonProcessingServer_isAFullNoOp() {
        SettingsBean settings = mock(SettingsBean.class);
        when(settings.isProcessingServer()).thenReturn(false);

        try (MockedStatic<SettingsBean> ms = mockStatic(SettingsBean.class);
             MockedStatic<DatabaseUtils> md = mockStatic(DatabaseUtils.class);
             MockedStatic<JCRTemplate> mj = mockStatic(JCRTemplate.class)) {
            ms.when(SettingsBean::getInstance).thenReturn(settings);

            try {
                CleanCommand.deleteTasks();
            } catch (Exception e) {
                throw new AssertionError("deleteTasks must not throw on a non-processing server", e);
            }

            verifyNoInteractions(DatabaseUtils.class);
            mj.verify(JCRTemplate::getInstance, never());
        }
    }

    // --- S12: ghost phase still runs after a bulk-SQL SQLException ---
    @Test
    public void deleteTasks_stillRunsGhostPhase_whenBulkSqlThrowsSqlException() throws Exception {
        SettingsBean settings = mock(SettingsBean.class);
        when(settings.isProcessingServer()).thenReturn(true);
        JCRTemplate jcrTemplate = mock(JCRTemplate.class);

        try (MockedStatic<SettingsBean> ms = mockStatic(SettingsBean.class);
             MockedStatic<DatabaseUtils> md = mockStatic(DatabaseUtils.class);
             MockedStatic<JCRTemplate> mj = mockStatic(JCRTemplate.class);
             MockedStatic<CleanCommand> mcc = mockStatic(CleanCommand.class, CALLS_REAL_METHODS)) {
            ms.when(SettingsBean::getInstance).thenReturn(settings);
            md.when(() -> DatabaseUtils.executeStatements(any())).thenThrow(new SQLException("boom"));
            mj.when(JCRTemplate::getInstance).thenReturn(jcrTemplate);
            mcc.when(CleanCommand::lookupRootJahiaUser).thenReturn(mock(JahiaUser.class));

            // Must NOT propagate — the SQLException is caught + logged.
            CleanCommand.deleteTasks();

            // Ghost phase still reached despite the SQL failure.
            mj.verify(JCRTemplate::getInstance, times(1));
        }
    }
}
