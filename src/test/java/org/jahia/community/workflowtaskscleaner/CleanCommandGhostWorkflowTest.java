package org.jahia.community.workflowtaskscleaner;

import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.workflow.Workflow;
import org.jahia.services.workflow.WorkflowService;
import org.jahia.services.workflow.WorkflowTask;
import org.junit.Test;
import org.mockito.MockedStatic;

import javax.jcr.ItemNotFoundException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S13 (G1 / U2, D5) — SAFETY HEADLINE, CHARACTERIZATION.
 *
 * <p>The ghost-cleanup phase aborts a workflow whenever the {@code nodeIds} it references no longer
 * resolve, with <b>NO status gate at all</b> — the code never reads the workflow status
 * ({@code CleanCommand.java:87-99}). So an <em>active/pending</em> publication workflow whose
 * content was deleted is aborted identically to a terminal one. This pins the bug (and the defensive
 * skips) so the Stage-7 fix (terminal-status gate and/or dry-run) can flip the headline assertion to
 * {@code never().abortProcess(...)}.</p>
 *
 * <p>Driven against the behaviour-preserving package-private seam
 * {@code CleanCommand.processGhostTasks(JCRSessionWrapper)} — the abort decision is exercised
 * directly with a mocked session + {@code MockedStatic<WorkflowService>}, avoiding the unmockable
 * {@code JCRUserNode}/{@code JCRTemplate.doExecute} chain (which is orthogonal to the abort logic).
 * Stage 7 can promote this seam to a full injectable {@code GhostWorkflowCleaner} collaborator.</p>
 */
public class CleanCommandGhostWorkflowTest {

    private static final String PROVIDER = "jBPM";
    private static final String PROCESS_ID = "42";
    private static final String WORKFLOW_ID = "42";

    private static WorkflowTask task(String processId) {
        WorkflowTask task = mock(WorkflowTask.class);
        when(task.getProvider()).thenReturn(PROVIDER);
        when(task.getProcessId()).thenReturn(processId);
        return task;
    }

    private static Workflow workflow(String id, Object nodeIdsVar) {
        Workflow w = mock(Workflow.class);
        Map<String, Object> vars = new HashMap<>();
        vars.put("nodeIds", nodeIdsVar);
        when(w.getVariables()).thenReturn(vars);
        when(w.getId()).thenReturn(id);
        when(w.getProvider()).thenReturn(PROVIDER);
        return w;
    }

    private static JCRSessionWrapper sessionWith(List<WorkflowTask> tasks, WorkflowService ws) {
        JCRSessionWrapper session = mock(JCRSessionWrapper.class);
        JahiaUser user = mock(JahiaUser.class);
        when(session.getUser()).thenReturn(user);
        when(ws.getTasksForUser(any(), eq(Locale.ENGLISH))).thenReturn(tasks);
        return session;
    }

    // --- HEADLINE: aborts with no status gate when the referenced node is gone ---
    @Test
    public void ghostCleanup_abortsWorkflow_withoutAnyStatusCheck_whenNodeDeleted() throws Exception {
        try (MockedStatic<WorkflowService> mw = mockStatic(WorkflowService.class)) {
            WorkflowService ws = mock(WorkflowService.class);
            mw.when(WorkflowService::getInstance).thenReturn(ws);

            WorkflowTask task = task(PROCESS_ID);
            JCRSessionWrapper session = sessionWith(Collections.singletonList(task), ws);
            Workflow w = workflow(WORKFLOW_ID, Arrays.asList("uuid-1"));
            when(ws.getWorkflow(PROVIDER, PROCESS_ID, Locale.ENGLISH)).thenReturn(w);
            when(session.getNodeByIdentifier("uuid-1")).thenThrow(new ItemNotFoundException("gone"));

            CleanCommand.processGhostTasks(session);

            // No status was ever read; abort fires regardless. THIS IS THE BUG.
            verify(ws).abortProcess(WORKFLOW_ID, PROVIDER);
        }
    }

    // --- Negative: node still exists -> never abort ---
    @Test
    public void ghostCleanup_doesNotAbort_whenReferencedNodeStillExists() throws Exception {
        try (MockedStatic<WorkflowService> mw = mockStatic(WorkflowService.class)) {
            WorkflowService ws = mock(WorkflowService.class);
            mw.when(WorkflowService::getInstance).thenReturn(ws);

            WorkflowTask task = task(PROCESS_ID);
            JCRSessionWrapper session = sessionWith(Collections.singletonList(task), ws);
            Workflow w = workflow(WORKFLOW_ID, Arrays.asList("uuid-1"));
            when(ws.getWorkflow(PROVIDER, PROCESS_ID, Locale.ENGLISH)).thenReturn(w);
            when(session.getNodeByIdentifier("uuid-1")).thenReturn(mock(JCRNodeWrapper.class));

            CleanCommand.processGhostTasks(session);

            verify(ws, never()).abortProcess(any(), any());
        }
    }

    // --- Negative: nodeIds variable missing / not a List -> defensive skip, never abort ---
    @Test
    public void ghostCleanup_doesNotAbort_whenNodeIdsVariableIsNotAList() throws Exception {
        try (MockedStatic<WorkflowService> mw = mockStatic(WorkflowService.class)) {
            WorkflowService ws = mock(WorkflowService.class);
            mw.when(WorkflowService::getInstance).thenReturn(ws);

            WorkflowTask task = task(PROCESS_ID);
            JCRSessionWrapper session = sessionWith(Collections.singletonList(task), ws);
            Workflow w = workflow(WORKFLOW_ID, "not-a-list");
            when(ws.getWorkflow(PROVIDER, PROCESS_ID, Locale.ENGLISH)).thenReturn(w);

            CleanCommand.processGhostTasks(session);

            verify(ws, never()).abortProcess(any(), any());
        }
    }

    // --- Resilience: one bad task throws RuntimeException -> the batch continues to the next ---
    @Test
    public void ghostCleanup_continuesBatch_whenOneTaskProcessingThrows() throws Exception {
        try (MockedStatic<WorkflowService> mw = mockStatic(WorkflowService.class)) {
            WorkflowService ws = mock(WorkflowService.class);
            mw.when(WorkflowService::getInstance).thenReturn(ws);

            WorkflowTask bad = task(PROCESS_ID);
            WorkflowTask good = task("99");
            JCRSessionWrapper session = sessionWith(Arrays.asList(bad, good), ws);

            // First task blows up inside processWorkflowTask.
            when(ws.getWorkflow(PROVIDER, PROCESS_ID, Locale.ENGLISH)).thenThrow(new RuntimeException("bad task"));
            // Second task is a genuine ghost and must still be aborted.
            Workflow goodWf = workflow("99", Arrays.asList("uuid-2"));
            when(ws.getWorkflow(PROVIDER, "99", Locale.ENGLISH)).thenReturn(goodWf);
            when(session.getNodeByIdentifier("uuid-2")).thenThrow(new ItemNotFoundException("gone"));

            CleanCommand.processGhostTasks(session);

            verify(ws, times(1)).abortProcess("99", PROVIDER);
            verify(ws, never()).abortProcess(eq(WORKFLOW_ID), any());
        }
    }
}
