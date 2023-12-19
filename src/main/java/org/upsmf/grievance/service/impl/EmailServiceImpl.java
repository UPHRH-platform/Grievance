package org.upsmf.grievance.service.impl;

// Importing required classes

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.javamail.MimeMessagePreparator;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.enums.Department;
import org.upsmf.grievance.exception.DataUnavailabilityException;
import org.upsmf.grievance.exception.InvalidDataException;
import org.upsmf.grievance.model.*;
import org.upsmf.grievance.repository.TicketDepartmentRepository;
import org.upsmf.grievance.repository.UserDepartmentRepository;
import org.upsmf.grievance.repository.UserRepository;
import org.upsmf.grievance.repository.UserRoleRepository;
import org.upsmf.grievance.service.EmailService;
import org.upsmf.grievance.service.TicketDepartmentService;
import org.upsmf.grievance.util.DateUtil;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// Annotation
@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    @Autowired
    private JavaMailSender javaMailSender;

    @Autowired
    private VelocityEngine velocityEngine;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDepartmentRepository userDepartmentRepository;

    @Value("${spring.mail.username}")
    private String sender;

    @Value("${site.url}")
    private String siteUrl;

    @Autowired
    private TicketDepartmentRepository ticketDepartmentRepository;

    @Override
    public void sendCreateTicketMail(EmailDetails details, Ticket ticket) {
//        getUserByDepartmentId(ticket.getTicketDepartment().getId());

        // sending mail activity in seperate thread
        Runnable mailThread = () -> {   // lambda expression
            sendMailToRaiser(details, ticket);
            sendMailToAdmin(details, ticket);
            sendMailToNodalOfficer(details, ticket);
        };
        new Thread(mailThread).start();
    }

    @Override
    public void sendUpdateTicketMail(EmailDetails details, Ticket ticket) {
        // Try block to check for exceptions
        Runnable mailThread = () -> {   // lambda expression
            sendUpdateMailToRaiser(details, ticket);
        };
        new Thread(mailThread).start();
    }

    @Override
    public void sendClosedTicketMail(EmailDetails details, Ticket ticket, String comment, List<AssigneeTicketAttachment> attachments, String feedbackURL) {
        // Try block to check for exceptions
        Runnable mailThread = () -> {   // lambda expression
            sendFeedbackMailToRaiser(details, ticket, comment, attachments, feedbackURL);
        };
        new Thread(mailThread).start();

    }
    @Override
    public void sendJunkMail(EmailDetails details, Ticket ticket, String comment, List<AssigneeTicketAttachment> attachments, String feedbackURL) {
        // Try block to check for exceptions
        Runnable mailThread = () -> {   // lambda expression
            sendJunkResponseToRaiser(details, ticket, comment, attachments, feedbackURL);
        };
        new Thread(mailThread).start();

    }
    private void sendFeedbackMailToRaiser(EmailDetails details, Ticket ticket,
                                         String comment, List<AssigneeTicketAttachment> attachments,
                                         String feedbackUrl) {
        try {
            MimeMessagePreparator preparator = new MimeMessagePreparator() {
                public void prepare(MimeMessage mimeMessage) throws Exception {
                    MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
                    message.setTo(details.getRecipient());
                    message.setSubject(details.getSubject());

                    String departmentName = "";
                    if (ticket.getTicketDepartment() != null) {
                        departmentName = ticket.getTicketDepartment().getTicketDepartmentName();
                    }

                    List<Department> departmentList = Department.getById(Integer.parseInt(ticket.getAssignedToId()));
                    VelocityContext velocityContext = new VelocityContext();
                    velocityContext.put("first_name", ticket.getFirstName());
                    velocityContext.put("id", ticket.getId());
                    velocityContext.put("created_date", DateUtil.getFormattedDateInString(ticket.getCreatedDate()));
                    velocityContext.put("department", departmentName);
                    velocityContext.put("comment", comment);
                    velocityContext.put("url", feedbackUrl);

                    // signature
                    createCommonMailSignature(velocityContext);
                    // merge mail body
                    StringWriter stringWriter = new StringWriter();
                    velocityEngine.mergeTemplate("templates/raiser_feedback.vm", "UTF-8", velocityContext, stringWriter);

                    message.setText(stringWriter.toString(), true);
                }
            };
            // Sending the mail
            javaMailSender.send(preparator);
            log.info("create ticket mail Sent Successfully...");
        }
        // Catch block to handle the exceptions
        catch (Exception e) {
            log.error("Error while Sending Mail", e);
        }
    }
    private void sendJunkResponseToRaiser(EmailDetails details, Ticket ticket,
                                          String comment, List<AssigneeTicketAttachment> attachments,
                                          String feedbackUrl) {
        try {
            MimeMessagePreparator preparator = new MimeMessagePreparator() {
                public void prepare(MimeMessage mimeMessage) throws Exception {
                    MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
                    message.setTo(details.getRecipient());
                    message.setSubject(details.getSubject());

                    String departmentName = "";
                    if (ticket.getTicketDepartment() != null) {
                        departmentName = ticket.getTicketDepartment().getTicketDepartmentName();
                    }

                    List<Department> departmentList = Department.getById(Integer.parseInt(ticket.getAssignedToId()));
                    VelocityContext velocityContext = new VelocityContext();
                    velocityContext.put("first_name", ticket.getFirstName());
                    velocityContext.put("id", ticket.getId());
                    velocityContext.put("created_date", DateUtil.getFormattedDateInString(ticket.getCreatedDate()));
                    velocityContext.put("department", departmentName);
                    velocityContext.put("comment", comment);
                    velocityContext.put("support_email_address","upmedicalfaculty@upsmfac.org");
                    velocityContext.put("support_phone_number","Phone: (0522) 2238846, 2235964, 2235965, 3302100");
                    velocityContext.put("url", feedbackUrl);
                    velocityContext.put("junk_by", ticket.isJunk());
                    velocityContext.put("Junk_by_reason", ticket.getJunkByReason());

                    // signature
                    createCommonMailSignature(velocityContext);
                    // merge mail body
                    StringWriter stringWriter = new StringWriter();
                    velocityEngine.mergeTemplate("templates/raiser_junk_ticket.vm", "UTF-8", velocityContext, stringWriter);

                    message.setText(stringWriter.toString(), true);
                }
            };
            // Sending the mail
            javaMailSender.send(preparator);
            log.info("create ticket mail Sent Successfully...");
        }
        // Catch block to handle the exceptions
        catch (Exception e) {
            log.error("Error while Sending Mail", e);
        }
    }

    private void sendUpdateMailToRaiser(EmailDetails details, Ticket ticket) {
        try {
            MimeMessagePreparator preparator = new MimeMessagePreparator() {
                public void prepare(MimeMessage mimeMessage) throws Exception {
                    MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
                    message.setTo(details.getRecipient());
                    message.setSubject(details.getSubject());

                    VelocityContext velocityContext = new VelocityContext();
                    velocityContext.put("first_name", ticket.getFirstName());
                    velocityContext.put("id", ticket.getId());
                    velocityContext.put("created_date", DateUtil.getFormattedDateInString(ticket.getCreatedDate()));
                    velocityContext.put("status", ticket.getStatus().name());
                    velocityContext.put("updated_date", DateUtil.getFormattedDateInString(ticket.getUpdatedDate()));
                    velocityContext.put("other_by", ticket.getOther());
                    velocityContext.put("other_by_reason", ticket.getOtherByReason());
                    // signature
                    createCommonMailSignature(velocityContext);
                    // merge mail body
                    StringWriter stringWriter = new StringWriter();
                    velocityEngine.mergeTemplate("templates/raiser_update_ticket.vm", "UTF-8", velocityContext, stringWriter);

                    message.setText(stringWriter.toString(), true);
                }
            };
            // Sending the mail
            javaMailSender.send(preparator);
            log.info("create ticket mail Sent Successfully...");
        }
        // Catch block to handle the exceptions
        catch (Exception e) {
            log.error("Error while Sending Mail", e);
        }
    }

    private void sendMailToNodalOfficer(EmailDetails details, Ticket ticket) {
        try {

            List<User> users = getUsersByDepartment(ticket.getAssignedToId());
            if(users == null || users.isEmpty()) {
                return;
            }
            users.stream().forEach(x -> {
                MimeMessagePreparator preparator = new MimeMessagePreparator() {
                    public void prepare(MimeMessage mimeMessage) throws Exception {
                        MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
                        message.setTo(x.getEmail());
                        message.setSubject(details.getSubject());

//                        List<Department> departmentList = Department.getById(Integer.parseInt(ticket.getAssignedToId()));

                        String departmentName = "";
                        Optional<UserDepartment> userDepartmentOptional = getUserDepartmentByAssignedTo(ticket.getAssignedToId());

                        if (userDepartmentOptional.isPresent()) {
                            departmentName = userDepartmentOptional.get().getDepartmentName();
                        }

                        VelocityContext velocityContext = new VelocityContext();
                        velocityContext.put("first_name", x.getFirstName());
                        velocityContext.put("id", ticket.getId());
                        velocityContext.put("created_date", DateUtil.getFormattedDateInString(ticket.getCreatedDate()));
                        velocityContext.put("priority", ticket.getPriority());
                        velocityContext.put("department", departmentName);
                        velocityContext.put("status", ticket.getStatus().name());
                        velocityContext.put("site_url", siteUrl);
                        // signature
                        createCommonMailSignature(velocityContext);
                        // merge mail body
                        StringWriter stringWriter = new StringWriter();
                        velocityEngine.mergeTemplate("templates/nodal_create_ticket.vm", "UTF-8", velocityContext, stringWriter);

                        message.setText(stringWriter.toString(), true);
                    }
                };
                // Sending the mail
                javaMailSender.send(preparator);
                log.info("create ticket mail Sent Successfully...");
            });
        }
        // Catch block to handle the exceptions
        catch (Exception e) {
            log.error("Error while Sending Mail", e);
        }
    }

    private Optional<UserDepartment> getUserDepartmentByAssignedTo(String assignedToId) {
        if (!StringUtils.isBlank(assignedToId)) {
            try {
                Long userId = Long.valueOf(assignedToId);

                Optional<User> userOptional = userRepository.findById(userId);
                if (!userOptional.isPresent()) {
                    log.error("Unable to find assigned to mapping with existing user set");
                    throw new DataUnavailabilityException("Unable to find assigned to mapping with existing user set");
                }

                Optional.ofNullable(userOptional.get().getUserDepartment());

            } catch (NumberFormatException e) {
                log.error("Error while parsing assinged to");
                throw new InvalidDataException("AssignedTo id only support number");
            } catch (Exception e) {
                log.error("Error while calculating AssignedTo");
                throw new DataUnavailabilityException("Unable to get AssignedTo value");
            }
        }

        return Optional.empty();
    }

    private List<User> getUsersByDepartment(String assignedToId) {
        if (!StringUtils.isBlank(assignedToId)) {
            if (assignedToId.equalsIgnoreCase("-1")) {
                Optional<User> userOptional = userRepository.findByUserDepartment(null);

                if (userOptional.isPresent()) {
                    log.error("Unable to find admin for -1 assignedTo");
                    throw new DataUnavailabilityException("Unable to find admin user");
                }
                log.info(">>>>>>>>>> assingedTo is {} and user {}", assignedToId, userOptional.get());
                return Collections.singletonList(userOptional.get());
            }

            try {
                Long userId = Long.valueOf(assignedToId);

                Optional<User> userOptional = userRepository.findById(userId);
                if (!userOptional.isPresent()) {
                    log.error("Unable to find assigned to mapping with existing user set");
                    throw new DataUnavailabilityException("Unable to find assigned to mapping with existing user set");
                }

                log.info(">>>>>>>>>> User details:  user id {}, email {} based on assignedTO {}",
                        userOptional.get().getId(), userOptional.get().getEmail(), assignedToId);

                return Collections.singletonList(userOptional.get());
            } catch (NumberFormatException e) {
                log.error("Error while parsing assinged to");
                throw new InvalidDataException("AssignedTo id only support number");
            } catch (Exception e) {
                log.error("Error while calculating AssignedTo");
                throw new DataUnavailabilityException("Unable to get AssignedTo value");
            }
        }
        return Collections.emptyList();
    }

    private List<User> getUsersByDepartment_old(String assignedToId) {
//        List<Department> departmentList = Department.getById(Integer.parseInt(assignedToId));
//        if(departmentList != null && !departmentList.isEmpty()) {
//            String departmentName = departmentList.get(0).getCode();
//            List<UserDepartment> userUserDepartments = userDepartmentRepository.findAllByDepartmentName(departmentName);
//            if(userUserDepartments != null && !userUserDepartments.isEmpty()){
//                List<Long> userIds = new ArrayList<>();
//                userUserDepartments.stream().forEach(x -> userIds.add(x.getUserId()));
//                if(!userIds.isEmpty()) {
//                    List<User> users = new ArrayList<>();
//                    userIds.stream().forEach(x -> {
//                        Optional<User> fetchedUser = userRepository.findById(x);
//                        if(fetchedUser.isPresent()) {
//                            users.add(fetchedUser.get());
//                        }
//                    });
//                    return users;
//                }
//            }
//        }
        return Collections.emptyList();
    }

    private void sendMailToAdmin(EmailDetails details, Ticket ticket) {
        try {
            List<User> users = getUsersByDepartment(String.valueOf(-1));
            if(users == null || users.isEmpty()) {
                return;
            }
            users.stream().forEach(x -> {
                 MimeMessagePreparator preparator = new MimeMessagePreparator() {
                    public void prepare(MimeMessage mimeMessage) throws Exception {
                        MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
                        message.setTo(x.getEmail());
                        message.setSubject(details.getSubject());
                        List<Department> departmentList = Department.getById(Integer.parseInt(ticket.getAssignedToId()));
                        VelocityContext velocityContext = new VelocityContext();
                        velocityContext.put("first_name", x.getFirstName());
                        velocityContext.put("id", ticket.getId());
                        velocityContext.put("created_date", DateUtil.getFormattedDateInString(ticket.getCreatedDate()));
                        velocityContext.put("priority", ticket.getPriority());
//                        velocityContext.put("userDepartment", departmentList != null && !departmentList.isEmpty() ? departmentList.get(0).getCode() : "Others");
                        velocityContext.put("status", ticket.getStatus().name());
                        // signature
                        createCommonMailSignature(velocityContext);
                        // merge mail body
                        StringWriter stringWriter = new StringWriter();
                        velocityEngine.mergeTemplate("templates/admin_create_ticket.vm", "UTF-8", velocityContext, stringWriter);

                        message.setText(stringWriter.toString(), true);
                    }
                };
                // Sending the mail
                javaMailSender.send(preparator);
                log.info("create ticket mail Sent Successfully...");
            });
        }
        // Catch block to handle the exceptions
        catch (Exception e) {
            log.error("Error while Sending Mail", e);
        }
    }

    private List<User> findGrivanceNodal() {
        Optional<UserDepartment> userDepartmentOptional = userDepartmentRepository
                .findByCouncilNameAndCouncilName("OTHER", "OTHER");

        if (userDepartmentOptional.isPresent()) {
            Optional<User> userOptional = userRepository.findByUserDepartment(userDepartmentOptional.get());

            if (userOptional.isPresent()) {
                return Collections.singletonList(userOptional.get());
            } else {
                log.error(">>>>>>>>>>>>>>>>>>>>> Unable to find Grivance Nodal");
            }
        } else {
            log.error(">>>>>>>>>>>>>>>>>>>>> Unable to find user department for OTHER concil and OTHER department");
        }

        return Collections.emptyList();
    }

    /** ununsed method
     * @param details
     * @param ticket
     */
    @Override
    public void sendMailToGrievanceNodal(EmailDetails details, Ticket ticket) {
        try {
            List<User> users = findGrivanceNodal();
            if(users == null || users.isEmpty()) {
                return;
            }
            users.stream().forEach(x -> {
                 MimeMessagePreparator preparator = new MimeMessagePreparator() {
                    public void prepare(MimeMessage mimeMessage) throws Exception {
                        MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
                        message.setTo(x.getEmail());
                        message.setSubject(details.getSubject());
                        List<Department> departmentList = Department.getById(Integer.parseInt(ticket.getAssignedToId()));
                        VelocityContext velocityContext = new VelocityContext();
                        velocityContext.put("first_name", x.getFirstName());
                        velocityContext.put("id", ticket.getId());
                        velocityContext.put("created_date", DateUtil.getFormattedDateInString(ticket.getCreatedDate()));
                        velocityContext.put("priority", ticket.getPriority());
                        velocityContext.put("updated_date", DateUtil.getFormattedDateInString(ticket.getUpdatedDate()));
//                        velocityContext.put("userDepartment", departmentList != null && !departmentList.isEmpty() ? departmentList.get(0).getCode() : "Others");
                        velocityContext.put("status", ticket.getStatus().name());
                        velocityContext.put("other_by", ticket.getOther());
                        velocityContext.put("other_by_reason", ticket.getOtherByReason());
                        // signature
                        createCommonMailSignature(velocityContext);
                        // merge mail body
                        StringWriter stringWriter = new StringWriter();
                        velocityEngine.mergeTemplate("templates/admin_create_ticket.vm", "UTF-8", velocityContext, stringWriter);

                        message.setText(stringWriter.toString(), true);
                    }
                };
                // Sending the mail
                javaMailSender.send(preparator);
                log.info("create ticket mail Sent Successfully...");
            });
        }
        // Catch block to handle the exceptions
        catch (Exception e) {
            log.error("Error while Sending Mail", e);
        }
    }

    private static void createCommonMailSignature(VelocityContext velocityContext) {
        velocityContext.put("signature_name", "U.P. State Medical Faculty");
        velocityContext.put("address", "Address: 5, Sarvpalli, Mall Avenue Road,  Lucknow - 226001 (U.P.) India");
        velocityContext.put("phone", "Phone: (0522) 2238846, 2235964, 2235965, 3302100");
        velocityContext.put("mobile", "Mobile : +91-8400955546 / +91-9151024463");
        velocityContext.put("fax", "Fax : (0522) 2236600");
        velocityContext.put("email", "Email:  upmedicalfaculty@upsmfac.org");
    }

    private void sendMailToRaiser(EmailDetails details, Ticket ticket) {
        try {
            MimeMessagePreparator preparator = new MimeMessagePreparator() {
                public void prepare(MimeMessage mimeMessage) throws Exception {
                    MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
                    message.setTo(details.getRecipient());
                    message.setSubject(details.getSubject());

                    VelocityContext velocityContext = new VelocityContext();
                    velocityContext.put("first_name", ticket.getFirstName());
                    velocityContext.put("id", ticket.getId());
                    velocityContext.put("created_date", DateUtil.getFormattedDateInString(ticket.getCreatedDate()));
                    // signature
                    createCommonMailSignature(velocityContext);
                    // merge mail body
                    StringWriter stringWriter = new StringWriter();
                    velocityEngine.mergeTemplate("templates/raiser-create-ticket.vm", "UTF-8", velocityContext, stringWriter);

                    message.setText(stringWriter.toString(), true);
                }
            };
            // Sending the mail
            javaMailSender.send(preparator);
            log.info("create ticket mail Sent Successfully...");
        }
        // Catch block to handle the exceptions
        catch (Exception e) {
            log.error("Error while Sending Mail", e);
        }
    }

    // Method 1
    // To send a simple email
    @Override
    public void sendSimpleMail(EmailDetails details)
    {
        // Try block to check for exceptions
        try {
            // Creating a simple mail message
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            // Setting up necessary details
            mailMessage.setFrom(sender);
            mailMessage.setTo(details.getRecipient());
            mailMessage.setText(details.getMsgBody());
            mailMessage.setSubject(details.getSubject());
            // Sending the mail
            javaMailSender.send(mailMessage);
            log.info("Mail Sent Successfully...");
        }
        // Catch block to handle the exceptions
        catch (Exception e) {
            log.error("Error while Sending Mail", e);
        }
    }

    // Method 2
    // To send an email with attachment
    @Override
    public void sendMailWithAttachment(EmailDetails details)
    {
        // Creating a mime message
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper mimeMessageHelper;
        try {
            // Setting multipart as true for attachments tobe send
            mimeMessageHelper = new MimeMessageHelper(mimeMessage, true);
            mimeMessageHelper.setFrom(sender);
            mimeMessageHelper.setTo(details.getRecipient());
            mimeMessageHelper.setText(details.getMsgBody());
            mimeMessageHelper.setSubject(details.getSubject());
            // Adding the attachment
            FileSystemResource file = new FileSystemResource(new File(details.getAttachment()));
            mimeMessageHelper.addAttachment(file.getFilename(), file);
            // Sending the mail
            javaMailSender.send(mimeMessage);
            log.info("Mail Sent Successfully...");
        }
        // Catch block to handle MessagingException
        catch (MessagingException e) {
            // Display message when exception occurred
            log.error("Error while Sending Mail with attachment", e);
        }
    }

    @Override
    public void sendMailToDGME(EmailDetails details, JsonNode assessmentMatrix) {
        try {
            MimeMessagePreparator preparator = new MimeMessagePreparator() {
                public void prepare(MimeMessage mimeMessage) throws Exception {
                    MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
                    message.setTo(details.getRecipient());
                    message.setSubject(details.getSubject());

                    VelocityContext velocityContext = new VelocityContext();
                    velocityContext.put("unassigned", assessmentMatrix.get("unassigned").numberValue());
                    velocityContext.put("open", assessmentMatrix.get("isOpen").numberValue());
                    velocityContext.put("closed", assessmentMatrix.get("isClosed").numberValue());
                    velocityContext.put("escalated", assessmentMatrix.get("isEscalated").numberValue());
                    velocityContext.put("junk", assessmentMatrix.get("isJunk").numberValue());
                    velocityContext.put("total", assessmentMatrix.get("total").numberValue());
                    // signature
                    createCommonMailSignature(velocityContext);
                    // merge mail body
                    StringWriter stringWriter = new StringWriter();
                    velocityEngine.mergeTemplate("templates/biweekly_report.vm", "UTF-8", velocityContext, stringWriter);

                    message.setText(stringWriter.toString(), true);
                }
            };
            // Sending the mail
            javaMailSender.send(preparator);
            log.info("create ticket mail Sent Successfully...");
        }
        // Catch block to handle the exceptions
        catch (Exception e) {
            log.error("Error while Sending Mail", e);
        }
    }

    @Override
    public void sendMailToNodalOfficers(EmailDetails details, Ticket ticket){
        log.info("Entering sendMailToNodalOfficers method");
        Runnable mailThread = () -> {
            log.info("Inside mailThread lambda");// lambda expression
            sendNudgeMailToNodalOfficer(details, ticket);
        };
        new Thread(mailThread, "MailThread").start();
    }

    private void sendNudgeMailToNodalOfficer(EmailDetails details, Ticket ticket) {
        try {

            List<User> users = getUsersByDepartment(ticket.getAssignedToId());
            if(users.isEmpty()) {
                return;
            }
            users.stream().forEach(user -> {
                MimeMessagePreparator preparator = new MimeMessagePreparator() {
                    public void prepare(MimeMessage mimeMessage) throws Exception {
                        MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
                        message.setTo(user.getEmail());
                        message.setSubject(details.getSubject());

                        String departmentName = "";
                        Optional<UserDepartment> userDepartmentOptional = getUserDepartmentByAssignedTo(ticket.getAssignedToId());

                        if (userDepartmentOptional.isPresent()) {
                            departmentName = userDepartmentOptional.get().getDepartmentName();
                        }

                        List<Department> departmentList = Department.getById(Integer.parseInt(ticket.getAssignedToId()));
                        VelocityContext velocityContext = new VelocityContext();
                        velocityContext.put("first_name", user.getFirstName());
                        velocityContext.put("id", ticket.getId());
                        velocityContext.put("created_date", DateUtil.getFormattedDateInString(ticket.getCreatedDate()));
                        velocityContext.put("priority", ticket.getPriority());
                        velocityContext.put("department", departmentName);
                        velocityContext.put("status", ticket.getStatus().name());
                        velocityContext.put("site_url", siteUrl);
                        // signature
                        createCommonMailSignature(velocityContext);
                        // merge mail body
                        StringWriter stringWriter = new StringWriter();
                        velocityEngine.mergeTemplate("templates/nodal_nudge_ticket.vm", "UTF-8", velocityContext, stringWriter);

                        message.setText(stringWriter.toString(), true);
                    }
                };
                // Sending the mail
                javaMailSender.send(preparator);
                log.info("create ticket mail Sent Successfully...");

            });
        }
        // Catch block to handle the exceptions
        catch (Exception e) {
            log.error("Error while Sending Mail", e);
        }
    }

    @Override
    public void sendMailToRaiserForEscalatedTicket(EmailDetails details, Ticket ticket) {
        // Try block to check for exceptions
        Runnable mailThread = () -> {   // lambda expression
            mailToRaiserForEscalatedTicket(details, ticket);
        };
        new Thread(mailThread).start();
    }

    private void mailToRaiserForEscalatedTicket(EmailDetails details, Ticket ticket) {
        try {
            MimeMessagePreparator preparator = new MimeMessagePreparator() {
                public void prepare(MimeMessage mimeMessage) throws Exception {
                    MimeMessageHelper message = new MimeMessageHelper(mimeMessage);
                    message.setTo(details.getRecipient());
                    message.setSubject(details.getSubject());

                    VelocityContext velocityContext = new VelocityContext();
                    velocityContext.put("first_name", ticket.getFirstName());
                    velocityContext.put("id", ticket.getId());
                    // signature
                    createCommonMailSignature(velocityContext);
                    // merge mail body
                    StringWriter stringWriter = new StringWriter();
                    velocityEngine.mergeTemplate("templates/raiser_escalation_ticket.vm", "UTF-8", velocityContext, stringWriter);

                    message.setText(stringWriter.toString(), true);
                }
            };
            // Sending the mail
            javaMailSender.send(preparator);
            log.info("create ticket mail Sent Successfully...");
        }
        // Catch block to handle the exceptions
        catch (Exception e) {
            log.error("Error while Sending Mail", e);
        }
    }
}
