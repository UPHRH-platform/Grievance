package org.upsmf.grievance.service.impl;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.dto.FeedbackDto;
import org.upsmf.grievance.model.es.Feedback;
import org.upsmf.grievance.model.es.Ticket;
import org.upsmf.grievance.model.reponse.FeedbackResponse;
import org.upsmf.grievance.repository.es.FeedbackRepository;
import org.upsmf.grievance.repository.es.TicketRepository;
import org.upsmf.grievance.service.FeedbackService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@Slf4j
public class FeedbackServiceImpl implements FeedbackService {

    @Value("${es.default.page.size}")
    private int defaultPageSize;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private TicketRepository esTicketRepository;

    @Override
    public void saveFeedback(FeedbackDto feedbackDto) {

        // validate request -- all fields are mandatory except comment
        validateFeedbackDto(feedbackDto);
        try{
            Feedback feedback = Feedback.builder()
                    .firstName(feedbackDto.getFirstName())
                    .lastName(feedbackDto.getLastName())
                    .email(feedbackDto.getEmail())
                    .phone(feedbackDto.getPhone())
                    .rating(feedbackDto.getRating())
                    .ticketId(String.valueOf(feedbackDto.getTicketId()))
                    .comment(feedbackDto.getComment()!=null?feedbackDto.getComment():"").build();

            log.info("Saving feedback: {}", feedback);
            feedbackRepository.save(feedback);
            // update same rating in ES ticket
            if(feedbackDto.getTicketId() != null && feedbackDto.getTicketId() > 0) {
                Optional<Ticket> esTicket = esTicketRepository.findOneByTicketId(feedbackDto.getTicketId());
                if (esTicket.isPresent()) {
                    Ticket ticket = esTicket.get();
                    ticket.setRating(Long.valueOf(feedbackDto.getRating()));
                    String comment = feedbackDto.getComment()!=null?feedbackDto.getComment():"";
                    ticket.setFeedbackComment(comment);
                    esTicketRepository.save(ticket);
                }
            } else {
                log.error("Unable to update rating in Ticket");
            }
            log.info("Feedback saved successfully");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public FeedbackResponse getFeedbacks() {
        Page<Feedback> page = feedbackRepository.findAll(Pageable.ofSize(defaultPageSize));
        return FeedbackResponse.builder().count(page.getNumberOfElements()).data(page.getContent()).build();
    }

    private void validateFeedbackDto(FeedbackDto feedbackDto) {
        if (feedbackDto.getRating() == null || feedbackDto.getRating() < 1 || feedbackDto.getRating() > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        if (!isValidPhoneNumber(feedbackDto.getPhone())) {
            throw new IllegalArgumentException("Invalid phone number");
        }

        if (!isValidEmail(feedbackDto.getEmail())) {
            throw new IllegalArgumentException("Invalid email format");
        }

        if (!isValidName(feedbackDto.getFirstName())) {
            throw new IllegalArgumentException("Invalid first name");
        }

        if (!isValidName(feedbackDto.getLastName())) {
            throw new IllegalArgumentException("Invalid last name");
        }
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        // Validate phone number format using a regular expression
        String phonePattern = "\\d{10}";
        return Pattern.matches(phonePattern, phoneNumber);
    }

    private boolean isValidEmail(String email) {
        // Validate email format using a regular expression
        String emailPattern = "^[A-Za-z0-9+_.-]+@(.+)$";
        return Pattern.matches(emailPattern, email);
    }

    private boolean isValidName(String name) {
        // Validate name format using a regular expression

        if (name == null || name.isBlank()){
            return false;
        } else {
            return true;
        }

//        String namePattern = "[A-Za-z]+";
//        return Pattern.matches(namePattern, name);
    }

    /**
     * Method to get feedbacks for provided Ticket ID
     * @param ticketId
     * @return
     */
    @Override
    public FeedbackResponse getFeedbackByTicketId(String ticketId) {
        FeedbackResponse feedbackResponse = null;
        List<Feedback> allByTicketId = feedbackRepository.findAllByTicketId(ticketId);
        if(allByTicketId != null && !allByTicketId.isEmpty()) {
            feedbackResponse = FeedbackResponse.builder()
                    .data(allByTicketId).count(allByTicketId.size()).build();
            return feedbackResponse;
        }
        feedbackResponse = FeedbackResponse.builder().count(0).data(Collections.emptyList()).build();
        return feedbackResponse;
    }
}
