package org.upsmf.grievance.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.upsmf.grievance.model.AssigneeTicketAttachment;
import org.upsmf.grievance.model.EmailDetails;
import org.upsmf.grievance.model.Ticket;
import org.upsmf.grievance.model.User;

import java.util.List;

public interface EmailService {

    // Method
    // To send a simple email
    void sendCreateTicketMail(EmailDetails details, Ticket ticket);

    void sendUpdateTicketMail(EmailDetails details, Ticket ticket);

    void sendClosedTicketMail(EmailDetails details, Ticket ticket, String comment, List<AssigneeTicketAttachment> attachments, String feedbackURL);
    void sendJunkMail(EmailDetails details, Ticket ticket, String comment, List<AssigneeTicketAttachment> attachments, String feedbackURL);
    void sendSimpleMail(EmailDetails details);

    // Method
    // To send an email with attachment
    void sendMailWithAttachment(EmailDetails details);

    void sendMailToDGME(EmailDetails details, JsonNode assessmentMatrix);

    public void sendMailToNodalOfficers(EmailDetails details, Ticket ticket);

    void sendMailToRaiserForEscalatedTicket(EmailDetails details, Ticket ticket);

    void sendMailToNodalForEscalatedTicket(EmailDetails details, Ticket ticket);

    void sendMailTicketAggregateMailToNodalOfficer(EmailDetails emailDetails, User user, List<org.upsmf.grievance.model.es.Ticket> openTicketsByID);

    void sendMailTicketAggregateMailToSecretary(EmailDetails emailDetails, User user, List<org.upsmf.grievance.model.es.Ticket> openTicketsByID);

    void sendMailToNodalOfficer(EmailDetails details, Ticket ticket);

    void sendMailToGrievanceNodal(EmailDetails details, Ticket ticket);

    void sendNudgeMailToGrievanceNodal(EmailDetails details, Ticket ticket);

    void sendUserActivationMail(User user, boolean active);

    void sendUserCreationMail(User user,String password);

    void sendUnjunkMail(EmailDetails details, Ticket ticket);

    void sendEscalationMailToGrievanceNodal(EmailDetails details, Ticket ticket);

    void sendCreateTicketOTPMail(String email, String otp, String recipientName, String subject, int otpExpirationMinutes);
}
