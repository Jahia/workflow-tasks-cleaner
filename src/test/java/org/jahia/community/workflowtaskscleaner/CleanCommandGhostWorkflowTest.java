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
 * S13 (G1 / U2) — SAFETY HEADLINE, now proving the <b>Stage-7 fix</b> (previously a characterization
 * of the bug). Ghost-cleanup abort is guarded by TWO independent safeguards, exercised against the
 * extracted {@link GhostWorkflowCleaner} collaborator (the Stage-4 recommendation to de-brittle S13):
 *
 * <ol>
 *   <li><b>Status gate</b>: an active/pending/suspended workflow whose target node was deleted is
 *       <b>NEVER</b> aborted — only a terminal-eligible (Completed/Aborted) ghost may be.</li>
 *   <li><b>Report-only default</b>: even a terminal-eligible ghost is only <em>logged</em> unless the
 *       cleaner is constructed in enforce mode.</li>
 * </ol>
 *
 * <p>The abort decision is driven directly with a mocked session + {@code MockedStatic<WorkflowService>}
 * and an injected {@link GhostWorkflowCleaner.WorkflowStatusResolver}, avoiding the unmockable
 * {@code JCRUserNode}/{@code JCRTemplate.doExecute} chain.</p>
 */
public class CleanCommandGhostWorkflowTest {

    private static final String PROVIDER = "jBPM";
    private static final String PROCESS_ID = "42";
    private static final String WORKFLOW_ID = "42";

    private static final int ACTIVE = WorkflowListCommand.WorkflowStatus.STATE_ACTIVE.getValue();
    private static final int COMPLETED = WorkflowListCommand.WorkflowStatus.STATE_COMPLETED.getValue();

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

    // Enforce mode + a resolver that reports the given jBPM state.
    private static GhostWorkflowCleaner enforcing(int state) {
        return new GhostWorkflowCleaner(false, w -> state);
    }

    // --- SAFETY HEADLINE: an ACTIVE workflow is NEVER aborted, even when its node is gone. ---
    @Test
    public void ghostCleanup_doesNotAbortActiveWorkflow_evenWhenNodeDeleted_enforceMode() throws Exception {
        try (MockedStatic<WorkflowService> mw = mockStatic(WorkflowService.class)) {
            WorkflowService ws = mock(WorkflowService.class);
            mw.when(WorkflowService::getInstance).thenReturn(ws);

            WorkflowTask task = task(PROCESS_ID);
            JCRSessionWrapper session = sessionWith(Collections.singletonList(task), ws);
            Workflow w = workflow(WORKFLOW_ID, Arrays.asList("uuid-1"));
            when(ws.getWorkflow(PROVIDER, PROCESS_ID, Locale.ENGLISH)).thenReturn(w);
            when(session.getNodeByIdentifier("uuid-1")).thenThrow(new ItemNotFoundException("gone"));

            enforcing(ACTIVE).process(session);

            // Status gate protects the active workflow — the old unconditional abort was the bug.
            verify(ws, never()).abortProcess(any(), any());
        }
    }

    // --- Enforce mode + terminal-eligible ghost -> aborted. ---
    @Test
    public void ghostCleanup_abortsTerminalEligibleGhost_inEnforceMode() throws Exception {
        try (MockedStatic<WorkflowService> mw = mockStatic(WorkflowService.class)) {
            WorkflowService ws = mock(WorkflowService.class);
            mw.when(WorkflowService::getInstance).thenReturn(ws);

            WorkflowTask task = task(PROCESS_ID);
            JCRSessionWrapper session = sessionWith(Collections.singletonList(task), ws);
            Workflow w = workflow(WORKFLOW_ID, Arrays.asList("uuid-1"));
            when(ws.getWorkflow(PROVIDER, PROCESS_ID, Locale.ENGLISH)).thenReturn(w);
            when(session.getNodeByIdentifier("uuid-1")).thenThrow(new ItemNotFoundException("gone"));

            enforcing(COMPLETED).process(session);

            verify(ws).abortProcess(WORKFLOW_ID, PROVIDER);
        }
    }

