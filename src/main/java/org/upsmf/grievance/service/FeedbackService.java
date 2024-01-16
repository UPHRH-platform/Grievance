package org.upsmf.grievance.service;

import org.upsmf.grievance.dto.FeedbackDto;
import org.upsmf.grievance.model.reponse.FeedbackResponse;

public interface FeedbackService {

    public void saveFeedback(FeedbackDto feedbackDto);

    public FeedbackResponse getFeedbacks();

    public FeedbackResponse getFeedbackByTicketId(String ticketId);
}
