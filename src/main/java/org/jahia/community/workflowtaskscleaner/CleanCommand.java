package org.jahia.community.workflowtaskscleaner;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jahia.api.Constants;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.services.workflow.Workflow;
import org.jahia.services.workflow.WorkflowService;
import org.jahia.services.workflow.WorkflowTask;
import org.jahia.settings.SettingsBean;
import org.jahia.utils.DatabaseUtils;
import org.jbpm.services.task.impl.model.GroupImpl;
import org.jbpm.services.task.impl.model.UserImpl;
import org.kie.api.task.model.OrganizationalEntity;
import org.kie.api.task.model.PeopleAssignments;
import org.kie.internal.task.api.model.InternalPeopleAssignments;
import org.kie.internal.task.api.model.InternalTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Command(scope = "workflow-cleaner", name = "clean", description = "Delete all exited and completed tasks")
@Service
public class CleanCommand implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(CleanCommand.class);
    private static final String HUMAN_READABLE_FORMAT = "d' days 'H' hours 'm' minutes 's' seconds'";
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
        if (SettingsBean.getInstance().isProcessingServer()) {
            try {
                LOGGER.info("Starting to delete exited and completed workflow tasks");
                final long start = System.currentTimeMillis();
                DatabaseUtils.executeStatements(SQL_STMTS);
                final long end = System.currentTimeMillis();
                LOGGER.info(String.format("Finished to delete exited and completed workflow tasks in %s", DurationFormatUtils.formatDuration(end - start, HUMAN_READABLE_FORMAT, true)));
            } catch (SQLException ex) {
                LOGGER.error("Impossible to clean workflow tasks", ex);
            }
            LOGGER.info("Starting to clean ghost workflows");
            cleanGhostWorkflows();
            LOGGER.info("Ghost workflows cleaned");
            
        }
    }
    
    private static void cleanGhostWorkflows() throws RepositoryException {
	JCRTemplate.getInstance().doExecute(JahiaUserManagerService.getInstance().lookupRootUser().getJahiaUser(), "default", Locale.ENGLISH,
			new JCRCallback<Object>() {
				public Object doInJCR(JCRSessionWrapper session) throws RepositoryException {
			    	JahiaUser user = session.getUser();
			        List<WorkflowTask> tasks = WorkflowService.getInstance().getTasksForUser(user, Locale.ENGLISH);
			        for (WorkflowTask task : tasks) {
			            Workflow w = WorkflowService.getInstance().getWorkflow(task.getProvider(), task.getProcessId(), Locale.ENGLISH);
			            List<String> nodeIds = (List<String>) w.getVariables().get("nodeIds");
			            if (nodeIds != null) {
			                boolean ok = false;
			                for (String nodeId : nodeIds) {
			                    try {
			                        session.getNodeByIdentifier(nodeId);
			                        ok = true;
			                        break;
			                    } catch (ItemNotFoundException e) {
			                    }
			                }
			                if (!ok) {
			                    WorkflowService.getInstance().abortProcess(w.getId(), w.getProvider());
			                }
			            }
			        }
					return null;
				}
			});
    }
}
