package org.jahia.community.workflowtaskscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

@GraphQLName("WorkflowTask")
@GraphQLDescription("A workflow task instance")
public class GqlWorkflowTask {

    private final String id;
    private final String name;
    private final String type;
    private final String title;
    public GqlWorkflowTask(String id, String name, String type, String title) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.title = title;
    }

    @GraphQLField
    @GraphQLName("id")
    @GraphQLDescription("Workflow instance ID")
    public String getId() {
        return id;
    }

    @GraphQLField
    @GraphQLName("name")
    @GraphQLDescription("Workflow name")
    public String getName() {
        return name;
    }

    @GraphQLField
    @GraphQLName("type")
    @GraphQLDescription("Workflow definition display name")
    public String getType() {
        return type;
    }

    @GraphQLField
    @GraphQLName("title")
    @GraphQLDescription("JCR title associated with the workflow")
    public String getTitle() {
        return title;
    }

}
