package org.jahia.community.workflowtaskscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.community.workflowtaskscleaner.CleanCommand;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.jahia.osgi.BundleUtils;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

@GraphQLName("WorkflowTasksCleanerMutation")
@GraphQLDescription("Workflow Tasks Cleaner mutations")
public class WorkflowTasksCleanerMutation {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowTasksCleanerMutation.class);
    private static final String CONFIG_PID = "org.jahia.community.workflowtaskscleaner";

    @GraphQLField
    @GraphQLName("runClean")
    @GraphQLDescription("Triggers the cleanup of exited/completed workflow tasks asynchronously. Returns false if a clean is already running.")
    @GraphQLRequiresPermission("admin")
    public Boolean runClean() {
        // U3 — the real mutual-exclusion CAS now lives inside CleanCommand.deleteTasks() so every
        // entry point honours it. Here we only peek to give the caller immediate feedback; the
        // worker's deleteTasks() acquires/releases the shared guard.
        if (CleanCommand.isRunning()) {
            LOGGER.info("Workflow tasks cleaner run requested but already running");
            return Boolean.FALSE;
        }
        new Thread(() -> {
            try {
                CleanCommand.deleteTasks();
            } catch (Exception e) {
                LOGGER.error("Failed to run workflow tasks cleaner", e);
            }
        }).start();
        return Boolean.TRUE;
    }

    @GraphQLField
    @GraphQLName("saveConfig")
    @GraphQLDescription("Saves the workflow tasks cleaner scheduled job configuration. The job is rescheduled automatically when the configuration is updated.")
    @GraphQLRequiresPermission("admin")
    public Boolean saveConfig(
            @GraphQLName("cronExpression")
            @GraphQLDescription("Quartz cron expression for the scheduled cleanup")
            String cronExpression) {

        if (cronExpression == null || cronExpression.isEmpty() || !CronExpression.isValidExpression(cronExpression)) {
            LOGGER.warn("Invalid cron expression provided, configuration not updated");
            return Boolean.FALSE;
        }
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
            props.put("cronExpression", cronExpression);
            config.update(props);
            return Boolean.TRUE;
        } catch (IOException e) {
            LOGGER.error("Failed to save configuration", e);
            return Boolean.FALSE;
        }
    }
}
