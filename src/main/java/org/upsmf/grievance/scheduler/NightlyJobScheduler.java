package org.upsmf.grievance.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.upsmf.grievance.constants.Constants;
import org.upsmf.grievance.dto.MailConfigDto;
import org.upsmf.grievance.dto.SearchDateRange;
import org.upsmf.grievance.dto.SearchRequest;
import org.upsmf.grievance.model.EmailDetails;
import org.upsmf.grievance.model.MailConfig;
import org.upsmf.grievance.model.User;
import org.upsmf.grievance.model.es.Ticket;
import org.upsmf.grievance.repository.MailConfigRepository;
import org.upsmf.grievance.service.EmailService;
import org.upsmf.grievance.service.IntegrationService;
import org.upsmf.grievance.service.SearchService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;

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

    @Value("${ticket.aggregator.mail.subject.for.raiser}")
    private String aggregatorSubject;

    @Value("${ticket.escalation.days}")
    private String adminEscalationDays;

    @Autowired
    private IntegrationService integrationService;

    @Autowired
    private MailConfigRepository mailConfigRepository;

    /**
     * @Scheduled(cron = "${cron.expression}")
     */
    @Scheduled(cron = "${nightly.job.cron.expression}", zone = "Asia/Kolkata")
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
     * @Scheduled(cron = "${cron.expression}")
     */
    @Scheduled(cron = "${escalation.job.cron.expression}", zone = "Asia/Kolkata")
    public void escalateTickets(){
        log.info("Starting the escalation job");
        List<MailConfigDto> escalationDays = getAll();
        if(escalationDays == null || escalationDays.isEmpty()) {
            log.info("Escalation config missing");
            return;
        }
        // assuming there will 1 active config
        Optional<MailConfigDto> mailConfigDto = escalationDays.stream().filter(x -> x.isActive()).findFirst();
        if(mailConfigDto.isPresent()) {
            int escalationPeriodInDays = mailConfigDto.get().getConfigValue();
            if(escalationPeriodInDays <= 0) {
                log.info("Escalation config found invalid value - {}", mailConfigDto.get());
                return;
            }
            long lastUpdateTimeBeforeEscalation = LocalDateTime.now().minusDays(escalationPeriodInDays).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long response = searchService.escalateTickets(lastUpdateTimeBeforeEscalation);
            log.info("No of tickets escalated "+response);
        }
    }

    /**
     * scheduler will run at 5 pm every day
     * @Scheduled(cron = "${cron.expression}")
     */
    @Scheduled(cron = "${ticket.aggregator.job.cron.expression}", zone = "Asia/Kolkata")
    public void newTicketsByUser(){
        log.info("Starting the ticket aggregator job");
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = null;
        try {
            // get all Nodal officers
            List<User> allUsersByRole = integrationService.getAllUsersByRole("NODALOFFICER");
            if(allUsersByRole.isEmpty()) {
                log.info("Email sending list is empty");
                return;
            }
            scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(10);
            // loop to get today's assigned tickets for nodal officer
            ScheduledThreadPoolExecutor finalScheduledThreadPoolExecutor = scheduledThreadPoolExecutor;
            allUsersByRole.stream().forEach(user -> {
                if(!user.getEmail().isBlank()) {
                    finalScheduledThreadPoolExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            // fetch user data
                            List<Ticket> openTicketsByID = searchService.getOpenTicketsByID(user.getId());
                            EmailDetails emailDetails = EmailDetails.builder().recipient(user.getEmail())
                                    .subject(aggregatorSubject).build();
                            log.info("Details - "+ user.getEmail() + " "+ "subject - "+ aggregatorSubject);
                            log.info("open tickets - ", openTicketsByID);
                            // send mail
                            if(openTicketsByID.size() > 0) {
                                emailService.sendMailTicketAggregateMailToNodalOfficer(emailDetails, user, openTicketsByID);
                            }
                        }
                    });
                }});
            while (finalScheduledThreadPoolExecutor.getQueue().size() > 0) {
                // do nothing
                log.debug("Queued Tasks in count - {}", finalScheduledThreadPoolExecutor.getQueue().size());
            }
            log.debug("No of tasks competed - {}", finalScheduledThreadPoolExecutor.getCompletedTaskCount());
        } catch (Exception e) {
            log.error("error in sending mail ", e);
        } finally {
            scheduledThreadPoolExecutor.shutdown();
            scheduledThreadPoolExecutor = null;
        }
    }

    public List<MailConfigDto> getAll() {
        Iterable<MailConfig> configIterable = mailConfigRepository.findAll();
        List<MailConfigDto> configs = new ArrayList<>();
        Iterator<MailConfig> iterator = configIterable.iterator();
        while (iterator.hasNext()) {
            MailConfig mailConfig = iterator.next();
            configs.add(new MailConfigDto(mailConfig));
        }
        return configs;
    }
}
