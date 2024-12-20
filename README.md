# Workflow tasks cleaner

This is a custom module to clean automatically exited or completed workflow tasks (publication, etc).

## Installation

- In Jahia, go to "Administration --> Server settings --> System components --> Modules"
- Upload the JAR **workflow-tasks-cleaner-X.X.X.jar**
- Check that the module is started

## How to use
### In the tools

- Go to the page **"Karaf command line"** (JAHIA_URL/modules/tools/karaf.jsp) and execute the command **workflow-cleaner:clean**

### Background job

**Properties:**
Name | Value | Description
 --- | --- | ---
jahia.workflow.tasks.cleaner.job.cronExpression | 0 30 2 * * ? | Crontab expression for the job


### Commands

#### `workflow-cleaner:list`

List all current workflow tasks with human-readable values for task states.


#### `workflow-cleaner:task`

List all current workflow tasks.

**Options:**
- `-id` (required): The task id.
- `--fix`: Try to fix the task (reassign groups/users).
- `--delete`: Delete the task.

