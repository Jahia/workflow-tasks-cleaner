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
 * contrast to the GraphQL fields, which are ALL {@code @GraphQLRequiresPermission("admin")}.</p>
 *
 * <p><b>Stage-7 (U9) decision:</b> the Karaf console is a trusted-admin-only surface (a caller has
 * already authenticated into the Karaf shell / SSH), and there is no per-invocation Jahia caller
 * identity to gate on — consistent with the support-token and versions-cleaner modules. So the gap
 * is DOCUMENTED rather than "fixed" by bolting on a fake gate; {@code noKarafCommand_carries...} is
 * kept as a tripwire that fails if someone later adds an ineffective annotation. The genuine bug
 * that IS fixed here is the copy-pasted {@code @Command} description on {@link WorkflowTaskCommand}
 * (asserted by {@code workflowTaskCommand_hasAccurateDescription}).</p>
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
    public void workflowTaskCommand_hasAccurateDescription() {
        // Stage-7 (U9) fix: the description previously copy-pasted from 'list' ("List all current
        // workflow tasks") is corrected to describe what 'task' actually does (inspect/fix/delete).
        Command cmd = WorkflowTaskCommand.class.getAnnotation(Command.class);
        assertThat(cmd.description())
                .isNotEqualTo("List all current workflow tasks")
                .containsIgnoringCase("delete")
                .containsIgnoringCase("fix");
    }
}