    // --- Report-only default: even a terminal-eligible ghost is NOT aborted (only logged). ---
    @Test
    public void ghostCleanup_reportOnly_doesNotAbortEvenTerminalEligibleGhost() throws Exception {
        try (MockedStatic<WorkflowService> mw = mockStatic(WorkflowService.class)) {
            WorkflowService ws = mock(WorkflowService.class);
            mw.when(WorkflowService::getInstance).thenReturn(ws);

            WorkflowTask task = task(PROCESS_ID);
            JCRSessionWrapper session = sessionWith(Collections.singletonList(task), ws);
            Workflow w = workflow(WORKFLOW_ID, Arrays.asList("uuid-1"));
            when(ws.getWorkflow(PROVIDER, PROCESS_ID, Locale.ENGLISH)).thenReturn(w);
            when(session.getNodeByIdentifier("uuid-1")).thenThrow(new ItemNotFoundException("gone"));

            // report-only cleaner, resolver WOULD make it eligible — still must not abort.
            new GhostWorkflowCleaner(true, w2 -> COMPLETED).process(session);

            verify(ws, never()).abortProcess(any(), any());
        }
    }

    // --- The shipped default constructor is report-only + conservative gate (never aborts). ---
    @Test
    public void ghostCleanup_defaultConstructor_isSafeAndNeverAborts() throws Exception {
        try (MockedStatic<WorkflowService> mw = mockStatic(WorkflowService.class)) {
            WorkflowService ws = mock(WorkflowService.class);
            mw.when(WorkflowService::getInstance).thenReturn(ws);

            WorkflowTask task = task(PROCESS_ID);
            JCRSessionWrapper session = sessionWith(Collections.singletonList(task), ws);
            Workflow w = workflow(WORKFLOW_ID, Arrays.asList("uuid-1"));
            when(ws.getWorkflow(PROVIDER, PROCESS_ID, Locale.ENGLISH)).thenReturn(w);
            when(session.getNodeByIdentifier("uuid-1")).thenThrow(new ItemNotFoundException("gone"));

            new GhostWorkflowCleaner().process(session);

            verify(ws, never()).abortProcess(any(), any());
        }
    }

    // --- Negative: node still exists -> never abort (even in enforce mode). ---
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

            enforcing(COMPLETED).process(session);

            verify(ws, never()).abortProcess(any(), any());
        }
    }

    // --- Negative: nodeIds variable missing / not a List -> defensive skip, never abort. ---
    @Test
    public void ghostCleanup_doesNotAbort_whenNodeIdsVariableIsNotAList() throws Exception {
        try (MockedStatic<WorkflowService> mw = mockStatic(WorkflowService.class)) {
            WorkflowService ws = mock(WorkflowService.class);
            mw.when(WorkflowService::getInstance).thenReturn(ws);

            WorkflowTask task = task(PROCESS_ID);
            JCRSessionWrapper session = sessionWith(Collections.singletonList(task), ws);
            Workflow w = workflow(WORKFLOW_ID, "not-a-list");
            when(ws.getWorkflow(PROVIDER, PROCESS_ID, Locale.ENGLISH)).thenReturn(w);

            enforcing(COMPLETED).process(session);

            verify(ws, never()).abortProcess(any(), any());
        }
    }

    // --- Resilience: one bad task throws RuntimeException -> the batch continues to the next. ---
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
            // Second task is a terminal-eligible ghost and must still be aborted (enforce mode).
            Workflow goodWf = workflow("99", Arrays.asList("uuid-2"));
            when(ws.getWorkflow(PROVIDER, "99", Locale.ENGLISH)).thenReturn(goodWf);
            when(session.getNodeByIdentifier("uuid-2")).thenThrow(new ItemNotFoundException("gone"));

            enforcing(COMPLETED).process(session);

            verify(ws, times(1)).abortProcess("99", PROVIDER);
            verify(ws, never()).abortProcess(eq(WORKFLOW_ID), any());
        }
    }
}
