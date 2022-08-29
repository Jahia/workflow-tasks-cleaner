package org.jahia.community.workflowtaskscleaner;

import org.jahia.services.scheduler.BackgroundJob;
import org.quartz.JobExecutionContext;

public class CleanBackgroundJob extends BackgroundJob {

    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) throws Exception {
        CleanCommand.deleteTasks();
    }
}
