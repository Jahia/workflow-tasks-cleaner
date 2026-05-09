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
| `src/main/java/.../WorkflowTasksCleanerScheduler.java` | Quartz OSGi scheduler (cron) |
| `src/main/java/.../CleanCommand.java` | Main cleanup logic (Karaf command) |
| `src/main/java/.../WorkflowTaskCommand.java` | Task fix/delete with SQL injection prevention |
| `src/main/java/.../graphql/WorkflowTasksCleanerMutationExtension.java` | `runClean` (async + AtomicBoolean guard), `saveConfig` |
| `src/main/java/.../graphql/WorkflowTasksCleanerQueryExtension.java` | `config`, `workflowList` queries |
| `src/main/resources/javascript/WorkflowTasksCleaner/` | React admin UI (Moonstone) |
| `META-INF/configurations/org.jahia.community.workflowtaskscleaner.cfg` | Default OSGi config |

## Security Notes

- SQL queries use `PreparedStatement` with bound parameters (no string concatenation)
- JCR SQL2 queries use `Query.bindValue()` with `$variable` placeholders (no string interpolation)
- `runClean()` mutation uses `AtomicBoolean` to prevent concurrent executions
- All GraphQL mutations require `admin` permission (`@GraphQLRequiresPermission("admin")`)

## Code Style

- No added comments unless explicitly requested
- SLF4J parameterized logging: `LOGGER.info("message {} and {}", arg1, arg2)`
- Tight exception catching (specific types, not broad `Exception`)
- OSGi DS annotations: `@Component`, `@Activate`, `@Modified`, `@Designate`
