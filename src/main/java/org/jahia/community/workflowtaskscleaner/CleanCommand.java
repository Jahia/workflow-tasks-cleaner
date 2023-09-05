package org.jahia.community.workflowtaskscleaner;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import javax.jcr.RepositoryException;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.jahia.settings.SettingsBean;
import org.jahia.utils.DatabaseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "workflow-cleaner", name = "clean", description = "Delete all exited and completed tasks")
@Service
public class CleanCommand implements Action {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(CleanCommand.class);
    private static final String HUMAN_READABLE_FORMAT = "d' days 'H' hours 'm' minutes 's' seconds'";
    private static final List<String> SQL_STMTS = Arrays.asList(
            "DELETE FROM jbpm_people_assignm_pot_owners WHERE task_id IN (SELECT task_id FROM jbpm_task WHERE (status='Completed' OR status='Exited') AND PROCESS_INSTANCE_ID NOT IN (SELECT id FROM jbpm_process_instance_info))",
            "DELETE FROM jbpm_people_assignments_bas WHERE task_id IN (SELECT task_id FROM jbpm_task WHERE (status='Completed' OR status='Exited') AND PROCESS_INSTANCE_ID NOT IN (SELECT id FROM jbpm_process_instance_info))",
            "DELETE FROM jbpm_i18ntext WHERE task_subjects_id IN (SELECT task_subjects_id FROM jbpm_task WHERE (status='Completed' OR status='Exited') AND PROCESS_INSTANCE_ID NOT IN (SELECT id FROM jbpm_process_instance_info))",
            "DELETE FROM jbpm_i18ntext WHERE task_names_id IN (SELECT task_names_id FROM jbpm_task WHERE (status='Completed' OR status='Exited') AND PROCESS_INSTANCE_ID NOT IN (SELECT id FROM jbpm_process_instance_info))",
            "DELETE FROM jbpm_i18ntext WHERE task_descriptions_id IN (SELECT task_descriptions_id FROM jbpm_task WHERE (status='Completed' OR status='Exited') AND PROCESS_INSTANCE_ID NOT IN (SELECT id FROM jbpm_process_instance_info))",
            "DELETE FROM jbpm_task WHERE (status='Completed' OR status='Exited') AND PROCESS_INSTANCE_ID NOT IN (SELECT id FROM jbpm_process_instance_info)"
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
        }
    }
}
