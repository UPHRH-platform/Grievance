package org.upsmf.grievance.service;

import org.upsmf.grievance.dto.SearchRequest;

import java.util.Map;

public interface DashboardService {

    public Map<String, Object> dashboardReport(SearchRequest searchRequest);
}
