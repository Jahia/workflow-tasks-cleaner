package org.jahia.community.workflowtaskscleaner;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.ShellUtil;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.ShellTable;
import org.jahia.api.Constants;
import org.jahia.api.usermanager.JahiaUserManagerService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.workflow.WorkflowService;
import org.jahia.services.workflow.jbpm.JBPM6WorkflowProvider;
import org.jahia.settings.SettingsBean;
import org.jahia.utils.DatabaseUtils;
import org.jbpm.services.task.impl.model.GroupImpl;
import org.jbpm.services.task.impl.model.UserImpl;
import org.jbpm.shared.services.api.JbpmServicesPersistenceManager;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.OrganizationalEntity;
import org.kie.api.task.model.PeopleAssignments;
import org.kie.api.task.model.Task;
import org.kie.internal.task.api.model.InternalPeopleAssignments;
import org.kie.internal.task.api.model.InternalTask;

import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFactory;
import javax.jcr.query.Query;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "workflow-cleaner", name = "task", description = "List all current workflow tasks")
@Service
public class WorkflowTaskCommand implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowTaskCommand.class);
    private static final String STATUS_LABEL = "Status";

    @Reference
    WorkflowService workflowService;

    @Reference
    JahiaUserManagerService userManagerService;

    @Reference
    JCRTemplate jcrTemplate;

    @Option(name = "-id", description = "The task id", required = true, valueToShowInHelp = "1")
    private Long taskId;

    @Option(name = "--fix", description = "try to fix the task (reassign groups/users)")
    private boolean toBeFixed;

    @Option(name = "--delete", description = "delete the task")
    private boolean toBeDeleted;

    @Override
    @SuppressWarnings({"java:S3011", "java:S106", "java:S2139"})
    public Object execute() throws RepositoryException {
        Locale locale = SettingsBean.getInstance().getDefaultLocale();
        try {
            JBPM6WorkflowProvider jBPM = (JBPM6WorkflowProvider) workflowService.getProviders().get("jBPM");
            Class<? extends JBPM6WorkflowProvider> jBPMClass = jBPM.getClass();
            Field runtimeEngineField = jBPMClass.getDeclaredField("runtimeEngine");
            runtimeEngineField.setAccessible(true);
            RuntimeEngine runtimeEngine = (RuntimeEngine) runtimeEngineField.get(jBPM);
            Field jbpmServicesPersistenceManagerField = jBPMClass.getDeclaredField("jbpmServicesPersistenceManager");
            jbpmServicesPersistenceManagerField.setAccessible(true);
            JbpmServicesPersistenceManager jbpmServicesPersistenceManager = (JbpmServicesPersistenceManager) jbpmServicesPersistenceManagerField.get(jBPM);
            jbpmServicesPersistenceManager.beginTransaction();
            try {
                TaskService taskService = runtimeEngine.getTaskService();
                Task taskById = taskService.getTaskById(taskId);
                LOGGER.info("Task: {}", taskById.getTaskData().getStatus().name());
                ShellTable taskTable = new ShellTable();
                taskTable.column(new Col("ProcessInstanceId"));
                taskTable.column(new Col("Id"));
                taskTable.column(new Col("Type"));
                taskTable.column(new Col("PotentialOwners"));
                taskTable.column(new Col(STATUS_LABEL));
                taskTable.column(new Col("CreatedOn"));
                taskTable.addRow().addContent(
                        taskById.getTaskData().getProcessInstanceId(),
                        taskById.getId(),
                        taskById.getTaskType(),
                        ShellUtil.getValueString(taskById.getPeopleAssignments().getPotentialOwners()),
                        taskById.getTaskData().getStatus().name(),
                        taskById.getTaskData().getCreatedOn()
                );
                taskTable.print(System.out);
                boolean tryToReassignPeoples = false;
                if (taskById.getPeopleAssignments().getPotentialOwners().isEmpty()) {
                    LOGGER.info("No potential owners");
                    tryToReassignPeoples = true;
                }
                if (taskById.getPeopleAssignments().getBusinessAdministrators().isEmpty()) {
                    LOGGER.info("No business administrators");
                    tryToReassignPeoples = true;
                }
                if (toBeFixed && tryToReassignPeoples) {
                    fixTask(locale, taskById, jbpmServicesPersistenceManager);
                }
                if (!toBeFixed && toBeDeleted) {
                    LOGGER.info("Deleting task {}", taskId);
                    deleteTask(taskId);
                }
            } finally {
                jbpmServicesPersistenceManager.endTransaction(true);
            }
        } catch (IllegalAccessException | NoSuchFieldException | SQLException | RepositoryException e) {
            LOGGER.error("Failed to execute task command", e);
            throw new IllegalStateException("Failed to execute task command", e);
        }
        return null;
    }

    private void fixTask(Locale locale, Task taskById, JbpmServicesPersistenceManager jbpmServicesPersistenceManager) throws RepositoryException {
        jcrTemplate.doExecuteWithSystemSessionAsUser(userManagerService.lookupRootUser().getJahiaUser(), Constants.EDIT_WORKSPACE, locale, session -> {
            Query query = session.getWorkspace().getQueryManager().createQuery("SELECT * FROM [jnt:workflowTask] WHERE [taskId] = $taskId", Query.JCR_SQL2);
            ValueFactory vf = session.getValueFactory();
            query.bindValue("taskId", vf.createValue(taskId));
            NodeIterator nodes = query.execute().getNodes();
            while (nodes.hasNext()) {
                JCRNodeWrapper node = (JCRNodeWrapper) nodes.nextNode();
                LOGGER.info("Node: {}", node.getPath());
                LOGGER.info("TaskId: {}", node.getProperty("taskId").getLong());
                JCRValueWrapper[] candidates = node.getProperty("candidates").getValues();
                LOGGER.info("Candidates = {}", ShellUtil.getValueString(candidates));
                List<OrganizationalEntity> potentialOwners = buildPotentialOwners(candidates);
                updateTaskAssignments(taskById, potentialOwners, jbpmServicesPersistenceManager);
            }
            return null;
        });
    }

    @SuppressWarnings("java:S2139")
    private List<OrganizationalEntity> buildPotentialOwners(JCRValueWrapper[] candidates) {
        List<OrganizationalEntity> potentialOwners = new ArrayList<>();
        try {
            for (JCRValueWrapper candidate : candidates) {
                String candidateString = candidate.getString();
                if (candidateString.startsWith("/users")) {
                    potentialOwners.add(new UserImpl(candidateString));
                } else {
                    potentialOwners.add(new GroupImpl(candidateString));
                }
            }
        } catch (RepositoryException e) {
            LOGGER.error("Failed to build potential owners", e);
            throw new IllegalStateException("Failed to build potential owners", e);
        }
        return potentialOwners;
    }

    private void updateTaskAssignments(Task taskById, List<OrganizationalEntity> potentialOwners, JbpmServicesPersistenceManager jbpmServicesPersistenceManager) {
        PeopleAssignments peopleAssignments = taskById.getPeopleAssignments();
        if (peopleAssignments instanceof InternalPeopleAssignments) {
            ((InternalPeopleAssignments) peopleAssignments).setPotentialOwners(potentialOwners);
        } else {
            throw new IllegalArgumentException("Unsupported PeopleAssignments implementation");
        }

        if (taskById instanceof InternalTask) {
            ((InternalTask) taskById).setPeopleAssignments(peopleAssignments);
            jbpmServicesPersistenceManager.persist(taskById);
        } else {
            throw new IllegalArgumentException("Unsupported Task implementation");
        }
    }

    private static void deleteTask(long taskId) throws SQLException {
        DataSource ds = DatabaseUtils.getDatasource();
        String[] sqls = {
                "DELETE FROM jbpm_people_assignm_pot_owners WHERE task_id = ?",
                "DELETE FROM jbpm_people_assignments_bas WHERE task_id = ?",
                "DELETE FROM jbpm_i18ntext WHERE task_subjects_id = ?",
                "DELETE FROM jbpm_i18ntext WHERE task_names_id = ?",
                "DELETE FROM jbpm_i18ntext WHERE task_descriptions_id = ?",
                "DELETE FROM jbpm_task WHERE id = ?"
        };
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (String sql : sqls) {
                    executeDelete(conn, sql, taskId);
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static void executeDelete(Connection conn, String sql, long taskId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, taskId);
            ps.executeUpdate();
        }
    }

}
