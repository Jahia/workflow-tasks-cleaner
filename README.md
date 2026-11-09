# Workflow Tasks Cleaner

A Jahia 8.2 module that automatically cleans exited or completed workflow tasks (publication, etc.).

## Installation

- In Jahia, go to **Administration → Server settings → System components → Modules**
- Upload the JAR **workflow-tasks-cleaner-X.X.X.jar**
- Check that the module is started

## Configuration

The cleaner runs on a Quartz cron schedule. After installing/updating the module, a default configuration is applied (OSGi PID `org.jahia.community.workflowtaskscleaner`):

| Property | Default | Description |
|---|---|---|
| `cronExpression` | `0 30 2 * * ?` | Quartz cron expression for the scheduled cleanup (default: 2:30 AM daily) |
| `abortGhostWorkflows` | `false` | Ghost-workflow cleanup mode. `false` = **report-only** (the default): the job only logs the workflows it *would* abort. `true` = **enforce**: terminal-eligible ghost workflows whose target nodes were deleted are aborted. In-progress (Active/Pending/Suspended) workflows are **never** aborted, in either mode. |

To change the schedule (or the `abortGhostWorkflows` mode), edit the OSGi configuration in the OSGi console or, for the cron expression, via GraphQL (see below). A change is applied immediately — **no module restart is required** (the job is auto-rescheduled and the mode re-read on configuration update).

> **Safety note:** ghost-workflow cleanup ships in report-only mode so the unattended nightly job never silently aborts a workflow. Review the log entries it emits, then set `abortGhostWorkflows=true` only if you want enforcement.

## Usage

### GraphQL API

All operations are grouped under a single `workflowTasksCleaner` namespace on the root `Query` and
`Mutation` types. Every field requires the `admin` permission (`@GraphQLRequiresPermission("admin")`);
a non-admin session is rejected.

#### Queries

##### `config`

Returns the current scheduler configuration. The only field is `cronExpression`.

```graphql
query {
  workflowTasksCleaner {
    config {
      cronExpression
    }
  }
}
```

##### `workflowList`

Lists the workflow instances currently in the system. Takes **no arguments**. Each entry exposes
exactly four fields.

```graphql
query {
  workflowTasksCleaner {
    workflowList {
      id     # workflow instance id
      name   # workflow name
      type   # workflow definition display name
      title  # associated JCR title (may be null)
    }
  }
}
```

#### Mutations

##### `runClean`

Triggers an asynchronous cleanup of exited/completed tasks (and the ghost-workflow pass). Returns
`true` when the run was started, or `false` if a clean is already running on this node. Because the
work runs on a background thread, `true` means "accepted", not "finished".

```graphql
mutation {
  workflowTasksCleaner {
    runClean
  }
}
```

##### `saveConfig`

Saves a new cron expression for the scheduled job. Returns `true` on success, `false` if the
expression is invalid. The job is **rescheduled automatically** — no module restart is required.

```graphql
mutation {
  workflowTasksCleaner {
    saveConfig(cronExpression: "0 0 3 * * ?")
  }
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
│   ├── WorkflowTasksCleanerScheduler.java    # Quartz OSGi scheduler (cron + abortGhostWorkflows toggle)
│   ├── CleanCommand.java                      # Main cleanup logic + shared concurrency guard (Karaf `clean`)
│   ├── CleanBackgroundJob.java                # Quartz job -> CleanCommand.deleteTasks()
│   ├── GhostWorkflowCleaner.java              # Ghost-workflow pass: status gate + report-only/enforce mode
│   ├── WorkflowListCommand.java               # Karaf `list` command (+ WorkflowStatus enum)
│   └── WorkflowTaskCommand.java               # Karaf `task` command (inspect/--fix/--delete)
│   └── graphql/
│       ├── WorkflowTasksCleanerGraphQLExtensionsProvider.java  # DXGraphQLExtensionsProvider registration
│       ├── WorkflowTasksCleanerQueryExtension.java             # Adds the `workflowTasksCleaner` Query namespace
│       ├── WorkflowTasksCleanerMutationExtension.java          # Adds the `workflowTasksCleaner` Mutation namespace
│       ├── WorkflowTasksCleanerQuery.java                      # `config`, `workflowList`
│       ├── WorkflowTasksCleanerMutation.java                   # `runClean`, `saveConfig`
│       ├── GqlConfig.java                                      # config payload (cronExpression only)
│       └── GqlWorkflowTask.java                               # workflow payload (id/name/type/title)
├── src/main/resources/
│   ├── META-INF/
│   │   ├── configurations/                    # Default OSGi config (.cfg, non-overridable)
│   │   └── definitions.cnd                    # Namespace prefixes only — NO custom node types
│   └── javascript/WorkflowTasksCleaner/       # React admin UI (Moonstone)
└── pom.xml
```

> Note: the GraphQL operations live in `WorkflowTasksCleanerQuery`/`WorkflowTasksCleanerMutation`;
> the `*Extension` classes only register the single `workflowTasksCleaner` namespace field on the
> root `Query`/`Mutation`. There is no `graphql/model/` package. The module defines **no** custom
> JCR node types — `definitions.cnd` declares only the `jnt`/`jmix` namespace prefixes.
