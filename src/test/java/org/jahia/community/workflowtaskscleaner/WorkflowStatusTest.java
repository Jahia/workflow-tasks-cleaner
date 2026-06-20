package org.jahia.community.workflowtaskscleaner;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the WorkflowStatus enum declared inside WorkflowListCommand.
 */
public class WorkflowStatusTest {

    @Test
    public void fromValue_shouldReturnPending_whenValueIsZero() {
        WorkflowListCommand.WorkflowStatus status = WorkflowListCommand.WorkflowStatus.fromValue(0);
        assertThat(status).isEqualTo(WorkflowListCommand.WorkflowStatus.STATE_PENDING);
    }

    @Test
    public void fromValue_shouldReturnActive_whenValueIsOne() {
        WorkflowListCommand.WorkflowStatus status = WorkflowListCommand.WorkflowStatus.fromValue(1);
        assertThat(status).isEqualTo(WorkflowListCommand.WorkflowStatus.STATE_ACTIVE);
    }

    @Test
    public void fromValue_shouldReturnCompleted_whenValueIsTwo() {
        WorkflowListCommand.WorkflowStatus status = WorkflowListCommand.WorkflowStatus.fromValue(2);
        assertThat(status).isEqualTo(WorkflowListCommand.WorkflowStatus.STATE_COMPLETED);
    }

    @Test
    public void fromValue_shouldReturnAborted_whenValueIsThree() {
        WorkflowListCommand.WorkflowStatus status = WorkflowListCommand.WorkflowStatus.fromValue(3);
        assertThat(status).isEqualTo(WorkflowListCommand.WorkflowStatus.STATE_ABORTED);
    }

    @Test
    public void fromValue_shouldReturnSuspended_whenValueIsFour() {
        WorkflowListCommand.WorkflowStatus status = WorkflowListCommand.WorkflowStatus.fromValue(4);
        assertThat(status).isEqualTo(WorkflowListCommand.WorkflowStatus.STATE_SUSPENDED);
    }

    @Test
    public void fromValue_shouldThrowIllegalArgumentException_whenValueIsInvalid() {
        assertThatThrownBy(() -> WorkflowListCommand.WorkflowStatus.fromValue(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @Test
    public void fromValue_shouldThrowIllegalArgumentException_whenValueIsNegative() {
        assertThatThrownBy(() -> WorkflowListCommand.WorkflowStatus.fromValue(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }

    @Test
    public void getDescriptionByValue_shouldReturnPending_forZero() {
        assertThat(WorkflowListCommand.WorkflowStatus.getDescriptionByValue(0)).isEqualTo("Pending");
    }

    @Test
    public void getDescriptionByValue_shouldReturnActive_forOne() {
        assertThat(WorkflowListCommand.WorkflowStatus.getDescriptionByValue(1)).isEqualTo("Active");
    }

    @Test
    public void getDescriptionByValue_shouldReturnCompleted_forTwo() {
        assertThat(WorkflowListCommand.WorkflowStatus.getDescriptionByValue(2)).isEqualTo("Completed");
    }

    @Test
    public void getDescriptionByValue_shouldReturnAborted_forThree() {
        assertThat(WorkflowListCommand.WorkflowStatus.getDescriptionByValue(3)).isEqualTo("Aborted");
    }

    @Test
    public void getDescriptionByValue_shouldReturnSuspended_forFour() {
        assertThat(WorkflowListCommand.WorkflowStatus.getDescriptionByValue(4)).isEqualTo("Suspended");
    }

    @Test
    public void getDescriptionByValue_shouldThrowIllegalArgumentException_whenValueIsInvalid() {
        assertThatThrownBy(() -> WorkflowListCommand.WorkflowStatus.getDescriptionByValue(999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    @Test
    public void allEnumValues_shouldHaveMatchingFromValueRoundtrip() {
        for (WorkflowListCommand.WorkflowStatus expected : WorkflowListCommand.WorkflowStatus.values()) {
            WorkflowListCommand.WorkflowStatus actual = WorkflowListCommand.WorkflowStatus.fromValue(expected.getValue());
            assertThat(actual).isEqualTo(expected);
        }
    }

    @Test
    public void allEnumValues_shouldHaveNonBlankDescription() {
        for (WorkflowListCommand.WorkflowStatus status : WorkflowListCommand.WorkflowStatus.values()) {
            assertThat(status.getDescription()).isNotBlank();
        }
    }
}
