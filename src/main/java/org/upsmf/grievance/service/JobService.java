package org.upsmf.grievance.service;

import org.springframework.scheduling.quartz.QuartzJobBean;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface JobService {

    //Schedule one time job.
    boolean scheduleOneTimeJob(String jobName, Class<? extends QuartzJobBean> jobClass, Date date);

    //Schedule cron recurring job.
    boolean scheduleCronJob(String jobName, Class<? extends QuartzJobBean> jobClass, Date date, String cronExpression);

    //Update one time job.
    boolean updateOneTimeJob(String jobName, Date date);

    //Update cron recurring job.
    boolean updateCronJob(String jobName, Date date, String cronExpression);

    //Unschedule scheduled job.
    boolean unScheduleJob(String jobName);

    //Delete a job.
    boolean deleteJob(String jobName);

    //Pause a job.
    boolean pauseJob(String jobName);

    //Resume a job.
    boolean resumeJob(String jobName);

    //Start a job instantly.
    boolean startJobNow(String jobName);

    //Check if job is already Running.
    boolean isJobRunning(String jobName);

    //Get list of all scheduled/Running jobs.
    List<Map<String, Object>> getAllJobs();

    //Check if job with given name is present.
    boolean isJobWithNamePresent(String jobName);

    //Get the current state of job.
    String getJobState(String jobName);

    //Stop a Job.
    boolean stopJob(String jobName);
}
