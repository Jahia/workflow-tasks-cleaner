package org.jahia.community.workflowtaskscleaner;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S11 (G2 / U1, D5) — Regression guard on the conservative bulk-SQL predicate.
 *
 * <p>CHARACTERIZATION: locks the SAFE bulk-delete path so a future edit cannot silently widen
 * deletion. Every statement must be state-only (Completed/Exited) AND orphaned-instance scoped,
 * with NO age/date filter. If someone adds {@code Cancelled}/{@code Obsolete}, removes the orphan
 * clause, or introduces a date predicate, this test fails.</p>
 */
public class CleanCommandSqlStmtsTest {

    @SuppressWarnings("unchecked")
    private static List<String> sqlStmts() throws Exception {
        Field f = CleanCommand.class.getDeclaredField("SQL_STMTS");
        f.setAccessible(true);
        return (List<String>) f.get(null);
    }

    @Test
    public void sqlStmts_containExactlySixStatements() throws Exception {
        assertThat(sqlStmts()).hasSize(6);
    }

    @Test
    public void everyStatement_pinsCompletedOrExitedAndOrphanedInstance() throws Exception {
        for (String stmt : sqlStmts()) {
            assertThat(stmt)
                    .as("state-only predicate must be present: %s", stmt)
                    .contains("(status='Completed' OR status='Exited')");
            assertThat(stmt)
                    .as("orphaned-instance predicate must be present: %s", stmt)
                    .contains("PROCESS_INSTANCE_ID NOT IN (SELECT instance_id FROM jbpm_process_instance_info)");
        }
    }

    @Test
    public void noStatement_containsAnyAgeOrDateToken() throws Exception {
        for (String stmt : sqlStmts()) {
            String upper = stmt.toUpperCase();
            assertThat(upper).as("no 'created' age filter: %s", stmt).doesNotContain("CREATED");
            assertThat(upper).as("no 'dueDate' age filter: %s", stmt).doesNotContain("DUEDATE");
            assertThat(upper).as("no DATEADD age filter: %s", stmt).doesNotContain("DATEADD");
            assertThat(upper).as("no INTERVAL age filter: %s", stmt).doesNotContain("INTERVAL");
            assertThat(stmt).as("no '<' comparison (age filter): %s", stmt).doesNotContain("<");
        }
    }

    @Test
    public void lastStatement_targetsJbpmTask() throws Exception {
        List<String> stmts = sqlStmts();
        assertThat(stmts.get(stmts.size() - 1)).startsWith("DELETE FROM jbpm_task WHERE");
    }
}
