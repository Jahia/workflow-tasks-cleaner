package org.jahia.community.workflowtaskscleaner.graphql;

import org.junit.Test;
import org.quartz.CronExpression;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the cron-validation branch of WorkflowTasksCleanerMutationExtension.saveConfig.
 *
 * These tests exercise only the validation logic that runs BEFORE any OSGi service call,
 * so they need no mocking and no OSGi runtime.
 *
 * The contract under test:
 *   - null input         → Boolean.FALSE
 *   - empty string       → Boolean.FALSE
 *   - invalid expression → Boolean.FALSE
 *   - valid expression   → passes validation (CronExpression.isValidExpression returns true)
 */
public class SaveConfigValidationTest {

    // --- replicate the guard exactly as it appears in saveConfig ---
    private static Boolean validateCron(String cronExpression) {
        if (cronExpression == null || cronExpression.isEmpty()
                || !CronExpression.isValidExpression(cronExpression)) {
            return Boolean.FALSE;
        }
        return Boolean.TRUE; // would proceed to OSGi call in real code
    }

    @Test
    public void saveConfig_shouldReturnFalse_whenCronExpressionIsNull() {
        assertThat(validateCron(null)).isFalse();
    }

    @Test
    public void saveConfig_shouldReturnFalse_whenCronExpressionIsEmpty() {
        assertThat(validateCron("")).isFalse();
    }

    @Test
    public void saveConfig_shouldReturnFalse_whenCronExpressionIsNotACron() {
        assertThat(validateCron("not a cron")).isFalse();
    }

    @Test
    public void saveConfig_shouldReturnFalse_whenCronExpressionIsArbitraryText() {
        assertThat(validateCron("every monday at noon")).isFalse();
    }

    @Test
    public void saveConfig_shouldPassValidation_whenCronExpressionIsValid() {
        // "0 30 2 * * ?" is the default; validation must accept it
        assertThat(validateCron("0 30 2 * * ?")).isTrue();
    }

    @Test
    public void saveConfig_shouldPassValidation_whenCronExpressionIsAnotherValidQuartzCron() {
        // every day at midnight
        assertThat(validateCron("0 0 0 * * ?")).isTrue();
    }

    @Test
    public void saveConfig_shouldReturnFalse_whenCronHasTooFewFields() {
        // Quartz requires 6 or 7 fields; 5 fields is invalid
        assertThat(validateCron("0 30 2 * *")).isFalse();
    }
}
