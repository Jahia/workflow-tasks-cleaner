package org.jahia.community.workflowtaskscleaner;

import org.jahia.community.workflowtaskscleaner.graphql.WorkflowTasksCleanerMutation;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S30 (G16 / U8) — the public deletion entry-point signatures carry NO dry-run parameter.
 *
 * <p>Confirms that none of {@code runClean}, {@code CleanCommand.execute()},
 * {@code CleanCommand.deleteTasks()} or {@code saveConfig} takes a preview/dry-run parameter or
 * returns a would-delete list. <b>Stage-7 note (U2):</b> the report-only/dry-run mode for
 * ghost-workflow abort was added <em>without</em> changing these signatures — it is driven by the
 * {@code abortGhostWorkflows} OSGi configuration flag (default false = report-only) and the
 * {@link GhostWorkflowCleaner} collaborator, so this contract deliberately still holds.</p>
 */
public class NoDryRunTest {

    @Test
    public void runClean_takesNoParameterAndReturnsBoolean() throws Exception {
        Method m = WorkflowTasksCleanerMutation.class.getMethod("runClean");
        assertThat(m.getParameterCount()).isZero();
        assertThat(m.getReturnType()).isEqualTo(Boolean.class);
    }

    @Test
    public void saveConfig_takesOnlyTheCronExpressionString() throws Exception {
        Method m = WorkflowTasksCleanerMutation.class.getMethod("saveConfig", String.class);
        assertThat(m.getParameterCount()).isEqualTo(1);
        assertThat(m.getParameterTypes()[0]).isEqualTo(String.class);
    }

    @Test
    public void deleteTasks_takesNoParameterAndReturnsVoid() throws Exception {
        Method m = CleanCommand.class.getMethod("deleteTasks");
        assertThat(m.getParameterCount()).isZero();
        assertThat(m.getReturnType()).isEqualTo(void.class);
    }

    @Test
    public void cleanCommandExecute_takesNoDryRunParameter() throws Exception {
        Method m = CleanCommand.class.getMethod("execute");
        assertThat(m.getParameterCount()).isZero();
    }
}
