package org.jahia.community.workflowtaskscleaner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S4/S5/S6 (G11 / F1, U10, D3) — the scheduler auto-(re)schedules via {@code @Activate}/{@code @Modified}
 * and unschedules on deactivate.
 *
 * <p>S4 kills the D3 "restart required" doc claim: a config change reschedules with no restart.</p>
 */
public class WorkflowTasksCleanerSchedulerTest {

    private static final String TRIGGER_NAME = "WorkflowTasksCleanerJobTrigger";
    private static final String GROUP = "Maintenance";

    private WorkflowTasksCleanerScheduler scheduler;
    private Scheduler quartz;

    @Before
    public void setUp() throws Exception {
        scheduler = new WorkflowTasksCleanerScheduler();
        quartz = mock(Scheduler.class);
        Field f = WorkflowTasksCleanerScheduler.class.getDeclaredField("scheduler");
        f.setAccessible(true);
        f.set(scheduler, quartz);
    }

    @After
    public void resetAbortFlag() {
        CleanCommand.setAbortGhostWorkflows(false);
    }

    private static WorkflowTasksCleanerScheduler.Config config(String cron) {
        return config(cron, false);
    }

    private static WorkflowTasksCleanerScheduler.Config config(String cron, boolean abortGhostWorkflows) {
        WorkflowTasksCleanerScheduler.Config config = mock(WorkflowTasksCleanerScheduler.Config.class);
        when(config.cronExpression()).thenReturn(cron);
        when(config.abortGhostWorkflows()).thenReturn(abortGhostWorkflows);
        return config;
    }

    // --- U2: the report-only/enforce toggle is propagated to CleanCommand on activate/@Modified ---
    @Test
    public void activate_propagatesAbortGhostWorkflowsToggle_toCleanCommand() {
        scheduler.activate(config("0 0 4 * * ?", true));
        assertThat(CleanCommand.isAbortGhostWorkflows()).as("enforce mode wired").isTrue();

        scheduler.activate(config("0 0 4 * * ?", false));
        assertThat(CleanCommand.isAbortGhostWorkflows()).as("report-only mode wired").isFalse();
    }

    // --- S4: reschedule on every activate/@Modified, no restart ---
    @Test
    public void activate_reschedulesEveryTime_withoutRestart() throws Exception {
        WorkflowTasksCleanerScheduler.Config config = config("0 0 4 * * ?");

        scheduler.activate(config); // @Activate
        scheduler.activate(config); // @Modified

        verify(quartz, times(2)).unscheduleJob(TRIGGER_NAME, GROUP);

        ArgumentCaptor<Trigger> triggerCaptor = ArgumentCaptor.forClass(Trigger.class);
        verify(quartz, times(2)).scheduleJob(any(JobDetail.class), triggerCaptor.capture());
        assertThat(triggerCaptor.getValue()).isInstanceOf(CronTrigger.class);
        assertThat(((CronTrigger) triggerCaptor.getValue()).getCronExpression()).isEqualTo("0 0 4 * * ?");
    }

    // --- S5a: unschedule failure -> no schedule ---
    @Test
    public void activate_doesNotSchedule_whenUnscheduleFails() throws Exception {
        doThrow(new SchedulerException("boom")).when(quartz).unscheduleJob(TRIGGER_NAME, GROUP);

        scheduler.activate(config("0 0 4 * * ?"));

        verify(quartz, never()).scheduleJob(any(), any());
    }

    // --- S5b: invalid cron -> ParseException caught, no schedule ---
    @Test
    public void activate_doesNotSchedule_whenCronExpressionIsInvalid() throws Exception {
        scheduler.activate(config("bogus"));

        verify(quartz).unscheduleJob(TRIGGER_NAME, GROUP);
        verify(quartz, never()).scheduleJob(any(), any());
    }

    // --- S6: deactivate unschedules ---
    @Test
    public void deactivate_unschedulesTheJob() throws Exception {
        scheduler.deactivate();

        verify(quartz).unscheduleJob(eq(TRIGGER_NAME), eq(GROUP));
    }
}
