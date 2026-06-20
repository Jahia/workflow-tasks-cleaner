package org.jahia.community.workflowtaskscleaner;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.quartz.CronExpression;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.ParseException;

@Component(service = {}, configurationPid = "org.jahia.community.workflowtaskscleaner", immediate = true)
@Designate(ocd = WorkflowTasksCleanerScheduler.Config.class)
public class WorkflowTasksCleanerScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowTasksCleanerScheduler.class);
    private static final String JOB_NAME = "WorkflowTasksCleanerJob";
    private static final String TRIGGER_NAME = "WorkflowTasksCleanerJobTrigger";
    private static final String GROUP = "Maintenance";
    public static final String DEFAULT_CRON_EXPRESSION = "0 30 2 * * ?";

    @Reference
    private Scheduler scheduler;

    @Activate
    @Modified
    public void activate(Config config) {
        try {
            if (!unscheduleExistingJob()) {
                LOGGER.warn("Could not unschedule existing job, skipping reschedule");
                return;
            }
            scheduleJob(config.cronExpression());
            LOGGER.info("Workflow tasks cleaner job scheduled");
        } catch (SchedulerException | ParseException e) {
            LOGGER.error("Failed to schedule workflow tasks cleaner job", e);
        }
    }

    @Deactivate
    public void deactivate() {
        unscheduleExistingJob();
        LOGGER.info("Workflow tasks cleaner job unscheduled");
    }

    private void scheduleJob(String cronExpression) throws SchedulerException, ParseException {
        try {
            new CronExpression(cronExpression);
        } catch (ParseException e) {
            throw new ParseException("Invalid cron expression: " + cronExpression, 0);
        }

        JobDetail jobDetail = new JobDetail(JOB_NAME, GROUP, CleanBackgroundJob.class);
        jobDetail.setDescription("Clean exited and completed workflow tasks");

        CronTrigger trigger = new CronTrigger();
        trigger.setName(TRIGGER_NAME);
        trigger.setGroup(GROUP);
        trigger.setCronExpression(cronExpression);

        scheduler.scheduleJob(jobDetail, trigger);
    }

    private boolean unscheduleExistingJob() {
        try {
            scheduler.unscheduleJob(TRIGGER_NAME, GROUP);
            return true;
        } catch (SchedulerException e) {
            LOGGER.error("Failed to unschedule workflow tasks cleaner job", e);
            return false;
        }
    }

    @ObjectClassDefinition(name = "Workflow tasks cleaner", description = "Configuration for the workflow tasks cleaner module")
    @interface Config {

        @AttributeDefinition(name = "Cron expression", description = "Cron expression for the cleanup job")
        String cronExpression() default DEFAULT_CRON_EXPRESSION;
    }
}
