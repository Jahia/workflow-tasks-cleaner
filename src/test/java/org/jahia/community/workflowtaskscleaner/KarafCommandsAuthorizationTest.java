package org.jahia.community.workflowtaskscleaner;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.jahia.modules.graphql.provider.dxm.security.GraphQLRequiresPermission;
import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S19 (G8 / U9, F6-F8, F10) — GENUINE-GAP: the Karaf commands carry NO Jahia authorization.
 *
 * <p>The three shell commands ({@code clean}, {@code list}, {@code task}) invoke the same
 * destructive deletion behind shell access only, with zero Jahia permission check — in stark
 * contrast to the GraphQL fields, which are ALL {@code @GraphQLRequiresPermission("admin")}.
 * Also pins the copy-paste bug: {@link WorkflowTaskCommand}'s {@code @Command} description wrongly
 * says "List all current workflow tasks" although it fixes/deletes (flagged for the Stage-7 fix).</p>
 */
public class KarafCommandsAuthorizationTest {

    @Test
    public void allThreeCommands_areKarafCommandsInWorkflowCleanerScopeImplementingAction() {
        assertCommand(CleanCommand.class, "clean");
        assertCommand(WorkflowListCommand.class, "list");
        assertCommand(WorkflowTaskCommand.class, "task");
    }

    private static void assertCommand(Class<?> type, String expectedName) {
        Command cmd = type.getAnnotation(Command.class);
        assertThat(cmd).as("%s must be @Command", type.getSimpleName()).isNotNull();
        assertThat(cmd.scope()).isEqualTo("workflow-cleaner");
        assertThat(cmd.name()).isEqualTo(expectedName);
        assertThat(Action.class.isAssignableFrom(type)).as("%s implements Action", type.getSimpleName()).isTrue();
    }

    @Test
    public void noKarafCommand_carriesAnyJahiaPermissionAnnotation() {
        assertNoPermissionAnnotation(CleanCommand.class);
        assertNoPermissionAnnotation(WorkflowListCommand.class);
        assertNoPermissionAnnotation(WorkflowTaskCommand.class);
    }

    private static void assertNoPermissionAnnotation(Class<?> type) {
        assertThat(isPermissionGated(type)).as("%s class-level permission gate", type.getSimpleName()).isFalse();
        for (Method m : type.getDeclaredMethods()) {
            assertThat(isPermissionGated(m)).as("%s#%s permission gate", type.getSimpleName(), m.getName()).isFalse();
        }
    }

    private static boolean isPermissionGated(AnnotatedElement element) {
        if (element.isAnnotationPresent(GraphQLRequiresPermission.class)) {
            return true;
        }
        for (Annotation a : element.getAnnotations()) {
            String n = a.annotationType().getSimpleName().toLowerCase();
            if (n.contains("permission") || n.contains("requiresrole") || n.contains("rolesallowed")) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void workflowTaskCommand_hasTheWrongCopyPasteDescription() {
        // Bug characterization: the description is copied from the 'list' command but 'task' fixes/deletes.
        // Stage-7 must correct this description (WorkflowTaskCommand.java:47).
        Command cmd = WorkflowTaskCommand.class.getAnnotation(Command.class);
        assertThat(cmd.description()).isEqualTo("List all current workflow tasks");
    }
}
