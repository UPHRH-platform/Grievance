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
import org.upsmf.grievance.dto.TicketRequest;
import org.upsmf.grievance.dto.UpdateTicketRequest;
import org.upsmf.grievance.enums.TicketPriority;
import org.upsmf.grievance.enums.TicketStatus;
import org.upsmf.grievance.exception.DataUnavailabilityException;
import org.upsmf.grievance.exception.InvalidDataException;
import org.upsmf.grievance.model.*;
import org.upsmf.grievance.repository.*;
import org.upsmf.grievance.repository.es.TicketRepository;
import org.upsmf.grievance.service.EmailService;
import org.upsmf.grievance.service.OtpService;
import org.upsmf.grievance.service.TicketService;
import org.upsmf.grievance.util.DateUtil;

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

    @Autowired
    private EmailService emailService;

    @Autowired
    private ObjectMapper mapper;

    /**
     *
     * @param ticket
     * @return
     */
    @Transactional
    public Ticket saveWithAttachment(Ticket ticket, List<String> attachments) {
        // save ticket in postgres
        org.upsmf.grievance.model.Ticket psqlTicket = ticketRepository.save(ticket);
        // update attachments if present
        if(attachments != null) {
            for(String url : attachments) {
                RaiserTicketAttachment raiserTicketAttachment = RaiserTicketAttachment.builder()
                        .attachment_url(url)
                        .ticketId(ticket.getId())
                        .build();
                raiserTicketAttachmentRepository.save(raiserTicketAttachment);
            }
        }
        // covert to ES ticket object
        org.upsmf.grievance.model.es.Ticket esticket = convertToESTicketObj(ticket);
        // save ticket in ES
        esTicketRepository.save(esticket);
        return psqlTicket;
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

//        Todo: uncomment opt condition
        // validate OTP
//        boolean isValid = otpService.validateOtp(ticketRequest.getEmail(), ticketRequest.getOtp());
//        if(!isValid) {
//            throw new TicketException("Invalid mail OTP, Please enter correct OTP", ErrorCode.TKT_001,
//                    "Error while matching mail OTP");
//        } else {
//            boolean isMobileOtpValid = otpService.validateMobileOtp(ticketRequest.getPhone(),
//                    ticketRequest.getMobileOtp());
//
//            if (!isMobileOtpValid) {
//                throw new TicketException("Invalid mobile OTP, Please enter correct OTP", ErrorCode.TKT_001,
//                        "Error while matching mobile OTP");
//            }
//        }
        // set default value for creating ticket
        Ticket ticket = createTicketWithDefault(ticketRequest);
        // create ticket
        ticket = saveWithAttachment(ticket, ticketRequest.getAttachmentUrls());
        // send mail
        EmailDetails emailDetails = EmailDetails.builder().recipient(ticket.getEmail()).subject("New Complaint Registration").build();
        emailService.sendCreateTicketMail(emailDetails, ticket);
        System.out.println(ticket);
        return ticket;
    }

    /**
     *
     * @param ticketRequest
     * @return
     * @throws Exception
     */
    private Ticket createTicketWithDefault(TicketRequest ticketRequest) throws Exception {

        Timestamp currentTimestamp = new Timestamp(DateUtil.getCurrentDate().getTime());
        LocalDateTime escalationDateTime = LocalDateTime.now().plus(Long.valueOf(ticketEscalationDays), ChronoUnit.DAYS);

        Optional<TicketUserType> userTypeOptional = ticketUserTypeRepository
                .findById(ticketRequest.getTicketUserTypeId());

        if (!userTypeOptional.isPresent()) {
            throw new DataUnavailabilityException("Ticket User type does not exist");
        }

        Optional<TicketCouncil> ticketCouncilOptional = ticketCouncilRepository
                .findById(ticketRequest.getTicketCouncilId());

        if (!ticketCouncilOptional.isPresent()) {
            throw new DataUnavailabilityException("Ticket cuncil does not exist");
        }

        Optional<TicketDepartment> ticketDepartmentOptional = ticketDepartmentRepository
                .findById(ticketRequest.getTicketDepartmentId());

        if (!ticketDepartmentOptional.isPresent()) {
            throw new DataUnavailabilityException("Ticket department does not exist");
        }

        Long userId = getFirstActiveUserByDepartmentId(ticketRequest.getTicketDepartmentId(), ticketRequest.getTicketCouncilId());


        return Ticket.builder()
                .createdDate(new Timestamp(DateUtil.getCurrentDate().getTime()))
                .firstName(ticketRequest.getFirstName())
                .lastName(ticketRequest.getLastName())
                .phone(ticketRequest.getPhone())
                .email(ticketRequest.getEmail())
//                .requesterType(ticketRequest.getUserType()) //TODO: replace with user type
                .assignedToId(String.valueOf(userId))
                .description(ticketRequest.getDescription())
                .updatedDate(currentTimestamp)
                .lastUpdatedBy("-1")//need to get user details and add id or name
                .escalated(false)
                .escalatedDate(Timestamp.valueOf(escalationDateTime))
                .escalatedTo("-1")
                .status(TicketStatus.OPEN)
                .requestType(ticketRequest.getRequestType()) //TODO: userDepartment details
                .priority(TicketPriority.LOW)
                .escalatedBy("-1")
                .reminderCounter(0L)
                .ticketUserType(userTypeOptional.get())
                .ticketCouncil(ticketCouncilOptional.get())
                .ticketDepartment(ticketDepartmentOptional.get())
                .build();
    }

    private @NonNull Long getFirstActiveUserByDepartmentId(Long departmentId, Long councilId) {
        if ((departmentId != null && councilId == null) || (departmentId == null && councilId != null)) {
            log.error("Missing one of attrbutes department id or council id - both are allowed or none");
            throw new InvalidDataException("Both council and department id are allowed or none");
        }

        if (departmentId == null && councilId == null) {
            log.info(">>>>>>>>> Did not foound department or council id information - ticket will be unassigned");
            return -1L;
        }

        Optional<TicketDepartment> ticketDepartmentOptional = ticketDepartmentRepository
                .findByIdAndTicketCouncilId(departmentId, councilId);

        if (!ticketDepartmentOptional.isPresent()) {
            log.error("Unable to find user ticket department");
            throw new DataUnavailabilityException("Unable to find ticket department");
        }

        Optional<Long> adminIdLong = findGrievanceNodalAdmin(ticketDepartmentOptional.get());

        if (adminIdLong.isPresent()) {
            return adminIdLong.get();
        }

        List<UserDepartment> userDepartmentList = userDepartmentRepository
                .findAllByDepartmentIdAndCouncilId(departmentId, councilId);

        if (userDepartmentList == null || userDepartmentList.isEmpty()) {
            log.error("Unable to find user departments - department id {} | council id {}", departmentId, councilId);
            throw new DataUnavailabilityException("Unable to find user department details");
        }

        List<User> userList = userRepository.findAllByUserDepartmentIn(userDepartmentList);

        if (userList == null || userList.isEmpty()) {
            log.error("Unable to find user details");
            throw new DataUnavailabilityException("Unable to find user details");
        }

        Optional<Long> activeUser = userList.stream()
                .filter(user -> user.getStatus() == 1)
                .map(User::getId)
                .findFirst();

        if (!activeUser.isPresent()) {
            log.error("Unable to find any acivite user");
            throw new DataUnavailabilityException("There no active user for given council and department");
        }

        return activeUser.get();
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
        // set incoming values
        setUpdateTicket(updateTicketRequest, ticket);
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
        TicketStatus currentTicketStatus=curentUpdatedTicket.getStatus();
        curentUpdatedTicket.getEmail();
        curentUpdatedTicket.getTicketId();
        if(curentUpdatedTicket.getStatus().name().equalsIgnoreCase(TicketStatus.CLOSED.name())) {
            generateFeedbackLinkAndEmail(ticket);
            return ticket;
        } else if (curentUpdatedTicket.getStatus().name().equalsIgnoreCase(TicketStatus.INVALID.name())) {
            generateFeedbackLinkAndEmailForJunkTicket(ticket);
            return ticket;
        }else if (updateTicketRequest.getIsNudged() != null && updateTicketRequest.getIsNudged()
                && !org.apache.commons.lang.StringUtils.isEmpty(updateTicketRequest.getCc())) {
            sendMailToNodal(updateTicketRequest.getCc(), ticket);
            return ticket;
        } else {
            EmailDetails resolutionOfYourGrievance = EmailDetails.builder().subject("Resolution of Your Grievance - " + curentUpdatedTicket.getTicketId()).recipient(curentUpdatedTicket.getEmail()).build();
            emailService.sendUpdateTicketMail(resolutionOfYourGrievance, ticket);
            return ticket;
        }
    }

    private void sendMailToNodal(@NonNull String cc, Ticket ticket) {

        EmailDetails emailDetails = EmailDetails.builder()
                .subject(mailReminderSubject)
                .build();

        emailService.sendMailToNodalOfficers(emailDetails, ticket);
    }

    private void generateFeedbackLinkAndEmail(Ticket curentUpdatedTicket) {
        List<Comments> comments = commentRepository.findAllByTicketId(curentUpdatedTicket.getId());
        Comments latestComment =null;
        if(comments!=null && comments.size() > 0) {
            latestComment = comments.get(comments.size()-1);
        }
        String comment = latestComment!=null?latestComment.getComment():"";
        String link = feedbackBaseUrl.concat("?").concat("guestName=")
                .concat(curentUpdatedTicket.getFirstName().concat("%20").concat(curentUpdatedTicket.getLastName()))
                .concat("&ticketId=").concat(String.valueOf(curentUpdatedTicket.getId()))
                .concat("&resolutionComment=").concat(comment)
                .concat("&email=").concat(curentUpdatedTicket.getEmail())
                .concat("&phone=").concat(curentUpdatedTicket.getPhone())
                .concat("&ticketTitle=").concat(curentUpdatedTicket.getDescription());
        EmailDetails resolutionOfYourGrievance = EmailDetails.builder().subject("Resolution of Your Grievance").recipient(curentUpdatedTicket.getEmail()).build();
        emailService.sendClosedTicketMail(resolutionOfYourGrievance, curentUpdatedTicket, comment, Collections.EMPTY_LIST, link);
    }
    private void generateFeedbackLinkAndEmailForJunkTicket(Ticket curentUpdatedTicket) {
        List<Comments> comments = commentRepository.findAllByTicketId(curentUpdatedTicket.getId());
        Comments latestComment =null;
        if(comments!=null && comments.size() > 0) {
            latestComment = comments.get(comments.size()-1);
        }
        String comment = latestComment!=null?latestComment.getComment():"";
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
        if(updateTicketRequest.getStatus()!=null) {
            ticket.setStatus(updateTicketRequest.getStatus());
        }

        if (updateTicketRequest.getIsNudged() != null && updateTicketRequest.getIsNudged()){
            if (ticket.getReminderCounter() != null){
                ticket.setReminderCounter(ticket.getReminderCounter() + 1);
            } else {
                ticket.setReminderCounter(0L);
            }
        }

        if(updateTicketRequest.getCc()!=null && !updateTicketRequest.getCc().isBlank()) {

            ticket.setAssignedToId(updateTicketRequest.getCc());
        }
        if(updateTicketRequest.getPriority()!=null) {
            ticket.setPriority(updateTicketRequest.getPriority());
        }
        if(Boolean.TRUE.equals(updateTicketRequest.getIsJunk())) {
            ticket.setJunk(updateTicketRequest.getIsJunk());
            if (updateTicketRequest.getRequestedBy() == null || updateTicketRequest.getRequestedBy().isBlank()) {
                ticket.setJunkedBy("-1");
            } else {
                User user = userRepository.findByKeycloakId(updateTicketRequest.getRequestedBy()).orElseThrow();
                String firstName = user.getFirstName();
                String lastName = user.getLastname();
                String junkedBy = firstName + " " + lastName;
                ticket.setJunkedBy(String.valueOf(user.getId()));
            }
        }
        ticket.setUpdatedDate(new Timestamp(DateUtil.getCurrentDate().getTime()));
        // update assignee comments
        if(updateTicketRequest.getComment()!=null) {
            Comments comments = Comments.builder().comment(updateTicketRequest.getComment())
                    .userId(updateTicketRequest.getRequestedBy())
                    .ticketId(ticket.getId())
                    .build();
            commentRepository.save(comments);
        }
        // update assignee attachment url
        if(updateTicketRequest.getAssigneeAttachmentURLs() != null) {
            for (String url : updateTicketRequest.getAssigneeAttachmentURLs()) {
                AssigneeTicketAttachment assigneeTicketAttachment = AssigneeTicketAttachment.builder()
                        .userId(updateTicketRequest.getRequestedBy())
                        .ticketId(ticket.getId())
                        .attachment_url(url).build();
                assigneeTicketAttachmentRepository.save(assigneeTicketAttachment);
            }
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
        if (!org.apache.commons.lang.StringUtils.isBlank(ticket.getAssignedToId()) && ! "-1".equalsIgnoreCase(ticket.getAssignedToId())) {
            try {
                Optional<User> userOptional = userRepository.findById(Long.valueOf(ticket.getAssignedToId()));

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
        if (!org.apache.commons.lang.StringUtils.isBlank(ticket.getJunkedBy()) && ! "-1".equalsIgnoreCase(ticket.getJunkedBy())) {
            try {
                Optional<User> userOptional = userRepository.findById(Long.valueOf(ticket.getJunkedBy()));

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
//                .requesterType(ticket.getRequesterType()) //TODO: rkr: replace with user type
                .assignedToId(ticket.getAssignedToId())
                .assignedToName(assingedToName) // get user details based on ID
                .description(ticket.getDescription())
                .junk(ticket.isJunk())
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
