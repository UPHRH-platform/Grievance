package org.upsmf.grievance.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.upsmf.grievance.constants.Constants;
import org.upsmf.grievance.dto.SearchDateRange;
import org.upsmf.grievance.dto.SearchRequest;
import org.upsmf.grievance.model.EmailDetails;
import org.upsmf.grievance.service.DashboardService;
import org.upsmf.grievance.service.EmailService;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DynamicWeeklyJobScheduler {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private EmailService emailService;

    @Value("${email.ids}")
    private List<String> emailIds;

    @Value("${subject.bi.weekly.report}")
    private String subject;

    private ScheduledExecutorService service = null;

    @Value("${max.executor.thread}")
    private int MAX_EXECUTOR_THREAD;

    @PostConstruct
    private void init() {
        if (service != null) {
            return;
        }
        if (MAX_EXECUTOR_THREAD <= 0) {
            MAX_EXECUTOR_THREAD = 10;
        }
        service = Executors.newScheduledThreadPool(MAX_EXECUTOR_THREAD);
        // start scheduler on initialization
        initializeSchedulers();
    }

    private void initializeSchedulers() {
        // fetch config value from DB
        int fixedDelaysInDays = 10;
        schedule(10);
    }

    /**
     * all scheduler job will be configured here.
     */
    @Synchronized
    public void schedule(int fixedDelaysInDays) {
        log.info("SchedulerManager:schedule: Started scheduler job for email notifications");
        if(service == null) {
            log.info("configuring task executor");
            service = Executors.newScheduledThreadPool(MAX_EXECUTOR_THREAD);
        }
        // shutdown if service is running
        service.shutdownNow();
        // As per requirement, first mail will go without any delay
        // consecutive mail will be sent as per configured value in days
        service.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                long days = daysFromDB();
                sendMail(days);
            }
        }, 0, fixedDelaysInDays, TimeUnit.DAYS);

        log.info("SchedulerManager:schedule: Started job for sending emails to the users.");
    }

    private static Calendar calculateTargetCalender(Calendar todayCal, int runDay, int runTime) {
        Calendar targetCal = Calendar.getInstance();

        // Setting 6 - 6th Day of the Week : Friday
        targetCal.set(Calendar.DAY_OF_WEEK, runDay);

        // can be 0-23 - Eg: For Evening 5PM it will be 17
        targetCal.set(Calendar.HOUR_OF_DAY, runTime);

        // Setting 0 - For the Minute Value of 05:00
        targetCal.set(Calendar.MINUTE, 0);

        // Setting 0 - For the Second Value of 05:00:00
        targetCal.set(Calendar.SECOND, 0);
        log.debug("Target Calendar Date : " + targetCal.getTime());

        if (todayCal.after(targetCal)) {
            targetCal.set(Calendar.WEEK_OF_YEAR, targetCal.getWeeksInWeekYear() + 1);
        }
        return targetCal;
    }

    private void sendMail(long days) {
        log.info("Starting the Bi Weekly job");
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setDate(SearchDateRange.builder().to(Calendar.getInstance().getTimeInMillis())
                .from(LocalDateTime.now().minusDays(days).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()).build());
        Map<String, Object> response = dashboardService.dashboardReport(searchRequest);
        log.info("Response " + response);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.valueToTree(response);
        JsonNode assessmentMatrix = jsonNode.get(Constants.ASSESSMENT_MATRIX);
        log.info("Json node " + assessmentMatrix.toString());
        EmailDetails emailDetails = null;
        for (int i = 0; i < emailIds.size(); i++) {
            emailDetails = emailDetails.builder().recipient(emailIds.get(i)).msgBody(assessmentMatrix.toString())
                    .subject(subject).build();
            log.info("Details " + emailIds.get(i) + " " + response.get(Constants.ASSESSMENT_MATRIX) + " " + subject);
            emailService.sendMailToDGME(emailDetails, assessmentMatrix);
        }
    }

    private long daysFromDB() {
        long days = 0;
        return days;
    }
}
