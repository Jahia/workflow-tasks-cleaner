package org.jahia.community.workflowtaskscleaner;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.apache.karaf.shell.support.ShellUtil;
import org.apache.karaf.shell.support.table.Col;
import org.apache.karaf.shell.support.table.ShellTable;
import org.jahia.api.usermanager.JahiaUserManagerService;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.workflow.Workflow;
import org.jahia.services.workflow.WorkflowDefinition;
import org.jahia.services.workflow.WorkflowService;
import org.jahia.services.workflow.WorkflowTask;
import org.jahia.services.workflow.jbpm.JBPM6WorkflowProvider;
import org.jahia.settings.SettingsBean;
import org.jbpm.shared.services.api.JbpmServicesPersistenceManager;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;
import org.kie.api.runtime.process.ProcessInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Command(scope = "workflow-cleaner", name = "list", description = "List all current workflow tasks")
@Service
public class WorkflowListCommand implements Action {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowListCommand.class);
    private static final String STATUS_LABEL = "Status";

    @Reference
    WorkflowService workflowService;

    @Reference
    JahiaUserManagerService userManagerService;

    enum WorkflowStatus {
        STATE_PENDING(0, "Pending"),
        STATE_ACTIVE(1, "Active"),
        STATE_COMPLETED(2, "Completed"),
        STATE_ABORTED(3, "Aborted"),
        STATE_SUSPENDED(4, "Suspended");

        private final int value;
        private final String description;

        WorkflowStatus(int value, String description) {
            this.value = value;
            this.description = description;
        }

        public int getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static WorkflowStatus fromValue(int value) {
            for (WorkflowStatus status : WorkflowStatus.values()) {
                if (status.getValue() == value) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Invalid value: " + value);
        }

        public static String getDescriptionByValue(int value) {
            for (WorkflowStatus status : WorkflowStatus.values()) {
                if (status.getValue() == value) {
                    return status.getDescription();
                }
            }
            throw new IllegalArgumentException("Invalid value: " + value);
        }
    }

    @Override
    @SuppressWarnings({"java:S3011", "java:S106", "java:S2139"})
    public Object execute() throws RepositoryException {
        Locale locale = SettingsBean.getInstance().getDefaultLocale();
        JBPM6WorkflowProvider jBPM = (JBPM6WorkflowProvider) workflowService.getProviders().get("jBPM");
        try {
            Class<? extends JBPM6WorkflowProvider> jBPMClass = jBPM.getClass();
            Field runtimeEngineField = jBPMClass.getDeclaredField("runtimeEngine");
            runtimeEngineField.setAccessible(true);
            RuntimeEngine runtimeEngine = (RuntimeEngine) runtimeEngineField.get(jBPM);
            Field jbpmServicesPersistenceManagerField = jBPMClass.getDeclaredField("jbpmServicesPersistenceManager");
            jbpmServicesPersistenceManagerField.setAccessible(true);
            JbpmServicesPersistenceManager jbpmServicesPersistenceManager = (JbpmServicesPersistenceManager) jbpmServicesPersistenceManagerField.get(jBPM);
            jbpmServicesPersistenceManager.beginTransaction();
            try {
                List<String> instanceIds = listAllWorkflowInstances(runtimeEngine, locale);
                listWorkflowInstancesForUser(runtimeEngine, locale);
                listTasksForUser(runtimeEngine, locale);
                listTasksByStatus(runtimeEngine, locale);
                listAllTasksByProcess(runtimeEngine, instanceIds);
            } finally {
                jbpmServicesPersistenceManager.endTransaction(false);
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            LOGGER.error("Failed to access workflow fields", e);
            throw new IllegalStateException("Failed to access workflow fields", e);
        }
        return null;
    }

    @SuppressWarnings({"java:S106", "java:S2139"})
    private List<String> listAllWorkflowInstances(RuntimeEngine runtimeEngine, Locale locale) {
        ShellTable table = getWorkflowInstancesTable();
        List<String> instanceIds = new ArrayList<>();
        try {
            List<WorkflowDefinition> workflows = workflowService.getWorkflows(locale);
            for (WorkflowDefinition workflowDefinition : workflows) {
                List<Workflow> workflowsForType = workflowService.getWorkflowsForDefinition(workflowDefinition.getName(), locale);
                for (Workflow w : workflowsForType) {
                    instanceIds.add(w.getId());
                    table.addRow().addContent(
                            w.getName(),
                            w.getWorkflowDefinition().getDisplayName(),
                            w.getId(),
                            w.getVariables().get("jcr_title"),
                            ShellUtil.getValueString(w.getVariables().get("nodeIds")),
                            WorkflowStatus.getDescriptionByValue(runtimeEngine.getKieSession().getProcessInstance(Long.valueOf(w.getId()), true).getState())
                    );
                }
            }
        } catch (RepositoryException e) {
            LOGGER.error("Failed to list workflow instances", e);
            throw new IllegalStateException("Failed to list workflow instances", e);
        }
        LOGGER.info("Workflow instance in the system (Jahia/JBPM DB)");
        table.print(System.out, true);
        return instanceIds;
    }

    @SuppressWarnings("java:S106")
    private void listWorkflowInstancesForUser(RuntimeEngine runtimeEngine, Locale locale) {
        ShellTable table = getWorkflowInstancesTable();
        JahiaUser root = userManagerService.lookupRootUser().getJahiaUser();
        List<Workflow> workflowsForUser = workflowService.getWorkflowsForUser(root, locale);
        for (Workflow w : workflowsForUser) {
            table.addRow().addContent(
                    w.getName(),
                    w.getWorkflowDefinition().getDisplayName(),
                    w.getId(),
                    w.getVariables().get("jcr_title"),
                    ShellUtil.getValueString(w.getVariables().get("nodeIds")),
                    WorkflowStatus.getDescriptionByValue(runtimeEngine.getKieSession().getProcessInstance(Long.valueOf(w.getId()), true).getState())
            );
        }
        LOGGER.info("Workflow instance in the system (Jahia/JBPM DB) for user {}", root.getUserKey());
        table.print(System.out, true);
    }

    @SuppressWarnings("java:S106")
    private void listTasksForUser(RuntimeEngine runtimeEngine, Locale locale) {
        ShellTable taskTable = new ShellTable();
        taskTable.column(new Col("Name"));
        taskTable.column(new Col("Id"));
        taskTable.column(new Col("Assignee"));
        taskTable.column(new Col("Outcomes"));
        taskTable.column(new Col("Participations"));
        JahiaUser root = userManagerService.lookupRootUser().getJahiaUser();
        List<WorkflowTask> tasksForUser = workflowService.getTasksForUser(root, locale);
        for (WorkflowTask task : tasksForUser) {
            taskTable.addRow().addContent(
                    task.getName(),
                    task.getId(),
                    task.getAssignee() != null ? task.getAssignee().getUserKey() : "not assigned",
                    ShellUtil.getValueString(task.getOutcomes()),
                    ShellUtil.getValueString(task.getParticipations().stream().map(p -> p.getJahiaPrincipal().getLocalPath()).collect(Collectors.toList()))
            );
        }
        LOGGER.info("Workflow tasks in the system (Jahia/JBPM DB) for user {}", root.getUserKey());
        taskTable.print(System.out, true);
    }

    @SuppressWarnings("java:S106")
    private void listTasksByStatus(RuntimeEngine runtimeEngine, Locale locale) {
        TaskService taskService = runtimeEngine.getTaskService();
        JahiaUser root = userManagerService.lookupRootUser().getJahiaUser();
        List<TaskSummary> tasksOwned = taskService.getTasksOwned(root.getLocalPath(), locale.getLanguage());
        ShellTable taskTable = getShellTableOfTaskSummary(tasksOwned, runtimeEngine);
        LOGGER.info("Tasks owned by user {}", root.getUserKey());
        taskTable.print(System.out, true);
        List<TaskSummary> tasksByVariousFields = taskService.getTasksByVariousFields(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Arrays.asList(Status.Ready, Status.Created), true);
        taskTable = getShellTableOfTaskSummary(tasksByVariousFields, runtimeEngine);
        LOGGER.info("Tasks by status");
        taskTable.print(System.out, true);
    }

    @SuppressWarnings("java:S106")
    private void listAllTasksByProcess(RuntimeEngine runtimeEngine, List<String> instanceIds) {
        TaskService taskService = runtimeEngine.getTaskService();
        ShellTable taskTable = new ShellTable();
        taskTable.column(new Col("ProcessInstanceId"));
        taskTable.column(new Col("Id"));
        taskTable.column(new Col("Type"));
        taskTable.column(new Col("PotentialOwners"));
        taskTable.column(new Col(STATUS_LABEL));
        taskTable.column(new Col("CreatedOn"));
        for (String instanceId : instanceIds) {
            List<Long> tasksByProcessInstanceId = taskService.getTasksByProcessInstanceId(Long.valueOf(instanceId));
            for (Long taskId : tasksByProcessInstanceId) {
                Task taskById = taskService.getTaskById(taskId);
                taskTable.addRow().addContent(
                        instanceId,
                        taskById.getId(),
                        taskById.getTaskType(),
                        ShellUtil.getValueString(taskById.getPeopleAssignments().getPotentialOwners()),
                        taskById.getTaskData().getStatus(),
                        taskById.getTaskData().getCreatedOn()
                );
            }
        }
        LOGGER.info("All tasks by process");
        taskTable.print(System.out, true);
    }

    @SuppressWarnings("java:S106")
    private static ShellTable getShellTableOfTaskSummary(List<TaskSummary> tasksOwned, RuntimeEngine runtimeEngine) {
        ShellTable taskTable;
        taskTable = new ShellTable();
        taskTable.column(new Col("Name"));
        taskTable.column(new Col("Owner"));
        taskTable.column(new Col("ProcessInstanceId"));
        taskTable.column(new Col(STATUS_LABEL));
        taskTable.column(new Col("CreatedOn"));
        taskTable.column(new Col("ExpirationTime"));
        taskTable.column(new Col("ProcessId"));
        taskTable.column(new Col("Id"));
        taskTable.column(new Col("PotentialOwners"));
        for (TaskSummary taskSummary : tasksOwned) {
            ProcessInstance processInstance = runtimeEngine.getKieSession().getProcessInstance(taskSummary.getProcessInstanceId(), true);
            taskTable.addRow().addContent(
                    taskSummary.getName(),
                    ShellUtil.getValueString(taskSummary.getActualOwner()),
                    taskSummary.getProcessInstanceId(),
                    processInstance == null ? "Orphan task" : WorkflowStatus.getDescriptionByValue(processInstance.getState()),
                    taskSummary.getStatus(),
                    taskSummary.getCreatedOn(),
                    taskSummary.getExpirationTime(),
                    taskSummary.getProcessId(),
                    taskSummary.getId(),
                    ShellUtil.getValueString(taskSummary.getPotentialOwners())
            );
        }
        return taskTable;
    }

    private static ShellTable getWorkflowInstancesTable() {
        ShellTable table = new ShellTable();
        table.column(new Col("Name"));
        table.column(new Col("Type"));
        table.column(new Col("Instance"));
        table.column(new Col("Title"));
        table.column(new Col("NodesIds"));
        table.column(new Col(STATUS_LABEL));
        return table;
    }

}
