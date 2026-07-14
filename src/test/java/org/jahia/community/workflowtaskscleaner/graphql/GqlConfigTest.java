package org.jahia.community.workflowtaskscleaner.graphql;

import graphql.annotations.annotationTypes.GraphQLField;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S7 (G13 / D1) — {@link GqlConfig} exposes ONLY {@code cronExpression}.
 *
 * <p>DOC-CORRECTION: the README/AGENTS documents {@code isRunning}/{@code tasksScanned}/
 * {@code tasksDeleted}/{@code tasksFixed} counters on the config type. This test proves those
 * fields do NOT exist on the real GraphQL type — the documented counters are fiction.</p>
 */
public class GqlConfigTest {

    @Test
    public void getCronExpression_returnsTheConstructorValue() {
        assertThat(new GqlConfig("0 30 2 * * ?").getCronExpression()).isEqualTo("0 30 2 * * ?");
    }

    @Test
    public void gqlConfig_declaresNoFictionalCounterGetters() {
        assertThat(hasMethod("getIsRunning")).as("isRunning getter must not exist").isFalse();
        assertThat(hasMethod("isRunning")).as("isRunning getter must not exist").isFalse();
        assertThat(hasMethod("getTasksScanned")).as("tasksScanned getter must not exist").isFalse();
        assertThat(hasMethod("getTasksDeleted")).as("tasksDeleted getter must not exist").isFalse();
        assertThat(hasMethod("getTasksFixed")).as("tasksFixed getter must not exist").isFalse();
    }

    @Test
    public void gqlConfig_exposesExactlyOneGraphQLField_cronExpression() {
        long graphQLFieldCount = Arrays.stream(GqlConfig.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GraphQLField.class))
                .count();
        assertThat(graphQLFieldCount).as("only cronExpression is a @GraphQLField").isEqualTo(1);

        Method cron = findMethod("getCronExpression");
        assertThat(cron).isNotNull();
        assertThat(cron.isAnnotationPresent(GraphQLField.class)).isTrue();
    }

    private static boolean hasMethod(String name) {
        return findMethod(name) != null;
    }

    private static Method findMethod(String name) {
        for (Method m : GqlConfig.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }
}
