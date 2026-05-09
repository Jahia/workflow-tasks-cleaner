package org.jahia.community.workflowtaskscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.community.workflowtaskscleaner.CleanCommand;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicBoolean;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLName("WorkflowTasksCleanerMutations")
@GraphQLDescription("Workflow tasks cleaner mutations")
public class WorkflowTasksCleanerMutationExtension {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowTasksCleanerMutationExtension.class);
    private static final String CONFIG_PID = "org.jahia.community.workflowtaskscleaner";
    private static final AtomicBoolean IS_RUNNING = new AtomicBoolean(false);

    private WorkflowTasksCleanerMutationExtension() {
    }

    @GraphQLField
    @GraphQLName("workflowTasksCleanerRunClean")
    @GraphQLDescription("Triggers the cleanup of exited/completed workflow tasks asynchronously. Returns false if a clean is already running.")
    @GraphQLRequiresPermission("admin")
    public static Boolean runClean() {
        if (IS_RUNNING.get()) {
            LOGGER.info("Workflow tasks cleaner run requested but already running");
            return Boolean.FALSE;
        }
        IS_RUNNING.set(true);
        new Thread(() -> {
            try {
                CleanCommand.deleteTasks();
            } catch (Exception e) {
                LOGGER.error("Failed to run workflow tasks cleaner", e);
            } finally {
                IS_RUNNING.set(false);
            }
        }).start();
        return Boolean.TRUE;
    }

    @GraphQLField
    @GraphQLName("workflowTasksCleanerSaveConfig")
    @GraphQLDescription("Saves the workflow tasks cleaner scheduled job configuration. A module restart is required for schedule changes to take effect.")
    @GraphQLRequiresPermission("admin")
    public static Boolean saveConfig(
            @GraphQLName("cronExpression")
            @GraphQLDescription("Quartz cron expression for the scheduled cleanup")
            String cronExpression) {

        try {
            ConfigurationAdmin configAdmin = BundleUtils.getOsgiService(ConfigurationAdmin.class, null);
            if (configAdmin == null) {
                return Boolean.FALSE;
            }
            Configuration config = configAdmin.getConfiguration(CONFIG_PID, null);
            Dictionary<String, Object> props = config.getProperties();
            if (props == null) {
                props = new Hashtable<>();
            }
            if (cronExpression != null && !cronExpression.isEmpty()) {
                props.put("cronExpression", cronExpression);
            }
            config.update(props);
            return Boolean.TRUE;
        } catch (IOException e) {
            LOGGER.error("Failed to save configuration", e);
            return Boolean.FALSE;
        }
    }
}
