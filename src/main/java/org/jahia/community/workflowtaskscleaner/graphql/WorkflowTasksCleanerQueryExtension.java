package org.jahia.community.workflowtaskscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
@GraphQLDescription("Workflow Tasks Cleaner queries")
public class WorkflowTasksCleanerQueryExtension {

    private WorkflowTasksCleanerQueryExtension() {
    }

    @GraphQLField
    @GraphQLName("workflowTasksCleaner")
    @GraphQLDescription("Workflow Tasks Cleaner query namespace")
    public static WorkflowTasksCleanerQuery workflowTasksCleaner() {
        return new WorkflowTasksCleanerQuery();
    }
}
