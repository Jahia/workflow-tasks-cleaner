package org.jahia.community.workflowtaskscleaner;

import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.workflow.Workflow;
import org.jahia.services.workflow.WorkflowService;
import org.jahia.services.workflow.WorkflowTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import java.util.List;
import java.util.Locale;

/**
 * Ghost-workflow cleanup collaborator (U2 SAFETY fix, extracted from {@code CleanCommand}).
 *
 * <p>A "ghost" workflow is one whose referenced content nodes ({@code nodeIds} variable) no longer
 * resolve in the repository. The pre-fix code aborted <b>any</b> such workflow unconditionally, with
 * no status check — silently killing <em>active/in-progress</em> publication workflows whose target
 * was deleted (SAFETY headline bug). This collaborator adds two independent safeguards:</p>
 *
 * <ol>
 *   <li><b>Status gate</b> ({@link #isAbortEligible(Workflow)}): a ghost workflow is only ever a
 *       candidate for abort when it is in a terminal-eligible state (Completed/Aborted). Active,
 *       Pending and Suspended workflows are <b>never</b> aborted. Because Jahia's {@link Workflow}
 *       object carries no status and {@code WorkflowService.getWorkflow(...)} only ever returns
 *       <em>active</em> instances, the shipped {@link #DEFAULT_STATUS_RESOLVER} conservatively
 *       reports every reachable workflow as Active (i.e. protected). A caller/environment with a
 *       reliable terminal-status oracle can inject a real resolver via the package-private
 *       constructor — that is exactly the seam the unit tests use.</li>
 *   <li><b>Report-only (dry-run) mode</b>: the default. Instead of aborting, it logs the workflows
 *       that <em>would</em> be aborted. This is what keeps the unattended 2:30 AM scheduled job from
 *       silently aborting anything. Enforcement is an explicit opt-in
 *       ({@code abortGhostWorkflows=true} in the OSGi configuration).</li>
 * </ol>
 */
public class GhostWorkflowCleaner {

    private static final Logger LOGGER = LoggerFactory.getLogger(GhostWorkflowCleaner.class);
    private static final String NODE_IDS_VAR = "nodeIds";

    /**
     * Resolves a workflow's jBPM process-instance state, mirroring the integer values of
     * {@link WorkflowListCommand.WorkflowStatus} (0=Pending, 1=Active, 2=Completed, 3=Aborted,
     * 4=Suspended). Return a value outside that range to mean "unknown" (treated as not eligible).
     */
    @FunctionalInterface
    public interface WorkflowStatusResolver {
        int resolveState(Workflow workflow);
    }

    /**
     * Conservative default: a workflow's terminal status cannot be determined from the
     * {@link Workflow} object alone, and {@code getWorkflow(...)} only returns live instances, so
     * every reachable workflow is reported as Active and therefore protected from abort.
     */
    static final WorkflowStatusResolver DEFAULT_STATUS_RESOLVER =
            w -> WorkflowListCommand.WorkflowStatus.STATE_ACTIVE.getValue();

    private final boolean reportOnly;
    private final WorkflowStatusResolver statusResolver;

    /** Safe production default: report-only + conservative status gate (never aborts). */
    public GhostWorkflowCleaner() {
        this(true, DEFAULT_STATUS_RESOLVER);
    }

    public GhostWorkflowCleaner(boolean reportOnly) {
        this(reportOnly, DEFAULT_STATUS_RESOLVER);
    }

    GhostWorkflowCleaner(boolean reportOnly, WorkflowStatusResolver statusResolver) {
        this.reportOnly = reportOnly;
        this.statusResolver = statusResolver;
    }

    /**
     * Enumerates the session user's workflow tasks and evaluates each for ghost cleanup. A single
     * bad task is skipped (its {@link RuntimeException} is logged) so the batch continues; a checked
     * {@link RepositoryException} (e.g. a dead session) propagates to abort the whole batch.
     */
    public void process(JCRSessionWrapper session) throws RepositoryException {
        JahiaUser user = session.getUser();
        List<WorkflowTask> tasks = WorkflowService.getInstance().getTasksForUser(user, Locale.ENGLISH);
        for (WorkflowTask task : tasks) {
            try {
                processWorkflowTask(task, session);
            } catch (RuntimeException e) {
                LOGGER.warn("Skipping task {}: processing failed", task.getId(), e);
            }
        }
    }

    void processWorkflowTask(WorkflowTask task, JCRSessionWrapper session) throws RepositoryException {
        Workflow w = WorkflowService.getInstance().getWorkflow(task.getProvider(), task.getProcessId(), Locale.ENGLISH);
        Object raw = w.getVariables().get(NODE_IDS_VAR);
        if (!(raw instanceof List)) {
            LOGGER.warn("Skipping workflow {}: nodeIds missing or not a List", w.getId());
            return;
        }
        @SuppressWarnings("unchecked")
        List<String> nodeIds = (List<String>) raw;
        if (hasExistingNodes(nodeIds, session)) {
            return; // not a ghost — at least one referenced node still resolves
        }

        // Ghost detected: the referenced nodes are gone. Apply the SAFETY status gate before doing
        // anything destructive — an active/in-progress workflow must NEVER be aborted here.
        if (!isAbortEligible(w)) {
            LOGGER.warn("Ghost workflow {} ({}) references deleted nodes but is NOT in a terminal-eligible"
                    + " state; NOT aborting (active/pending/suspended workflows are protected)",
                    w.getId(), w.getProvider());
            return;
        }

        if (reportOnly) {
            LOGGER.info("[REPORT-ONLY] Ghost workflow {} ({}) would be aborted (referenced nodes deleted)."
                    + " Set abortGhostWorkflows=true to enforce.", w.getId(), w.getProvider());
            return;
        }

        LOGGER.info("Aborting terminal-eligible ghost workflow {} ({})", w.getId(), w.getProvider());
        WorkflowService.getInstance().abortProcess(w.getId(), w.getProvider());
    }

    /**
     * SAFETY status gate: a ghost workflow may only be aborted when it is in a terminal-eligible
     * state (Completed or Aborted). Active/Pending/Suspended in-progress workflows are protected.
     */
    boolean isAbortEligible(Workflow w) {
        int state = statusResolver.resolveState(w);
        return state == WorkflowListCommand.WorkflowStatus.STATE_COMPLETED.getValue()
                || state == WorkflowListCommand.WorkflowStatus.STATE_ABORTED.getValue();
    }

    private static boolean hasExistingNodes(List<String> nodeIds, JCRSessionWrapper session) throws RepositoryException {
        for (String nodeId : nodeIds) {
            try {
                session.getNodeByIdentifier(nodeId);
                return true;
            } catch (ItemNotFoundException e) {
                // Node not found, continue to next node
            }
        }
        return false;
    }
}
