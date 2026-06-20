package org.jahia.community.workflowtaskscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
@GraphQLDescription("Workflow Tasks Cleaner mutations")
public class WorkflowTasksCleanerMutationExtension {

    private WorkflowTasksCleanerMutationExtension() {
    }

    @GraphQLField
    @GraphQLName("workflowTasksCleaner")
    @GraphQLDescription("Workflow Tasks Cleaner mutation namespace")
    public static WorkflowTasksCleanerMutation workflowTasksCleaner() {
        return new WorkflowTasksCleanerMutation();
    }
}
