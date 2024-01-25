package org.upsmf.grievance.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.util.StringUtils;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.dto.MailConfigDto;
import org.upsmf.grievance.dto.TicketAuditDto;
import org.upsmf.grievance.dto.TicketRequest;
import org.upsmf.grievance.dto.UpdateTicketRequest;
import org.upsmf.grievance.enums.TicketPriority;
import org.upsmf.grievance.enums.TicketStatus;
import org.upsmf.grievance.exception.DataUnavailabilityException;
import org.upsmf.grievance.exception.InvalidDataException;
import org.upsmf.grievance.exception.TicketException;
import org.upsmf.grievance.model.*;
import org.upsmf.grievance.repository.*;
import org.upsmf.grievance.repository.es.TicketRepository;
import org.upsmf.grievance.service.*;
import org.upsmf.grievance.util.DateUtil;
import org.upsmf.grievance.util.ErrorCode;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TicketServiceImpl implements TicketService {

    @Autowired
    @Qualifier("esTicketRepository")
    private TicketRepository esTicketRepository;

    @Autowired
    @Qualifier("ticketRepository")
    private org.upsmf.grievance.repository.TicketRepository ticketRepository;

    @Value("${ticket.escalation.days}")
    private String ticketEscalationDays;
    @Value("${mail.reminder.subject}")
    private String mailReminderSubject;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private AssigneeTicketAttachmentRepository assigneeTicketAttachmentRepository;

    @Autowired
    private RaiserTicketAttachmentRepository raiserTicketAttachmentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserDepartmentRepository userDepartmentRepository;
    @Autowired
    private TicketUserTypeRepository ticketUserTypeRepository;
    @Autowired
    private TicketCouncilRepository ticketCouncilRepository;

    @Autowired
    private TicketDepartmentRepository ticketDepartmentRepository;

    @Autowired
    private OtpService otpService;

    @Value("${feedback.base.url}")
    private String feedbackBaseUrl;

    @Value("${ticket.otp.enable}")
    private Boolean otpEnabled = true;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private TicketAuditService ticketAuditService;

    @Autowired
    private SchedulerConfigService schedulerConfigService;

    /**
     *
     * @param ticket
     * @return
     */
    @Transactional
    public Ticket saveWithAttachment(Ticket ticket, List<String> attachments) {
        // save ticket in postgres
        org.upsmf.grievance.model.Ticket psqlTicket = ticketRepository.save(ticket);
        auditTicketSave(psqlTicket);
        // update attachments if present
        if(attachments != null) {
            int count = 1;
            for(String url : attachments) {
                RaiserTicketAttachment raiserTicketAttachment = RaiserTicketAttachment.builder()
                        .attachment_url(url)
                        .ticketId(ticket.getId())
                        .build();
                String defaultName = "attachment".concat(String.valueOf(count++));
                String name = extractAttachmentNameFromGCPURl(url, defaultName);
                raiserTicketAttachment.setAttachmentName(name);
                raiserTicketAttachmentRepository.save(raiserTicketAttachment);
            }
        }
        // covert to ES ticket object
        org.upsmf.grievance.model.es.Ticket esticket = convertToESTicketObj(ticket);
        // save ticket in ES
        esTicketRepository.save(esticket);
        return psqlTicket;
    }

    private static String extractAttachmentNameFromGCPURl(String url, String defaultName) {
        Optional<String> optionalName = Arrays.stream(url.split("\\?")).findFirst();
        String name = defaultName;
        if(optionalName.isPresent()) {
            String attachmentUrl = optionalName.get();
            int lastIndexOf = attachmentUrl.lastIndexOf("/");
            if (lastIndexOf + 1 < attachmentUrl.length()) {
                name = attachmentUrl.substring(lastIndexOf + 1, attachmentUrl.length());
                int separatorIndex = name.indexOf("_");
                if(separatorIndex > 0) {
                    name = name.substring(separatorIndex+1, name.length());
                }
                log.info("attachment name - {}", name);
                // remove encoded string from name
                name = removeEncodedCharacters(name);
            }
        }
        return name;
    }

    private static String removeEncodedCharacters(String name) {
        name = name.replace("%20", " ");
        name = name.replace("%28", "(");
        name = name.replace("%29", ")");
        name = name.replace("%2D", "-");
        name = name.replace("%5F", "_");
        name = name.replace("%22", "_");
        name = name.replace("%2A", "_");
        name = name.replace("%2B", "_");
        name = name.replace("%3A", "_");
        name = name.replace("%E2%80", "_");
        name = name.replace("%AF", "_");

        log.info("attachment name after removing encoded characters - {}", name);
        return name;
    }

    /**
     * @param ticket
     */
    private void auditTicketSave(@NonNull Ticket ticket) {
        TicketAuditDto ticketAuditDto = TicketAuditDto.builder()
                .createdBy(ticket.getEmail())
                .ticketId(ticket.getId())
                .remark("New ticket")
                .updatedTime(ticket.getCreatedDate())
                .build();

        ticketAuditService.saveTicketAudit(ticketAuditDto);
    }

    @Override
    @Transactional
    public Ticket save(Ticket ticket) {
        // save ticket in postgres
        org.upsmf.grievance.model.Ticket psqlTicket = ticketRepository.save(ticket);
        // covert to ES ticket object
        org.upsmf.grievance.model.es.Ticket esticket = convertToESTicketObj(ticket);
        // save ticket in ES
        esTicketRepository.save(esticket);
        // TODO send mail
        return psqlTicket;
    }

    /**
     *
     * @param ticketRequest
     * @return
     * @throws Exception
     */
    @Override
    @Transactional
    public Ticket save(TicketRequest ticketRequest) throws Exception {
        // validate request
        validateTicketRequest(ticketRequest);

        // validate OTP
        if(otpEnabled) {
            boolean isValid = otpService.validateOtp(ticketRequest.getEmail(), ticketRequest.getOtp());
            if(!isValid) {
                throw new TicketException("Invalid mail OTP, Please enter correct OTP", ErrorCode.TKT_001,
                        "Error while matching mail OTP");
            } else {
                boolean isMobileOtpValid = otpService.validateMobileOtp(ticketRequest.getPhone(),
                        ticketRequest.getMobileOtp());

                if (!isMobileOtpValid) {
                    throw new TicketException("Invalid mobile OTP, Please enter correct OTP", ErrorCode.TKT_001,
                            "Error while matching mobile OTP");
                }
            }
        }
        // set default value for creating ticket
        Ticket ticket = createTicketWithDefault(ticketRequest);
        // create ticket
        ticket = saveWithAttachment(ticket, ticketRequest.getAttachmentUrls());
        // get raiser urls
        List<RaiserTicketAttachment> raiserTicketAttachments = raiserTicketAttachmentRepository
                .findByTicketId(ticket.getId());
        // send mail
        EmailDetails emailDetails = EmailDetails.builder().recipient(ticket.getEmail()).subject("New Complaint Registration").build();
        emailService.sendCreateTicketMail(emailDetails, ticket, raiserTicketAttachments);
        log.debug("ticket details - {}",ticket);
        return ticket;
    }

    private LocalDateTime getEscalationDateFromMailConfig(){
        LocalDateTime escalationDateTime = LocalDateTime.now().plus(Long.valueOf(ticketEscalationDays), ChronoUnit.DAYS);
        // get mail config value
        List<MailConfigDto> mailConfig = schedulerConfigService.getAll();
        log.debug("getting all config values - {}", mailConfig);
        if(mailConfig != null && !mailConfig.isEmpty()) {
            Optional<MailConfigDto> mailConfigDto = mailConfig.stream().filter(config -> config.isActive() && config.getAuthorityTitle().equalsIgnoreCase("SECRETARY"))
                    .findFirst();
            log.debug("finding matching config for secretary - {}", mailConfigDto);
            if(mailConfigDto.isPresent()) {
                MailConfigDto dto = mailConfigDto.get();
                if(dto.getConfigValue() != null && dto.getConfigValue() > 0) {
                    log.debug("config value for secretary - {}", dto.getConfigValue());
                    escalationDateTime = LocalDateTime.now().plus(Long.valueOf(dto.getConfigValue()), ChronoUnit.DAYS);
                }
            }
        }
        log.debug("escalation time of new tickets - {}", escalationDateTime);
        return escalationDateTime;
    }

    /**
     *
     * @param ticketRequest
     * @return
     * @throws Exception
     */
    private Ticket createTicketWithDefault(TicketRequest ticketRequest) throws Exception {

        Timestamp currentTimestamp = new Timestamp(DateUtil.getCurrentDate().getTime());
        LocalDateTime escalationDateTime = getEscalationDateFromMailConfig();

        Optional<TicketUserType> userTypeOptional = ticketUserTypeRepository
                .findById(ticketRequest.getTicketUserTypeId());

        if (!userTypeOptional.isPresent()) {
            throw new DataUnavailabilityException("Ticket User type does not exist");
        }

        Optional<TicketCouncil> ticketCouncilOptional = ticketCouncilRepository
                .findById(ticketRequest.getTicketCouncilId());

        if (!ticketCouncilOptional.isPresent()) {
            throw new DataUnavailabilityException("Ticket council does not exist");
        }

        Optional<TicketDepartment> ticketDepartmentOptional = ticketDepartmentRepository
                .findById(ticketRequest.getTicketDepartmentId());

        if (!ticketDepartmentOptional.isPresent()) {
            throw new DataUnavailabilityException("Ticket department does not exist");
        }

        Long userId = getFirstActiveUserByDepartmentId(ticketRequest.getTicketDepartmentId(), ticketRequest.getTicketCouncilId());

        String grievanceNodalEmail = null;
        if(userId == -1) {
            // get admin user
            Optional<User> adminUser = getOwner(String.valueOf(userId));
             if(adminUser.isPresent()) {
                 grievanceNodalEmail = adminUser.get().getEmail();
             }
        }
        Optional<User> userOptional = userRepository.findById(userId);

        Ticket createdTicket = Ticket.builder()
                .createdDate(new Timestamp(DateUtil.getCurrentDate().getTime()))
                .firstName(ticketRequest.getFirstName())
                .lastName(ticketRequest.getLastName())
                .phone(ticketRequest.getPhone())
                .email(ticketRequest.getEmail())
                .assignedToId(String.valueOf(userId))
                .description(ticketRequest.getDescription())
                .updatedDate(currentTimestamp)
                .lastUpdatedBy("-1")//need to get user details and add id or name
                .escalated(false)
                //.escalatedDate(Timestamp.valueOf(escalationDateTime))
                .escalatedTo("-1")
                .status(TicketStatus.OPEN)
                .requestType(ticketRequest.getRequestType())
                .priority(TicketPriority.LOW)
                .escalatedBy("-1")
                .reminderCounter(0L)
                .ticketUserType(userTypeOptional.get())
                .ticketCouncil(ticketCouncilOptional.get())
                .ticketDepartment(ticketDepartmentOptional.get())
                .ownerEmail(userOptional.isPresent() ? userOptional.get().getEmail() : null)
                .build();
        if(userId == -1 && grievanceNodalEmail != null && !grievanceNodalEmail.isBlank()) {
            createdTicket.setOwnerEmail(grievanceNodalEmail);
        }
        return createdTicket;
    }

    private @NonNull Long getFirstActiveUserByDepartmentId(Long departmentId, Long councilId) {
        if ((departmentId != null && councilId == null) || (departmentId == null && councilId != null)) {
            log.error("Missing one of attributes department id or council id - both are allowed or none");
            throw new InvalidDataException("Both council and department id are allowed or none");
        }

        if (departmentId == null && councilId == null) {
            log.info(">>>>>>>>> Did not found department or council id information - ticket will be unassigned");
            return -1L;
        }

        Optional<TicketDepartment> ticketDepartmentOptional = ticketDepartmentRepository
                .findByIdAndTicketCouncilId(departmentId, councilId);

        if (!ticketDepartmentOptional.isPresent()) {
            log.error("Unable to find user ticket department");
            throw new DataUnavailabilityException("Unable to find ticket department");
        }

        Optional<Long> otherAssingedId = getOhterAssignmentId(ticketDepartmentOptional.get());

        if (otherAssingedId.isPresent()) {
            return otherAssingedId.get();
        }

        return getUserByDepartmentAndCouncilId(departmentId, councilId);
    }

    private Long getUserByDepartmentAndCouncilId(Long departmentId, Long councilId) {
        List<UserDepartment> userDepartmentList = userDepartmentRepository
                .findAllByDepartmentIdAndCouncilId(departmentId, councilId);

        if (userDepartmentList == null || userDepartmentList.isEmpty()) {
            log.error("Unable to find user departments - department id {} | council id {}", departmentId, councilId);
            throw new DataUnavailabilityException("Unable to find user department details");
        }

        List<User> userList = userRepository.findAllByUserDepartmentIn(userDepartmentList);

        List<User> activeUserList = userList.stream()
                .filter(user -> user.getStatus() == 1)
                .collect(Collectors.toList());

        if (activeUserList == null || activeUserList.isEmpty()) {
            log.error("Unable to find any acivite user from user list");
            throw new DataUnavailabilityException("There no active user for given council and department");
        }

        Optional<Long> minTicketUserIdOptional = getMinimumTicketedAssignedUser(activeUserList);

        if (minTicketUserIdOptional.isPresent()) {
            return minTicketUserIdOptional.get();
        } else {
            Optional<Long> activeUser = activeUserList.stream()
                    .map(User::getId)
                    .skip(new Random().nextInt(activeUserList.size()))
                    .findFirst();

            if (!activeUser.isPresent()) {
                log.error("Unable to find random user from the active list");
                throw new DataUnavailabilityException("Unable to find user for given council and department");
            }

            return activeUser.get();
        }
    }

    /**
     * @param activeUserList
     * @return
     */
    private Optional<Long> getMinimumTicketedAssignedUser(List<User> activeUserList) {
        try {
            return activeUserList.stream()
                    .reduce((user1, user2) -> {
                        if (getTicketCountByUserId(user1.getId()) < getTicketCountByUserId(user2.getId())) {
                            return user1;
                        } else {
                            return user2;
                        }
                    })
                    .map(User::getId);
        }catch (Exception e) {
            log.error("Error while calculating minimum ticket assigned user");
            return Optional.empty();
        }
    }

    private Long getTicketCountByUserId(Long userId) {
        List<Ticket> ticketList = ticketRepository.findAllByAssignedToId(userId.toString());

        if (ticketList == null || ticketList.isEmpty()) {
            return 0L;
        }

        return ticketList.stream()
                .filter(ticket -> ticket.getStatus() != null)
                .filter(ticket -> ticket.getStatus().equals(TicketStatus.OPEN))
                .count();
    }

    /**
     * It will check if department is other then it'll return back with -1 (assignedTo value)
     *
     * @param ticketDepartment
     * @return
     */
    private Optional<Long> getOhterAssignmentId(@NonNull TicketDepartment ticketDepartment) {
        if ("OTHER".equalsIgnoreCase(ticketDepartment.getTicketDepartmentName())) {

            return Optional.ofNullable(-1L);
        }
        return Optional.empty();
    }

    private Optional<Long> findGrievanceNodalAdmin(@NonNull TicketDepartment ticketDepartment) {
        if ("Other".equalsIgnoreCase(ticketDepartment.getTicketDepartmentName())) {

            Optional<UserDepartment> userDepartmentOptional = userDepartmentRepository
                    .findByCouncilNameAndCouncilName("OTHER", "OTHER");

            if (userDepartmentOptional.isPresent()) {
                Optional<User> userOptional = userRepository
                        .findByUserDepartment(userDepartmentOptional.get());

                if (userOptional.isPresent()) {
                    return Optional.ofNullable(userOptional.get().getId());
                }
            }
        }

        return Optional.empty();
    }

    /**
     *
     * @param updateTicketRequest
     * @return
     */
    @Override
    @Transactional
    public Ticket update(UpdateTicketRequest updateTicketRequest) throws Exception {
        //  validate ticket
        validateUpdateTicketRequest(updateTicketRequest);
        // check if the ticket exists
        Optional<Ticket> ticketDetails = getTicketDetailsByID(updateTicketRequest.getId());
        Ticket ticket = null;
        if(!ticketDetails.isPresent()) {
            // TODO throw exception
            throw new RuntimeException("Ticket does not exists");
        }
        ticket = ticketDetails.get();
        boolean oldIsJunkValue = ticket.isJunk();
        TicketStatus oldStatusValue = ticket.getStatus();
        // set incoming values
        setUpdateTicket(updateTicketRequest, ticket);
        // if ticket is getting reopened we will reset escalation date time
        if(updateTicketRequest.getStatus().name().equalsIgnoreCase("OPEN")
                && (oldStatusValue.name().equalsIgnoreCase("INVALID")
                || oldStatusValue.name().equalsIgnoreCase("CLOSED")) ) {
            // sending reopen ticket mail to nodal officer
            log.info("ticket is getting reopened we will reset escalation date time - {}", ticket.getId());
            ticket.setEscalatedDate(null);
        }
        //handle un-junk
        if (Boolean.FALSE.equals(updateTicketRequest.getIsJunk()) && Boolean.TRUE.equals(oldIsJunkValue)) {
            ticket.setPriority(TicketPriority.LOW);
        }
        // update ticket in DB
        ticketRepository.save(ticket);
        ticket = getTicketById(ticket.getId());
        // check if ticket exists in ES
        Optional<org.upsmf.grievance.model.es.Ticket> esTicketDetails = esTicketRepository.findOneByTicketId(updateTicketRequest.getId());
        org.upsmf.grievance.model.es.Ticket updatedESTicket = convertToESTicketObj(ticket);
        if(esTicketDetails.isPresent()) {
            // TODO revisit this
            esTicketRepository.deleteById(esTicketDetails.get().getId());
            updatedESTicket.setRating(esTicketDetails.get().getRating());
        }
        org.upsmf.grievance.model.es.Ticket curentUpdatedTicket=esTicketRepository.save(updatedESTicket);
        //send mail to end user
        curentUpdatedTicket.getEmail();
        curentUpdatedTicket.getTicketId();
        if(curentUpdatedTicket.getStatus().name().equalsIgnoreCase(TicketStatus.CLOSED.name())) {
            sendMailForFeedbackLinkAndEmail(ticket);
            return ticket;
        } else if (curentUpdatedTicket.getStatus().name().equalsIgnoreCase(TicketStatus.INVALID.name())) {
            ticket.setJunk(updateTicketRequest.getIsJunk());
            sendMailForFeedbackLinkAndEmailForJunkMail(ticket);
            return ticket;
        }else if (updateTicketRequest.getIsNudged() != null && updateTicketRequest.getIsNudged()
                && !org.apache.commons.lang.StringUtils.isEmpty(updateTicketRequest.getCc())) {
            if(ticket.getAssignedToId() != null && !ticket.getAssignedToId().equalsIgnoreCase("-1")) {
                sendMailToNodalAsync(ticket);
            } else if(ticket.getAssignedToId() != null && ticket.getAssignedToId().equalsIgnoreCase("-1")) {
                sendNudgeMailToGrievanceNodal(ticket);
            }
            return ticket;
        } else {
            ticket.setOther(updateTicketRequest.getIsOther());

            if (Boolean.FALSE.equals(updateTicketRequest.getIsJunk()) && Boolean.TRUE.equals(oldIsJunkValue)) {
                sendMailForUnJunk(ticket, curentUpdatedTicket);
            } else if (ticket.getAssignedToId() != null && ticket.getAssignedToId().equalsIgnoreCase("-1")) {
                sendMailToGrievanceNodal(ticket, curentUpdatedTicket);
            } else if(updateTicketRequest.getStatus().name().equalsIgnoreCase("OPEN")
                    && oldStatusValue.name().equalsIgnoreCase("CLOSED") ) {
                // sending reopen ticket mail to nodal officer
                log.info("sending reopen ticket mail to nodal officer - {}", ticket.getOwnerEmail());
                if(!ticket.getAssignedToId().equalsIgnoreCase("-1")
                        && ticket.getOwnerEmail() != null
                        && !ticket.getOwnerEmail().isBlank()) {
                    sendReopenTicketMailToNodal(ticket, curentUpdatedTicket);
                }
                sendReopenTicketMailToRaiser(ticket, curentUpdatedTicket);
                return ticket;
            }
            sendUpdateTicketMail(ticket, curentUpdatedTicket);
            return ticket;
        }
    }

    private void sendReopenTicketMailToRaiser(Ticket ticket, org.upsmf.grievance.model.es.Ticket curentUpdatedTicket) {
        Ticket finalTicket = ticket;
        org.upsmf.grievance.model.es.Ticket finalCurentUpdatedTicket = curentUpdatedTicket;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                EmailDetails resolutionOfYourGrievance = EmailDetails.builder().subject("Re-opening of Your Grievance Ticket - " + finalCurentUpdatedTicket.getTicketId()).recipient(finalCurentUpdatedTicket.getEmail()).build();
                emailService.sendUpdateTicketMail(resolutionOfYourGrievance, finalTicket);
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private void sendReopenTicketMailToNodal(Ticket ticket, org.upsmf.grievance.model.es.Ticket curentUpdatedTicket) {
        Ticket finalTicket = ticket;
        org.upsmf.grievance.model.es.Ticket finalCurentUpdatedTicket = curentUpdatedTicket;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                EmailDetails emailDetails = EmailDetails.builder()
                        .subject("Ticket Re-opened with ID - " + finalCurentUpdatedTicket.getTicketId())
                        .recipient(finalTicket.getOwnerEmail())
                        .build();
                emailService.sendMailToNodalOfficer(emailDetails, finalTicket);
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }
    private void sendUpdateTicketMail(Ticket ticket, org.upsmf.grievance.model.es.Ticket curentUpdatedTicket) {
        Ticket finalTicket = ticket;
        org.upsmf.grievance.model.es.Ticket finalCurentUpdatedTicket = curentUpdatedTicket;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                EmailDetails resolutionOfYourGrievance = EmailDetails.builder().subject("Resolution of Your Grievance - " + finalCurentUpdatedTicket.getTicketId()).recipient(finalCurentUpdatedTicket.getEmail()).build();
                emailService.sendUpdateTicketMail(resolutionOfYourGrievance, finalTicket);
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }
    private void sendMailToGrievanceNodal(Ticket ticket, org.upsmf.grievance.model.es.Ticket curentUpdatedTicket) {
        Ticket finalTicket = ticket;
        org.upsmf.grievance.model.es.Ticket finalCurentUpdatedTicket = curentUpdatedTicket;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                EmailDetails resolutionOfYourGrievance = EmailDetails.builder().subject("Resolution of Your Grievance - " + finalCurentUpdatedTicket.getTicketId()).recipient(finalCurentUpdatedTicket.getEmail()).build();
                emailService.sendMailToGrievanceNodal(resolutionOfYourGrievance, finalTicket);
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }
    private void sendMailForFeedbackLinkAndEmail(Ticket ticket) {
        generateFeedbackLinkAndEmail(ticket);
    }
    private void sendMailForUnJunk(Ticket ticket, org.upsmf.grievance.model.es.Ticket curentUpdatedTicket) {
        Ticket finalTicket = ticket;
        org.upsmf.grievance.model.es.Ticket finalCurentUpdatedTicket = curentUpdatedTicket;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                EmailDetails emailDetails = EmailDetails.builder()
                        .subject("Ticket Re-opened with ID - " + finalCurentUpdatedTicket.getTicketId())
                        .recipient(finalTicket.getOwnerEmail())
                        .build();

                emailService.sendUnjunkMail(emailDetails, finalTicket);
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }
    private void sendNudgeMailToGrievanceNodal(Ticket ticket) {
        Ticket finalTicket = ticket;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                String subject = "Nudge from Secretary, UPSMF in an Unassigned Ticket - Ticket ID: ".concat(String.valueOf(finalTicket.getId()));
                EmailDetails emailDetails = EmailDetails.builder()
                        .subject(subject)
                        .build();
                emailService.sendNudgeMailToGrievanceNodal(emailDetails, finalTicket);
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }
    private void sendMailToNodalAsync(Ticket ticket) {
        Ticket finalTicket = ticket;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                sendMailToNodal(finalTicket);
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private void sendMailForFeedbackLinkAndEmailForJunkMail(Ticket ticket) {
        Ticket finalTicket = ticket;
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                generateFeedbackLinkAndEmailForJunkTicket(finalTicket);
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private void sendMailToNodal(Ticket ticket) {
        String subject = "Nudge from Secretary, UPSMF in an Assigned Ticket - Ticket ID: ".concat(String.valueOf(ticket.getId()));
        EmailDetails emailDetails = EmailDetails.builder()
                .subject(subject)
                .build();

        emailService.sendMailToNodalOfficers(emailDetails, ticket);
    }

    private void generateFeedbackLinkAndEmail(Ticket curentUpdatedTicket) {
        List<Comments> comments = commentRepository.findAllByTicketId(curentUpdatedTicket.getId());
        log.error("Ticket for generateFeedbackLinkAndEmail - {}", curentUpdatedTicket);
        List<AssigneeTicketAttachment> assigneeTicketAttachments = assigneeTicketAttachmentRepository
                .findByTicketId(curentUpdatedTicket.getId());
        List<RaiserTicketAttachment> raiserTicketAttachments = raiserTicketAttachmentRepository
                .findByTicketId(curentUpdatedTicket.getId());
        log.error("comments fetch for generateFeedbackLinkAndEmail - {}", comments);
        Comments latestComment =null;
        if(comments!=null && comments.size() > 0) {
            latestComment = comments.get(comments.size()-1);
        }
        String comment = latestComment!=null?latestComment.getComment():"";
        log.error("latestComment for generateFeedbackLinkAndEmail - {}", comments);
        log.error("Comment for generateFeedbackLinkAndEmail - {}", comment);
        String link = feedbackBaseUrl.concat("?").concat("guestName=")
                .concat(curentUpdatedTicket.getFirstName().concat("%20").concat(curentUpdatedTicket.getLastName()))
                .concat("&ticketId=").concat(String.valueOf(curentUpdatedTicket.getId()))
                .concat("&resolutionComment=").concat(comment)
                .concat("&email=").concat(curentUpdatedTicket.getEmail())
                .concat("&phone=").concat(curentUpdatedTicket.getPhone())
                .concat("&ticketTitle=").concat(curentUpdatedTicket.getDescription());
        EmailDetails resolutionOfYourGrievance = EmailDetails.builder().subject("Resolution of Your Grievance").recipient(curentUpdatedTicket.getEmail()).build();
        emailService.sendClosedTicketMail(resolutionOfYourGrievance, curentUpdatedTicket, comment, assigneeTicketAttachments, link, raiserTicketAttachments);
    }
    private void generateFeedbackLinkAndEmailForJunkTicket(Ticket curentUpdatedTicket) {
        List<Comments> comments = commentRepository.findAllByTicketId(curentUpdatedTicket.getId());
        Comments latestComment =null;
        if(comments!=null && comments.size() > 0) {
            latestComment = comments.get(comments.size()-1);
        }
        String comment = latestComment!=null?latestComment.getComment():"";
        log.error("comments for generateFeedbackLinkAndEmailForJunkTicket - {}", comments);
        log.error("Comment for generateFeedbackLinkAndEmailForJunkTicket - {}", comment);
        String link = feedbackBaseUrl.concat("?").concat("guestName=")
                .concat(curentUpdatedTicket.getFirstName().concat("%20").concat(curentUpdatedTicket.getLastName()))
                .concat("&ticketId=").concat(String.valueOf(curentUpdatedTicket.getId()))
                .concat("&resolutionComment=").concat(comment)
                .concat("&email=").concat(curentUpdatedTicket.getEmail())
                .concat("&phone=").concat(curentUpdatedTicket.getPhone())
                .concat("&ticketTitle=").concat(curentUpdatedTicket.getDescription());
        EmailDetails resolutionOfYourGrievance = EmailDetails.builder().subject("Resolution of Your Grievance").recipient(curentUpdatedTicket.getEmail()).build();
        emailService.sendJunkMail(resolutionOfYourGrievance, curentUpdatedTicket, comment, Collections.EMPTY_LIST, link);
    }
    @Override
    public Ticket getTicketById(long id) {
        if(id <= 0) {
            throw new RuntimeException("Invalid Ticket ID");
        }
        Optional<Ticket> ticketDetails = getTicketDetailsByID(id);
        if(!ticketDetails.isPresent()) {
            throw new RuntimeException("Invalid Ticket ID");
        }
        return ticketDetails.get();
    }

    /**
     *
     * @param updateTicketRequest
     * @param ticket
     */
    private void setUpdateTicket(UpdateTicketRequest updateTicketRequest, Ticket ticket) throws Exception {
        // TODO check request role and permission

        TicketAuditDto ticketAuditDto = TicketAuditDto.builder()
                .createdBy(ticket.getEmail())
                .updatedBy(updateTicketRequest.getRequestedBy())
                .ticketId(ticket.getId())
                .build();

        if (updateTicketRequest.getRequestedBy() != null && !updateTicketRequest.getRequestedBy().isBlank()) {
            Optional<User> userOptional = userRepository.findByKeycloakId(updateTicketRequest.getRequestedBy());

            if (userOptional.isPresent()) {
                ticketAuditDto.setUpdatedBy(userOptional.get().getEmail());
                ticketAuditDto.setUpdatedByUserId(userOptional.get().getId().toString());  //id is primary key of user so null check has been skipped.
            }
        }

        if(updateTicketRequest.getStatus()!=null) {
            ticketAuditDto.setAttribute("STATUS");
            updateTicketAudit(ticketAuditDto, ticket.getStatus().name(),
                    updateTicketRequest.getStatus().name(), "Ticket status has been updated");

            if (TicketStatus.CLOSED.name().equalsIgnoreCase(updateTicketRequest.getStatus().name())) {
                ticket.setJunk(false);
                updateTicketRequest.setIsJunk(false);
            }
            ticket.setStatus(updateTicketRequest.getStatus());
        }

        if (updateTicketRequest.getIsNudged() != null && updateTicketRequest.getIsNudged()){
            if (ticket.getReminderCounter() != null){
                ticketAuditDto.setAttribute("REMINDER_COUNT");

                updateTicketAudit(ticketAuditDto, ticket.getReminderCounter().toString(),
                        String.valueOf(ticket.getReminderCounter() + 1),
                        "Ticket reminder value has been updated");

                ticket.setReminderCounter(ticket.getReminderCounter() + 1);
            } else {
                ticket.setReminderCounter(0L);
            }
        }

        if(updateTicketRequest.getCc()!=null && !updateTicketRequest.getCc().isBlank()) {
            ticket.setAssignedToId(updateTicketRequest.getCc());

            Optional<User> userOptional = getOwner(ticket.getAssignedToId());

            if (userOptional.isPresent()) {
                ticketAuditDto.setAttribute("OWNER_MAIL");
                updateTicketAudit(ticketAuditDto, ticket.getOwnerEmail(),
                        userOptional.get().getEmail(),
                        "Ticket owner has been changed");

                ticket.setOwnerEmail(userOptional.get().getEmail());
            }

            if ("-1".equalsIgnoreCase(updateTicketRequest.getCc())) {
                ticketAuditDto.setAttribute("COUNCIL");
                updateTicketAudit(ticketAuditDto,
                        ticket.getTicketCouncil() != null ? ticket.getTicketCouncil().getTicketCouncilName() : null,
                        "OTHER",
                        "Ticket council assigned to other");

                ticketAuditDto.setAttribute("DEPARTMENT");
                updateTicketAudit(ticketAuditDto,
                        ticket.getTicketDepartment() != null ? ticket.getTicketDepartment().getTicketDepartmentName() : null,
                        "OTHER",
                        "Ticket department assigned to other");

                updateCouncilDepartmentForOther(ticket);
            }
        }

        if(updateTicketRequest.getPriority()!=null) {
            ticketAuditDto.setAttribute("PRIORITY");
            updateTicketAudit(ticketAuditDto, ticket.getPriority() != null ? ticket.getPriority().name() : "",
                    updateTicketRequest.getPriority().name(),
                    "Ticket priority value has been updated");

            ticket.setPriority(updateTicketRequest.getPriority());
        }

        if (Boolean.TRUE.equals(updateTicketRequest.getIsOther())) {
            ticketAuditDto.setAttribute("IS_OTHER");
            updateTicketAudit(ticketAuditDto, ticket.getOther() != null ? ticket.getOther().toString() : "" ,
                    Boolean.TRUE.toString(),
                    "Ticket set other value has been updated");

            ticket.setOther(updateTicketRequest.getIsOther());

            ticketAuditDto.setAttribute("OTHER_BY_REASON");
            updateTicketAudit(ticketAuditDto, ticket.getOtherByReason() != null ? ticket.getOtherByReason() : "" ,
                    updateTicketRequest.getOtherByReason(),
                    "Ticket otherBy reason  has been updated");

            ticket.setOtherByReason(updateTicketRequest.getOtherByReason());
        }

        if(Boolean.TRUE.equals(updateTicketRequest.getIsJunk())) {
            ticketAuditDto.setAttribute("IS_JUNK");
            updateTicketAudit(ticketAuditDto, String.valueOf(ticket.isJunk()) ,
                    Boolean.TRUE.toString(),
                    "Ticket junk status has been updated");

            ticket.setJunk(updateTicketRequest.getIsJunk());

            ticketAuditDto.setAttribute("JUNK_BY_REASON");
            updateTicketAudit(ticketAuditDto, ticket.getJunkByReason() != null ? ticket.getJunkByReason() : "" ,
                    updateTicketRequest.getJunkByReason(),
                    "Ticket junkBy reason has been updated");

            ticket.setJunkByReason(updateTicketRequest.getJunkByReason());

            if (updateTicketRequest.getRequestedBy() == null || updateTicketRequest.getRequestedBy().isBlank()) {

                ticketAuditDto.setAttribute("JUNKED_BY");
                updateTicketAudit(ticketAuditDto, ticket.getJunkedBy() != null ? ticket.getJunkedBy() : "" ,
                        "Grievance Nodal",
                        "Ticket junkBy user has been updated");

                ticket.setJunkedBy("-1");
            } else {
                User user = userRepository.findByKeycloakId(updateTicketRequest.getRequestedBy()).orElseThrow();
                String firstName = user.getFirstName();
                String lastName = user.getLastname();
                String junkedBy = firstName + " " + lastName;

                ticketAuditDto.setAttribute("JUNKED_BY");
                updateTicketAudit(ticketAuditDto, ticket.getJunkedBy() != null ? ticket.getJunkedBy() : "" ,
                        String.valueOf(user.getId()),
                        "Ticket junkBy user has been updated");

                ticket.setJunkedBy(String.valueOf(user.getId()));
            }
        } else if (Boolean.FALSE.equals(updateTicketRequest.getIsJunk())) {
            ticket.setJunk(false);
        }
        ticket.setUpdatedDate(new Timestamp(DateUtil.getCurrentDate().getTime()));
        // update assignee comments
        if(updateTicketRequest.getComment()!=null) {

            ticketAuditDto.setAttribute("COMMENT");
            updateTicketAudit(ticketAuditDto, "" , updateTicketRequest.getComment(),
                    "New comment has been added");

            Comments comments = Comments.builder().comment(updateTicketRequest.getComment())
                    .userId(updateTicketRequest.getRequestedBy())
                    .ticketId(ticket.getId())
                    .build();
            commentRepository.save(comments);
        }

        // update assignee attachment url
        if(updateTicketRequest.getAssigneeAttachmentURLs() != null) {

            ticketAuditDto.setAttribute("ATTACHMENT");
            updateTicketAudit(ticketAuditDto, "" , " ",
                    "New attachment has been added");
            int count = 1;
            for (String url : updateTicketRequest.getAssigneeAttachmentURLs()) {
                AssigneeTicketAttachment assigneeTicketAttachment = AssigneeTicketAttachment.builder()
                        .userId(updateTicketRequest.getRequestedBy())
                        .ticketId(ticket.getId())
                        .attachment_url(url).build();
                String defaultName = "attachment".concat(String.valueOf(count++));
                String name = extractAttachmentNameFromGCPURl(url, defaultName);
                assigneeTicketAttachment.setAttachmentName(name);
                assigneeTicketAttachmentRepository.save(assigneeTicketAttachment);
            }
        }

        if (updateTicketRequest.getTicketCouncilId() != null && updateTicketRequest.getTicketCouncilId() != 0) {
            Optional<TicketCouncil> ticketCouncilOptional = ticketCouncilRepository.findById(updateTicketRequest.getTicketCouncilId());

            if (ticketCouncilOptional.isPresent()) {

                ticketAuditDto.setAttribute("COUNCIL");
                updateTicketAudit(ticketAuditDto,
                        ticket.getTicketCouncil() != null ? ticket.getTicketCouncil().getTicketCouncilName() : null,
                        ticketCouncilOptional.get().getTicketCouncilName(),
                        "Ticket council has been updated");

                ticket.setTicketCouncil(ticketCouncilOptional.get());
            }
        }

        if (updateTicketRequest.getTicketDepartmentId() != null && updateTicketRequest.getTicketDepartmentId() != 0) {
            Optional<TicketDepartment> ticketDepartmentOptional = ticketDepartmentRepository.findById(updateTicketRequest.getTicketDepartmentId());

            if (ticketDepartmentOptional.isPresent()) {

                ticketAuditDto.setAttribute("DEPARTMENT");
                updateTicketAudit(ticketAuditDto,
                        ticket.getTicketDepartment() != null ? ticket.getTicketDepartment().getTicketDepartmentName() : null,
                        ticketDepartmentOptional.get().getTicketDepartmentName(),
                        "Ticket department has been updated");

                ticket.setTicketDepartment(ticketDepartmentOptional.get());
            }
        }
    }

    /**
     * @param ticketAuditDto
     * @param oldValue
     * @param newValue
     * @param remark
     */
    private void updateTicketAudit(@NonNull TicketAuditDto ticketAuditDto,
                                   String oldValue, String newValue, String remark) {

        if ((oldValue != null && newValue != null) && !oldValue.equalsIgnoreCase(newValue)) {

            try {
                log.info(">>>>>>>>>>>>> Ticket audit - ticket id {} : old vlaue {} : new value {}",
                        ticketAuditDto.getTicketId(), oldValue, newValue);

                ticketAuditDto.setOldValue(oldValue);
                ticketAuditDto.setNewValue(newValue);
                ticketAuditDto.setRemark(remark);
                ticketAuditDto.setUpdatedTime(new Timestamp(DateUtil.getCurrentDate().getTime()));

                ticketAuditService.saveTicketAudit(ticketAuditDto);
            } catch (Exception e) {
                log.error("Error while trying to save ticket audit for tiket update value");
            }
        }
    }

    /**
     * @param assignedToId
     * @return
     */
    public Optional<User> getOwner(String assignedToId) {
        if (org.apache.commons.lang.StringUtils.isBlank(assignedToId)) {
            log.error(">>>>>>>>>> Invalid assignedTo Id - Unable to find value");

            return Optional.empty();
        }

        Optional<User> userOptional = Optional.empty();

        try {
            if ("-1".equalsIgnoreCase(assignedToId)) {
                Optional<UserDepartment> userDepartmentOptional = userDepartmentRepository
                        .findByCouncilNameAndCouncilName("OTHER", "OTHER");

                if (!userDepartmentOptional.isPresent()) {
                    log.error("Unable to find any user department which is tagged to OTHER (Council & Department)");
                }
                userOptional = userRepository.findByUserDepartment(userDepartmentOptional.get());

            } else {
                Long userId = Long.valueOf(assignedToId);
                userOptional = userRepository.findById(userId);
            }
        } catch (NumberFormatException e) {
            log.error("Error while parsing assinged to");
        } catch (Exception e) {
            log.error("Error while finding owner mail id");
        }

        return userOptional;
    }

    private void updateCouncilDepartmentForOther(@NonNull Ticket ticket) {
        Optional<UserDepartment> userDepartmentOptional = userDepartmentRepository
                .findByCouncilNameAndCouncilName("OTHER", "OTHER");

        if (!userDepartmentOptional.isPresent()) {
            log.error("Update ticket - Unable to find any user department which is tagged to OTHER (Council & Department)");
        }

        Optional<TicketDepartment> ticketDepartmentOptional = ticketDepartmentRepository.findById(userDepartmentOptional.get().getDepartmentId());

        if (ticketDepartmentOptional.isPresent()) {
            ticket.setTicketDepartment(ticketDepartmentOptional.get());
        }

        Optional<TicketCouncil> ticketCouncilOptional = ticketCouncilRepository.findById(userDepartmentOptional.get().getCouncilId());
        if (ticketCouncilOptional.isPresent()) {
            ticket.setTicketCouncil(ticketCouncilOptional.get());
        }
    }

    /**
     *
     * @param ticket
     * @return
     */
    private org.upsmf.grievance.model.es.Ticket convertToESTicketObj(Ticket ticket) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DateUtil.DEFAULT_DATE_FORMATTS);

        log.info(">>>>>>>>>>>>>>>>>>>>>> system time from ticket data: "
                + ticket.getCreatedDate().toLocalDateTime().format(dateTimeFormatter));

        String assingedToName = "";
        String junkByName = "";

//        Calculating assingedTo name for ES record
        if (!org.apache.commons.lang.StringUtils.isBlank(ticket.getAssignedToId())) {
            try {
                Optional<User> userOptional = userOptional = getOwner(ticket.getAssignedToId());

                if (userOptional.isPresent()) {
                    assingedToName = userOptional.get().getFirstName() + " " + userOptional.get().getLastname();
                }
            }catch (NumberFormatException e) {
                log.error("Error while parsing assinged to value");
                throw new InvalidDataException("AssignTo only support number");
            } catch (Exception e) {
                log.error("Error while calculating assign to value");
                throw new InvalidDataException("Invalid assignedTo value");
            }
        }

//      Calculating junkedBy name for ES record
        if (!org.apache.commons.lang.StringUtils.isBlank(ticket.getJunkedBy())) {
            try {
                Optional<User> userOptional = getOwner(ticket.getJunkedBy());

                if (userOptional.isPresent()) {
                    junkByName = userOptional.get().getFirstName() + " " + userOptional.get().getLastname();
                }
            }catch (NumberFormatException e) {
                log.error("Error while parsing assinged to value");
                throw new InvalidDataException("AssignTo only support number");
            } catch (Exception e) {
                log.error("Error while calculating assign to value");
                throw new InvalidDataException("Invalid assignedTo value");
            }
        }

        // get user details based on ID
        return org.upsmf.grievance.model.es.Ticket.builder()
                .ticketId(ticket.getId())
                .firstName(ticket.getFirstName())
                .lastName(ticket.getLastName())
                .phone(ticket.getPhone())
                .email(ticket.getEmail())
                .ownerEmail(ticket.getOwnerEmail())
//                .requesterType(ticket.getRequesterType()) //TODO: rkr: replace with user type
                .assignedToId(ticket.getAssignedToId())
                .assignedToName(assingedToName) // get user details based on ID
                .description(ticket.getDescription())
                .junk(ticket.isJunk())
                .other(ticket.getOther())
                .junkedBy(ticket.getJunkedBy())
                .junkedByName(junkByName)
                .createdDate(ticket.getCreatedDate().toLocalDateTime().format(dateTimeFormatter))
                .createdDateTS(ticket.getCreatedDate().getTime())
                .updatedDate(ticket.getUpdatedDate().toLocalDateTime().format(dateTimeFormatter))
                .updatedDateTS(ticket.getUpdatedDate().getTime())
                .lastUpdatedBy(ticket.getLastUpdatedBy())
                .escalated(ticket.isEscalated())
                .escalatedDate(ticket.getEscalatedDate() != null ? ticket.getEscalatedDate().toLocalDateTime().format(dateTimeFormatter) : null)
                .escalatedDateTS(ticket.getEscalatedDate() != null ? ticket.getEscalatedDate().getTime() : -1)
                .status(ticket.getStatus())
                .requestType(ticket.getRequestType())
                .priority(ticket.getPriority())
                .escalatedBy(ticket.getEscalatedBy())
                .escalatedTo(ticket.getEscalatedTo())
                .escalatedToAdmin(ticket.isEscalatedToAdmin())
                .reminderCounter(ticket.getReminderCounter())
                .ticketUserTypeId(ticket.getTicketUserType() != null ? ticket.getTicketUserType().getId() : -1)
                .ticketUserTypeName(ticket.getTicketUserType() != null ? ticket.getTicketUserType().getUserTypeName() : "")
                .ticketCouncilId(ticket.getTicketCouncil() != null ? ticket.getTicketCouncil().getId() : -1)
                .ticketCouncilName(ticket.getTicketCouncil() != null ? ticket.getTicketCouncil().getTicketCouncilName() : "")
                .ticketDepartmentId(ticket.getTicketDepartment() != null ? ticket.getTicketDepartment().getId() : -1)
                .ticketDepartmentName(ticket.getTicketDepartment() != null ? ticket.getTicketDepartment().getTicketDepartmentName() : "")
                .rating(0L).build();
    }

    /**
     *
     * @param id
     * @return
     */
    public Optional<org.upsmf.grievance.model.es.Ticket> getESTicketByID(long id) {
        return esTicketRepository.findOneByTicketId(id);
    }

    /**
     *
     * @param id
     * @return
     */
    public Optional<Ticket> getTicketDetailsByID(long id) {
        return ticketRepository.findById(id);
    }

    private void validateTicketRequest(TicketRequest ticketRequest) throws Exception{
        if(ticketRequest==null){
            throw new IllegalArgumentException("Ticket request cannot be null");
        }
        if(StringUtils.isBlank(ticketRequest.getFirstName())){
            throw new IllegalArgumentException("First name is required");
        }

        if (StringUtils.isBlank(ticketRequest.getEmail())) {
            throw new IllegalArgumentException("Email is required");
        }
        if (!isValidPhoneNumber(ticketRequest.getPhone())) {
            throw new IllegalArgumentException("Invalid phone number");
        }

//        if (ticketRequest.getUserType() == null) {
//            throw new IllegalArgumentException("User type is required");
//        }

        if (ticketRequest.getTicketUserTypeId() == null) {
            throw new IllegalArgumentException("Ticket User type is required");
        }

        if (ticketRequest.getAttachmentUrls() != null) {
            for (String attachmentUrl : ticketRequest.getAttachmentUrls()) {
                if (StringUtils.isBlank(attachmentUrl)) {
                    throw new IllegalArgumentException("Invalid attachment URL");
                }
            }
        }
    }
    private boolean isValidPhoneNumber(String phoneNumber) {
        // Validate phone number format using a regular expression
        String phonePattern = "\\d{10}";
        return Pattern.matches(phonePattern, phoneNumber);
    }

    private boolean isValidStatus(TicketStatus status) {
        return status == TicketStatus.OPEN ||  status==TicketStatus.CLOSED || status==TicketStatus.INVALID;
    }

    private boolean isValidPriority(TicketPriority priority) {
        return priority == TicketPriority.LOW || priority == TicketPriority.MEDIUM || priority==TicketPriority.HIGH;
    }

    private void validateUpdateTicketRequest(UpdateTicketRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Update ticket request is null");
        }

        if (request.getStatus() == null || !isValidStatus(request.getStatus())) {
            throw new IllegalArgumentException("Invalid ticket status");
        }

        if (request.getPriority() == null || !isValidPriority(request.getPriority())) {
            throw new IllegalArgumentException("Invalid ticket priority");
        }
    }

    @Override
    @Transactional
    @Synchronized
    public void updateTicket(Long ticketId) {
        Ticket ticket = getTicketById(ticketId);
        try {
            ticket.setUpdatedDate(new Timestamp(DateUtil.getCurrentDate().getTime()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ticket.setEscalatedDate(new Timestamp(new Date().getTime()));
        ticket.setEscalatedToAdmin(true);
        ticket.setPriority(TicketPriority.MEDIUM);
        ticketRepository.save(ticket);
        ticket = getTicketById(ticket.getId());
        // check if ticket exists in ES
        Optional<org.upsmf.grievance.model.es.Ticket> esTicketDetails = esTicketRepository.findOneByTicketId(ticket.getId());
        org.upsmf.grievance.model.es.Ticket updatedESTicket = convertToESTicketObj(ticket);
        if(esTicketDetails.isPresent()) {
            // TODO revisit this
            esTicketRepository.deleteById(esTicketDetails.get().getId());
            updatedESTicket.setRating(esTicketDetails.get().getRating());
        }
        org.upsmf.grievance.model.es.Ticket curentUpdatedTicket=esTicketRepository.save(updatedESTicket);
    }
}
