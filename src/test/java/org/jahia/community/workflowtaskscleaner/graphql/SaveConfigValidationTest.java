package org.jahia.community.workflowtaskscleaner.graphql;

import org.junit.Test;
import org.quartz.CronExpression;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the cron-validation branch of {@link WorkflowTasksCleanerMutation#saveConfig(String)}.
 *
 * For invalid input the guard returns {@code Boolean.FALSE} BEFORE any OSGi service lookup, so the
 * real method can be exercised directly with no mocking and no OSGi runtime. For valid input the
 * method would proceed to a {@code ConfigurationAdmin} lookup (unavailable in a unit test), so the
 * validity of the accepted expressions is asserted against the same predicate the guard delegates to
 * ({@link CronExpression#isValidExpression(String)}) rather than the method's overall return value.
 */
public class SaveConfigValidationTest {

    @Test
    public void saveConfig_returnsFalse_whenCronExpressionIsNull() {
        assertThat(new WorkflowTasksCleanerMutation().saveConfig(null)).isFalse();
    }

    @Test
    public void saveConfig_returnsFalse_whenCronExpressionIsEmpty() {
        assertThat(new WorkflowTasksCleanerMutation().saveConfig("")).isFalse();
    }

    @Test
    public void saveConfig_returnsFalse_whenCronExpressionIsNotACron() {
        assertThat(new WorkflowTasksCleanerMutation().saveConfig("not a cron")).isFalse();
    }

    @Test
    public void saveConfig_returnsFalse_whenCronExpressionIsArbitraryText() {
        assertThat(new WorkflowTasksCleanerMutation().saveConfig("every monday at noon")).isFalse();
    }

    @Test
    public void saveConfig_returnsFalse_whenCronHasTooFewFields() {
        // Quartz requires 6 or 7 fields; 5 fields is invalid.
        assertThat(new WorkflowTasksCleanerMutation().saveConfig("0 30 2 * *")).isFalse();
    }

    @Test
    public void cronValidationPredicate_acceptsTheDefaultExpression() {
        assertThat(CronExpression.isValidExpression("0 30 2 * * ?")).isTrue();
    }

    @Test
    public void cronValidationPredicate_acceptsAnotherValidQuartzCron() {
        assertThat(CronExpression.isValidExpression("0 0 0 * * ?")).isTrue();
    }
}
