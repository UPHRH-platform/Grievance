package org.upsmf.grievance.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.upsmf.grievance.constants.Constants;
import org.upsmf.grievance.dto.SearchDateRange;
import org.upsmf.grievance.dto.SearchRequest;
import org.upsmf.grievance.model.EmailDetails;
import org.upsmf.grievance.service.EmailService;
import org.upsmf.grievance.service.IntegrationService;
import org.upsmf.grievance.service.SearchService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class NightlyJobScheduler {

    @Autowired
    private SearchService searchService;

    @Autowired
    private EmailService emailService;

    @Value("${email.ids}")
    private List<String> emailIds;

    @Value("${subject.daily.report}")
    private String subject;

    @Value("${ticket.escalation.days}")
    private String adminEscalationDays;

    @Autowired
    private IntegrationService integrationService;

    @Scheduled(cron = "0 1 0 * * ?")
    public void runNightlyJob(){
        log.info("Starting the Nightly job");
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setDate(SearchDateRange.builder().to(Calendar.getInstance().getTimeInMillis())
                .from(LocalDateTime.now().minusDays(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()).build());
        Map<String, Object> response = searchService.dashboardReport(searchRequest);
        log.info("Response "+response);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.valueToTree(response);
        JsonNode assessmentMatrix = jsonNode.get(Constants.ASSESSMENT_MATRIX);
        log.info("Json node "+assessmentMatrix.toString());
        EmailDetails emailDetails = new EmailDetails();
        for (int i=0;i<emailIds.size();i++){
            emailDetails.builder().recipient(emailIds.get(i)).msgBody(assessmentMatrix.toString())
                    .subject(subject);
            log.info("Details "+emailIds.get(i) + " "+response.get(Constants.ASSESSMENT_MATRIX)+ " "+ subject);
            emailService.sendSimpleMail(emailDetails);
        }
    }

    /**
     * scheduler will run at interval of 4 hours
     */
    @Scheduled(cron = "0 0 */4 * * ?")
    public void escalateTickets(){
        log.info("Starting the escalation job");
        long lastUpdateTimeBeforeEscalation = LocalDateTime.now().minusDays(Integer.parseInt(adminEscalationDays)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long response = searchService.escalateTickets(lastUpdateTimeBeforeEscalation);
        log.info("No of tickets escalated "+response);
    }

    /**
     * scheduler will run at 5 pm every day
     */
    @Scheduled(cron = "0 0 17 * * ?")
    public void newTicketsByUser(){
        log.info("Starting the ticket aggregator job");
        // get all Nodal officers

        // loop to get today's assigned tickets for nodal officer
        // send mail
        log.info("No of tickets ");
    }
}
