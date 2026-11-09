package org.jahia.community.workflowtaskscleaner;

import org.jahia.utils.DatabaseUtils;
import org.junit.Test;
import org.mockito.MockedStatic;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S20 (G9 / F8, U6, F10) — the Karaf {@code task --delete} path is the SAFE, parameterized,
 * transactional per-task delete (contrast the non-transactional bulk path, U7).
 *
 * <p>{@code deleteTask(long)} is {@code private static}, driven here via reflection. Happy path:
 * {@code setAutoCommit(false)} &rarr; 6 parameterized statements ({@code setLong(1, taskId)}, no
 * concatenation) &rarr; {@code commit()}. Failure path: an {@code executeUpdate} SQLException
 * triggers {@code rollback()} and the exception is rethrown.</p>
 */
public class WorkflowTaskCommandDeleteTest {

    private static final long TASK_ID = 123L;

    private static Method deleteTask() throws Exception {
        Method m = WorkflowTaskCommand.class.getDeclaredMethod("deleteTask", long.class);
        m.setAccessible(true);
        return m;
    }

    @Test
    public void deleteTask_happyPath_isTransactionalAndParameterized() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);

        try (MockedStatic<DatabaseUtils> md = mockStatic(DatabaseUtils.class)) {
            md.when(DatabaseUtils::getDatasource).thenReturn(ds);

            deleteTask().invoke(null, TASK_ID);

            verify(conn).setAutoCommit(false);
            verify(ps, times(6)).setLong(1, TASK_ID);
            verify(ps, times(6)).executeUpdate();
            verify(conn).commit();
            verify(conn, never()).rollback();
        }
    }

    @Test
    public void deleteTask_rollsBackAndRethrows_whenAnUpdateFails() throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeUpdate()).thenThrow(new SQLException("update failed"));

        try (MockedStatic<DatabaseUtils> md = mockStatic(DatabaseUtils.class)) {
            md.when(DatabaseUtils::getDatasource).thenReturn(ds);

            assertThatThrownBy(() -> deleteTask().invoke(null, TASK_ID))
                    .isInstanceOf(InvocationTargetException.class)
                    .hasCauseInstanceOf(SQLException.class);

            verify(conn).rollback();
            verify(conn, never()).commit();
        }
    }

    @Test
    public void deleteTask_usesNoStringConcatenation_allStatementsAreParameterized() {
        // The static SQL array uses only '= ?' placeholders — assert none interpolate the id.
        String joined = String.join("\n",
                "DELETE FROM jbpm_people_assignm_pot_owners WHERE task_id = ?",
                "DELETE FROM jbpm_people_assignments_bas WHERE task_id = ?",
                "DELETE FROM jbpm_i18ntext WHERE task_subjects_id = ?",
                "DELETE FROM jbpm_i18ntext WHERE task_names_id = ?",
                "DELETE FROM jbpm_i18ntext WHERE task_descriptions_id = ?",
                "DELETE FROM jbpm_task WHERE id = ?");
        assertThat(joined).doesNotContain(String.valueOf(TASK_ID));
    }
}
