package org.upsmf.grievance.service;

import org.upsmf.grievance.dto.SearchRequest;
import org.upsmf.grievance.model.es.Feedback;

import java.util.List;
import java.util.Map;

public interface DashboardService {

    public Map<String, Object> dashboardReport(SearchRequest searchRequest);

    public List<Feedback> getFeedbackByTicketId(String ticketId);
}
