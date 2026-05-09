package org.jahia.community.workflowtaskscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.workflow.Workflow;
import org.jahia.services.workflow.WorkflowDefinition;
import org.jahia.services.workflow.WorkflowService;
import org.jahia.settings.SettingsBean;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLName("WorkflowTasksCleanerQueries")
@GraphQLDescription("Workflow tasks cleaner queries")
public class WorkflowTasksCleanerQueryExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowTasksCleanerQueryExtension.class);
    private static final String CONFIG_PID = "org.jahia.community.workflowtaskscleaner";

    private WorkflowTasksCleanerQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("workflowTasksCleanerConfig")
    @GraphQLDescription("Returns the current workflow tasks cleaner scheduled job configuration")
    @GraphQLRequiresPermission("admin")
    public static GqlConfig config() {
        ConfigurationAdmin configAdmin = BundleUtils.getOsgiService(ConfigurationAdmin.class, null);
        if (configAdmin == null) {
            return new GqlConfig("0 30 2 * * ?");
        }
        try {
            Configuration config = configAdmin.getConfiguration(CONFIG_PID, null);
            if (config != null && config.getProperties() != null) {
                Object cron = config.getProperties().get("cronExpression");
                if (cron != null) {
                    return new GqlConfig(cron.toString());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to read configuration", e);
        }
        return new GqlConfig("0 30 2 * * ?");
    }

    @GraphQLField
    @GraphQLName("workflowTasksCleanerWorkflowList")
    @GraphQLDescription("Lists all current workflow tasks with their status")
    @GraphQLRequiresPermission("admin")
    public static List<GqlWorkflowTask> workflowList() {
        List<GqlWorkflowTask> result = new ArrayList<>();
        Locale locale = SettingsBean.getInstance().getDefaultLocale();
        try {
            WorkflowService workflowService = WorkflowService.getInstance();
            List<WorkflowDefinition> workflows = workflowService.getWorkflows(locale);
            for (WorkflowDefinition workflowDefinition : workflows) {
                List<Workflow> workflowsForType = workflowService.getWorkflowsForDefinition(workflowDefinition.getName(), locale);
                for (Workflow w : workflowsForType) {
                    result.add(new GqlWorkflowTask(
                            w.getId(),
                            w.getName(),
                            w.getWorkflowDefinition().getDisplayName(),
                            w.getVariables().get("jcr_title") != null ? w.getVariables().get("jcr_title").toString() : null
                    ));
                }
            }
        } catch (javax.jcr.RepositoryException e) {
            LOGGER.error("Failed to list workflows", e);
        }
        return result;
    }
}
