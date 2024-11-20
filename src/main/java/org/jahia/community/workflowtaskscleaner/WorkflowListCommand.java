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
import org.jetbrains.annotations.NotNull;
import org.kie.api.runtime.manager.RuntimeEngine;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.task.TaskService;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.api.task.model.TaskSummary;

import javax.jcr.RepositoryException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Command(scope = "workflow-cleaner", name = "list", description = "List all current workflow tasks")
@Service
public class WorkflowListCommand implements Action {

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
    public Object execute() throws RepositoryException {
        ShellTable table = getWorkflowInstancesTable();
        Locale locale = SettingsBean.getInstance().getDefaultLocale();
        List<String> instanceIds = new ArrayList<>();
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
            System.out.print("Workflow instance in the system (Jahia/JBPM DB)\n\n");
            table.print(System.out, true);
            table = getWorkflowInstancesTable();
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
            System.out.print("\n\nWorkflow instance in the system (Jahia/JBPM DB) for user " + root.getUserKey() + "\n\n");
            table.print(System.out, true);
            ShellTable taskTable = new ShellTable();
            taskTable.column(new Col("Name"));
            taskTable.column(new Col("Id"));
            taskTable.column(new Col("Assignee"));
            taskTable.column(new Col("Outcomes"));
            taskTable.column(new Col("Participations"));
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
            System.out.print("\n\nTask instance in the system (Jahia/JBPM DB) for user " + root.getUserKey() + "\n\n");
            taskTable.print(System.out, true);


            TaskService taskService = runtimeEngine.getTaskService();
            List<TaskSummary> tasksOwned = taskService.getTasksOwned(root.getLocalPath(), locale.getLanguage());
            taskTable = getShellTableOfTaskSummary(tasksOwned, runtimeEngine);
            System.out.print("\n\nTasks owned by user " + root.getUserKey() + "\n\n");
            taskTable.print(System.out, true);
            List tasksByVariousFields = taskService.getTasksByVariousFields(Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Collections.EMPTY_LIST, Arrays.asList(Status.Ready, Status.Created), true);
            taskTable = getShellTableOfTaskSummary(tasksByVariousFields, runtimeEngine);
            System.out.print("\n\nTasks by status\n\n");
            taskTable.print(System.out, true);
            taskTable = new ShellTable();
            taskTable.column(new Col("ProcessInstanceId"));
            taskTable.column(new Col("Id"));
            taskTable.column(new Col("Type"));
            taskTable.column(new Col("PotentialOwners"));
            taskTable.column(new Col("Status"));
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
            System.out.print("\n\nAll tasks by process \n\n");
            taskTable.print(System.out, true);
            jbpmServicesPersistenceManager.endTransaction(false);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    private static @NotNull ShellTable getShellTableOfTaskSummary(List<TaskSummary> tasksOwned, RuntimeEngine runtimeEngine) {
        ShellTable taskTable;
        taskTable = new ShellTable();
        taskTable.column(new Col("Name"));
        taskTable.column(new Col("Owner"));
        taskTable.column(new Col("ProcessInstanceId"));
        taskTable.column(new Col("Status"));
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

    private static @NotNull ShellTable getWorkflowInstancesTable() {
        ShellTable table = new ShellTable();
        table.column(new Col("Name"));
        table.column(new Col("Type"));
        table.column(new Col("Instance"));
        table.column(new Col("Title"));
        table.column(new Col("NodesIds"));
        table.column(new Col("Status"));
        return table;
    }

}
