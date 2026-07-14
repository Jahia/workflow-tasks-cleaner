package org.jahia.community.workflowtaskscleaner;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.settings.SettingsBean;
import org.jahia.utils.DatabaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@Command(scope = "workflow-cleaner", name = "clean", description = "Delete all exited and completed tasks")
@Service
public class CleanCommand implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanCommand.class);
    private static final String HUMAN_READABLE_FORMAT = "d' days 'H' hours 'm' minutes 's' seconds'";

    // U3 — single, shared concurrency guard honoured by ALL deletion entry points (GraphQL runClean,
    // the Quartz job and the Karaf clean command), because they all funnel through deleteTasks().
    // NOTE (cluster caveat): this is a JVM-local flag; SettingsBean.isProcessingServer() confines
    // destructive work to one node but does NOT provide same-node-across-cluster mutual exclusion.
    static final AtomicBoolean IS_RUNNING = new AtomicBoolean(false);

    // U2 — report-only (dry-run) by default; ghost-workflow abort is only enforced when an operator
    // explicitly opts in via the OSGi configuration (abortGhostWorkflows=true), pushed here by the
    // scheduler on @Activate/@Modified.
    private static volatile boolean abortGhostWorkflows = false;
    private static final List<String> SQL_STMTS = Arrays.asList(
            "DELETE FROM jbpm_people_assignm_pot_owners WHERE task_id IN (SELECT id FROM jbpm_task WHERE (status='Completed' OR status='Exited') AND PROCESS_INSTANCE_ID NOT IN (SELECT instance_id FROM jbpm_process_instance_info))",
            "DELETE FROM jbpm_people_assignments_bas WHERE task_id IN (SELECT id FROM jbpm_task WHERE (status='Completed' OR status='Exited') AND PROCESS_INSTANCE_ID NOT IN (SELECT instance_id FROM jbpm_process_instance_info))",
            "DELETE FROM jbpm_i18ntext WHERE task_subjects_id IN (SELECT id FROM jbpm_task WHERE (status='Completed' OR status='Exited') AND PROCESS_INSTANCE_ID NOT IN (SELECT instance_id FROM jbpm_process_instance_info))",
            "DELETE FROM jbpm_i18ntext WHERE task_names_id IN (SELECT id FROM jbpm_task WHERE (status='Completed' OR status='Exited') AND PROCESS_INSTANCE_ID NOT IN (SELECT instance_id FROM jbpm_process_instance_info))",
            "DELETE FROM jbpm_i18ntext WHERE task_descriptions_id IN (SELECT id FROM jbpm_task WHERE (status='Completed' OR status='Exited') AND PROCESS_INSTANCE_ID NOT IN (SELECT instance_id FROM jbpm_process_instance_info))",
            "DELETE FROM jbpm_task WHERE (status='Completed' OR status='Exited') AND PROCESS_INSTANCE_ID NOT IN (SELECT instance_id FROM jbpm_process_instance_info)"
    );

    @Override
    public Object execute() throws RepositoryException {
        deleteTasks();
        return null;
    }

    public static void deleteTasks() throws RepositoryException {
        if (!SettingsBean.getInstance().isProcessingServer()) {
            return;
        }
        // U3 — acquire the shared guard so concurrent invocations from ANY entry point are skipped.
        if (!IS_RUNNING.compareAndSet(false, true)) {
            LOGGER.warn("Workflow tasks cleaner is already running on this node; skipping this invocation");
            return;
        }
        try {
            try {
                LOGGER.info("Starting to delete exited and completed workflow tasks");
                final long start = System.currentTimeMillis();
                DatabaseUtils.executeStatements(SQL_STMTS);
                final long end = System.currentTimeMillis();
                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info("Finished to delete exited and completed workflow tasks in {}", DurationFormatUtils.formatDuration(end - start, HUMAN_READABLE_FORMAT, true));
                }
            } catch (SQLException ex) {
                LOGGER.error("Impossible to clean workflow tasks", ex);
            }
            LOGGER.info("Starting to clean ghost workflows (mode: {})", abortGhostWorkflows ? "ENFORCE" : "REPORT-ONLY");
            cleanGhostWorkflows();
            LOGGER.info("Ghost workflows cleaned");
        } finally {
            IS_RUNNING.set(false);
        }
    }

    static void cleanGhostWorkflows() throws RepositoryException {
        final boolean reportOnly = !abortGhostWorkflows;
        JCRTemplate.getInstance().doExecute(lookupRootJahiaUser(), "default", Locale.ENGLISH,
                new JCRCallback<Object>() {
                    public Object doInJCR(JCRSessionWrapper session) throws RepositoryException {
                        new GhostWorkflowCleaner(reportOnly).process(session);
                        return null;
                    }
                });
    }

    // Package-private seam: isolates the root-user resolution so the ghost-cleanup phase can be
    // exercised without mocking the (unmockable) JCRUserNode returned by lookupRootUser().
    static JahiaUser lookupRootJahiaUser() {
        return JahiaUserManagerService.getInstance().lookupRootUser().getJahiaUser();
    }

    public static boolean isRunning() {
        return IS_RUNNING.get();
    }

    // U2 — set by the scheduler from the OSGi configuration (default false => report-only).
    public static void setAbortGhostWorkflows(boolean value) {
        abortGhostWorkflows = value;
    }

    static boolean isAbortGhostWorkflows() {
        return abortGhostWorkflows;
    }
}
