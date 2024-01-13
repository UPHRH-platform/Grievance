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
import org.upsmf.grievance.service.DashboardService;
import org.upsmf.grievance.service.EmailService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class BiWeeklyJobScheduler {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private EmailService emailService;

    @Value("${email.ids}")
    private List<String> emailIds;

    @Value("${subject.bi.weekly.report}")
    private String subject;

    @Scheduled(cron = "0 1 0 */14 * ?")
    public void runBiWeeklyJob(){
        log.info("Starting the Bi Weekly job");
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setDate(SearchDateRange.builder().to(Calendar.getInstance().getTimeInMillis())
                .from(LocalDateTime.now().minusDays(14).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()).build());
        Map<String, Object> response = dashboardService.dashboardReport(searchRequest);
        log.info("Response "+response);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.valueToTree(response);
        JsonNode assessmentMatrix = jsonNode.get(Constants.ASSESSMENT_MATRIX);
        log.info("Json node "+assessmentMatrix.toString());
        EmailDetails emailDetails = null;
        for (int i=0;i<emailIds.size();i++){
            emailDetails = emailDetails.builder().recipient(emailIds.get(i)).msgBody(assessmentMatrix.toString())
                    .subject(subject).build();
            log.info("Details "+emailIds.get(i) + " "+response.get(Constants.ASSESSMENT_MATRIX)+ " "+ subject);
            emailService.sendMailToDGME(emailDetails, assessmentMatrix);
        }
    }
}

