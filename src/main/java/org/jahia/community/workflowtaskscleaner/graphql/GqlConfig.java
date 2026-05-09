package org.jahia.community.workflowtaskscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

@GraphQLName("WorkflowTasksCleanerConfig")
@GraphQLDescription("Workflow tasks cleaner scheduled job configuration")
public class GqlConfig {

    private final String cronExpression;

    public GqlConfig(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    @GraphQLField
    @GraphQLName("cronExpression")
    @GraphQLDescription("Quartz cron expression for the scheduled cleanup")
    public String getCronExpression() {
        return cronExpression;
    }
}
