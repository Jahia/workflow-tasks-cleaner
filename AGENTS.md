# AGENTS.md

## Project Overview

This is a Jahia 8.2 OSGi module (`workflow-tasks-cleaner`) that cleans exited/completed workflow tasks via GraphQL API, admin UI, and Karaf CLI commands.

## Build

```bash
mvn clean install
```

## Important Commands

- **Lint**: `yarn run lint --max-warnings 1` (run after `yarn install` if lockfile missing)
- **Build**: `mvn clean install`
- **Full rebuild**: `mvn clean compile` (Java only)

## Key Files

| File | Purpose |
|---|---|
| `src/main/java/.../WorkflowTasksCleanerScheduler.java` | Quartz OSGi scheduler (cron + `abortGhostWorkflows` toggle) |
| `src/main/java/.../CleanCommand.java` | Main cleanup logic + shared `IS_RUNNING` concurrency guard (Karaf `clean`) |
| `src/main/java/.../GhostWorkflowCleaner.java` | Ghost-workflow pass: status gate + report-only(default)/enforce mode |
| `src/main/java/.../WorkflowTaskCommand.java` | Task inspect/`--fix`/`--delete` with SQL injection prevention |
| `src/main/java/.../graphql/WorkflowTasksCleanerMutation.java` | `runClean`, `saveConfig` (holder; namespaced under `workflowTasksCleaner`) |
| `src/main/java/.../graphql/WorkflowTasksCleanerQuery.java` | `config`, `workflowList` (holder; namespaced under `workflowTasksCleaner`) |
| `src/main/java/.../graphql/WorkflowTasksCleaner{Query,Mutation}Extension.java` | Register the single `workflowTasksCleaner` namespace on root Query/Mutation |
| `src/main/resources/javascript/WorkflowTasksCleaner/` | React admin UI (Moonstone) |
| `META-INF/configurations/org.jahia.community.workflowtaskscleaner.cfg` | Default OSGi config (cron + report-only default) |

> GraphQL shape (the previous docs were inaccurate): `config` returns only `cronExpression`;
> `workflowList` takes **no** arguments and returns `id/name/type/title` per entry. There is no
> `taskState` enum, no per-task counters, and no `graphql/model/` package.

## Security Notes

- SQL queries use `PreparedStatement` with bound parameters (no string concatenation)
- JCR SQL2 queries use `Query.bindValue()` with `$variable` placeholders (no string interpolation)
- Concurrency: the `IS_RUNNING` `AtomicBoolean` guard lives in `CleanCommand.deleteTasks()`, so ALL
  entry points honour it (GraphQL `runClean`, the Quartz job, and the Karaf `clean` command). Note
  this is JVM-local; `SettingsBean.isProcessingServer()` confines destructive work to one node but
  is not a cross-cluster lock.
- Ghost-workflow abort is gated by workflow status (active/pending/suspended are never aborted) and
  defaults to report-only (`abortGhostWorkflows=false`) so the nightly job never silently aborts.
- All GraphQL queries and mutations require `admin` permission (`@GraphQLRequiresPermission("admin")`).
- The Karaf commands (`workflow-cleaner:clean|list|task`) have **no** Jahia permission gate: they are
  a trusted-admin-only surface (shell/SSH access is the gate), consistent with the support-token and
  versions-cleaner modules.

## Code Style

- No added comments unless explicitly requested
- SLF4J parameterized logging: `LOGGER.info("message {} and {}", arg1, arg2)`
- Tight exception catching (specific types, not broad `Exception`)
- OSGi DS annotations: `@Component`, `@Activate`, `@Modified`, `@Designate`
