# Workflow Tasks Cleaner

A Jahia 8.2 module that automatically cleans exited or completed workflow tasks (publication, etc.).

## Installation

- In Jahia, go to **Administration → Server settings → System components → Modules**
- Upload the JAR **workflow-tasks-cleaner-X.X.X.jar**
- Check that the module is started

## Configuration

The cleaner runs on a Quartz cron schedule. After installing/updating the module, a default configuration is applied:

| Property | Default | Description |
|---|---|---|
| `cronExpression` | `0 30 2 * * ?` | Quartz cron expression for the scheduled cleanup (default: 2:30 AM daily) |

To change the schedule, edit the OSGi configuration in the OSGi console or via GraphQL (see below).

## Usage

### GraphQL API

The module exposes a GraphQL API under the `WorkflowTasksCleanerQuery` and `WorkflowTasksCleanerMutations` extensions.

#### Queries

##### `workflowTasksCleanerConfig`

Returns the current scheduler configuration (cron expression).

```graphql
query {
  workflowTasksCleanerConfig {
    cronExpression
    isRunning
    tasksScanned
    tasksDeleted
    tasksFixed
  }
}
```

##### `workflowTasksCleanerWorkflowList`

Lists workflow tasks with optional state filter.

```graphql
query {
  workflowTasksCleanerWorkflowList(taskState: ESCALATED) {
    taskId
    taskName
    taskState
    processId
    processSubject
    assignee
    createdDate
    dueDate
  }
}
```

`taskState` values: `PENDING`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `EXITED`, `ESCALATED`, `SUSPENDED`, `OBSOLETE`

#### Mutations

##### `workflowTasksCleanerRunClean`

Triggers an immediate cleanup of exited/completed tasks. Returns `false` if a clean is already running.

```graphql
mutation {
  workflowTasksCleanerRunClean
}
```

##### `workflowTasksCleanerSaveConfig`

Saves a new cron expression for the scheduled job. **A module restart is required for schedule changes to take effect.**

```graphql
mutation {
  workflowTasksCleanerSaveConfig(cronExpression: "0 0 3 * * ?")
}
```

### Admin UI

The module includes a React-based admin UI accessible via the Jahia tools:

- **Karaf command line** (legacy): `workflow-cleaner:clean`, `workflow-cleaner:list`, `workflow-cleaner:task`
- **Admin UI** (recommended): Available in the Jahia admin panel under the workflow tasks cleaner section

### Karaf Commands (legacy)

#### `workflow-cleaner:list`

List all current workflow tasks with human-readable values for task states.

#### `workflow-cleaner:task`

**Options:**
- `-id` (required): The task id
- `--fix`: Try to fix the task (reassign groups/users)
- `--delete`: Delete the task

```bash
workflow-cleaner:task -id 12345 --delete
```

## Technical Details

- **Module type**: Jahia OSGi module (using OSGi Declarative Services)
- **Minimum Jahia version**: 8.2.2.1
- **Parent POM**: Jahia 8.2.2.1
- **API**: GraphQL with Apollo Client (admin UI) + JCR SQL2 queries (backend)
- **Frontend**: React 18 with Moonstone Design System + Webpack 5 Module Federation

## Building

```bash
mvn clean install
```

## Module Structure

```
workflow-tasks-cleaner/
├── src/main/java/org/jahia/community/workflowtaskscleaner/
│   ├── WorkflowTasksCleanerScheduler.java    # Quartz OSGi scheduler
│   ├── CleanCommand.java                     # Main cleanup logic (Karaf)
│   ├── WorkflowListCommand.java             # List command (Karaf)
│   ├── WorkflowTaskCommand.java              # Task command (Karaf)
│   ├── graphql/
│   │   ├── WorkflowTasksCleanerGraphQLExtensionsProvider.java  # Schema provider
│   │   ├── WorkflowTasksCleanerQueryExtension.java            # GraphQL queries
│   │   ├── WorkflowTasksCleanerMutationExtension.java         # GraphQL mutations
│   │   └── model/
│   │       ├── GqlConfig.java               # Config data model
│   │       └── GqlWorkflowTask.java         # Workflow task data model
│   └── package-info.java
├── src/main/resources/
│   ├── META-INF/
│   │   ├── configurations/                  # Default OSGi config
│   │   ├── definitions/                     # CND node type definitions
│   │   └── OSGI-INF/
│   │       └── blueprint/                   # (empty - using OSGi DS)
│   └── javascript/
│       └── WorkflowTasksCleaner/
│           ├── WorkflowTasksCleaner.jsx     # Main admin component
│           ├── SchedulerConfig.jsx         # Scheduler config component
│           ├── WorkflowTasksCleaner.gql.js  # GraphQL queries/mutations
│           └── SCSS/
└── pom.xml
```
