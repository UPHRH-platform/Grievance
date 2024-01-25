package org.upsmf.grievance.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.config.EsConfig;
import org.upsmf.grievance.dto.SearchDateRange;
import org.upsmf.grievance.dto.SearchRequest;
import org.upsmf.grievance.enums.TicketPriority;
import org.upsmf.grievance.enums.TicketStatus;
import org.upsmf.grievance.model.TicketStatistics;
import org.upsmf.grievance.model.es.Feedback;
import org.upsmf.grievance.model.es.Ticket;
import org.upsmf.grievance.service.DashboardService;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    @Autowired
    private EsConfig esConfig;

    /**
     * Request will contain filter and date range
     * filter will contain below params
     * ** council ID - ID of the selected council
     * ** Department ID (ID of the department of the council) - This value is valid if council ID is present
     * ** User ID - (ID of user assigned to above department) - This value cannot be valid if council or department ID are missing
     * @param searchRequest
     * @return
     */

    @Override
    public Map<String, Object> dashboardReport(SearchRequest searchRequest) {
        log.info("Creating dashboard for following request - {}", searchRequest);
        // validate payload and set default for required fields
        ValidatePayloadAndSetDefaults(searchRequest);
        log.info("updating dashboard request after validation - {}", searchRequest);
        Map<String, Object> dashboardData = new HashMap<>();
        // get keyPerformanceMatrixReport
        ObjectNode keyPerformanceMatrixReport = createKeyPerformanceMatrixReport(searchRequest.getFilter(), searchRequest.getDate());
        // get Ticket assignment matrix
        ObjectNode ticketAssignmentMatrixReport = createTicketAssignmentMatrixReport(searchRequest.getFilter(), searchRequest.getDate());
        // get Resolution Matrix Report
        Map<String, Map<String, ObjectNode>> resolutionMatrixReport = createResolutionMatrixReport(searchRequest.getFilter(), searchRequest.getDate());
        // create response
        dashboardData.put("assignmentMatrix", ticketAssignmentMatrixReport);
        dashboardData.put("performanceIndicators", keyPerformanceMatrixReport);
        dashboardData.put("resolutionMatrix", resolutionMatrixReport);
        return dashboardData;
    }

    /**
     * validate payload and set default for required fields
     * @param searchRequest
     */
    private static void ValidatePayloadAndSetDefaults(SearchRequest searchRequest) {
        // take currentDateTime in Long
        long startDate30DaysFromNow = LocalDateTime.now(ZoneId.of(ZoneId.SHORT_IDS.get("IST"))).minus(30, ChronoUnit.DAYS).toEpochSecond(ZoneOffset.of("+05:30"));
        long currentEndDate = LocalDateTime.now(ZoneId.of(ZoneId.SHORT_IDS.get("IST"))).toEpochSecond(ZoneOffset.of("+05:30"));
        // set variables
        Map<String,Object> filter = searchRequest.getFilter();
        SearchDateRange date = searchRequest.getDate();
        if(filter == null) {
            searchRequest.setFilter(new HashMap<>());
        }
        if(date == null) {
            SearchDateRange defaultDateRange = SearchDateRange.builder().from(startDate30DaysFromNow).to(currentEndDate).build();
            searchRequest.setDate(defaultDateRange);
        }
        if(date != null && (date.getFrom() == null || date.getFrom() <= 0)) {
            date.setFrom(startDate30DaysFromNow);
        }
        if(date != null && (date.getTo() == null || date.getTo() <= 0)) {
            date.setTo(currentEndDate);
        }
    }

    private List<Ticket> executeQuery(BoolQueryBuilder esQuery) {
        try {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                    .query(esQuery).size(10000);
            org.elasticsearch.action.search.SearchRequest searchRequest = new org.elasticsearch.action.search.SearchRequest("ticket");
            searchRequest.searchType(SearchType.QUERY_THEN_FETCH);
            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = esConfig.elasticsearchClient().search(searchRequest, RequestOptions.DEFAULT);
            return getTicketDocumentsFromHits(searchResponse.getHits());
        } catch (IOException e) {
            log.error("Error while searching ticket by ticket id list", e);
        }
        return null;
    }

    private long executeQueryForCount(BoolQueryBuilder esQuery) {
        try {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                    .query(esQuery);
            org.elasticsearch.action.search.SearchRequest searchRequest = new org.elasticsearch.action.search.SearchRequest("ticket");
            searchRequest.searchType(SearchType.QUERY_THEN_FETCH);
            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = esConfig.elasticsearchClient().search(searchRequest, RequestOptions.DEFAULT);
            if(searchResponse != null && searchResponse.getHits() != null && searchResponse.getHits().getTotalHits() != null) {
                return searchResponse.getHits().getTotalHits().value;
            }
            return 0;
        } catch (IOException e) {
            log.error("Error while searching ticket by ticket id list", e);
        }
        return 0;
    }

    private List<Ticket> getTicketDocumentsFromHits(SearchHits hits) {
        List<Ticket> documents = new ArrayList<>();
        for (SearchHit hit : hits) {
            Ticket esTicket = new Ticket();
            for (Map.Entry entry : hit.getSourceAsMap().entrySet()) {
                String key = (String) entry.getKey();
                mapEsTicketDtoToTicketDto(entry, key, esTicket);
            }
            if(hit.getId() != null && !hit.getId().isBlank()) {
                esTicket.setId(hit.getId());
            }
            documents.add(esTicket);
        }
        return documents;
    }

    private void mapEsTicketDtoToTicketDto(Map.Entry entry, String key, Ticket esTicket) {
        switch (key) {
            case "ticket_id":
                Long longValue = ((Number) entry.getValue()).longValue();
                esTicket.setTicketId(longValue);
                break;
            case "requester_first_name":
                esTicket.setFirstName((String) entry.getValue());
                break;
            case "requester_last_name":
                esTicket.setLastName((String) entry.getValue());
                break;
            case "requester_phone":
                esTicket.setPhone((String) entry.getValue());
                break;
            case "requester_email":
                esTicket.setEmail((String) entry.getValue());
                break;
//            case "requester_type":
//                for (RequesterType enumValue : RequesterType.values()) {
//                    if (enumValue.name().equals(entry.getValue().toString())) {
//                        esTicket.setRequesterType(enumValue);
//                        break;
//                    }
//                }
//                break;
            case "assigned_to_id":
                esTicket.setAssignedToId(String.valueOf(entry.getValue()));
                break;
            case "assigned_to_name":
                esTicket.setAssignedToName((String) entry.getValue());
                break;
            case "description":
                esTicket.setDescription((String) entry.getValue());
                break;
            case "is_junk":
                esTicket.setJunk((Boolean) entry.getValue());
                break;
            case "created_date":
                esTicket.setCreatedDate((String) entry.getValue());
                break;
            case "updated_date":
                esTicket.setUpdatedDate((String) entry.getValue());
                break;
            case "junked_by":
                esTicket.setJunkedBy((String) entry.getValue());
                break;
            case "created_date_ts":
                longValue = ((Number) entry.getValue()).longValue();
                esTicket.setCreatedDateTS(longValue);
                break;
            case "updated_date_ts":
                longValue = ((Number) entry.getValue()).longValue();
                esTicket.setUpdatedDateTS(longValue);
                break;
            case "last_updated_by":
                esTicket.setLastUpdatedBy(String.valueOf(entry.getValue()));
                break;
            case "is_escalated":
                esTicket.setEscalated((Boolean) entry.getValue());
                break;
            case "escalated_date":
                esTicket.setEscalatedDate((String) entry.getValue());
                break;
            case "escalated_date_ts":
                longValue = ((Number) entry.getValue()).longValue();
                esTicket.setEscalatedDateTS(longValue);
                break;
            case "escalated_to":
                esTicket.setEscalatedTo(String.valueOf(entry.getValue()));
                break;
            case "status":
                for (TicketStatus enumValue : TicketStatus.values()) {
                    if (enumValue.name().equals(entry.getValue().toString())) {
                        esTicket.setStatus(enumValue);
                        break;
                    }
                }
                break;
            case "request_type":
                esTicket.setRequestType((String) entry.getValue());
                break;
            case "priority":
                for (TicketPriority enumValue : TicketPriority.values()) {
                    if (enumValue.name().equals(entry.getValue().toString())) {
                        esTicket.setPriority(enumValue);
                        break;
                    }
                }
                break;
            case "escalated_by":
                esTicket.setEscalatedBy(String.valueOf(entry.getValue()));
                break;
            case "rating":
                longValue = ((Number) entry.getValue()).longValue();
                esTicket.setRating(longValue);
                break;
            case "is_escalated_to_admin":
                esTicket.setEscalatedToAdmin(((Boolean) entry.getValue()).booleanValue());
                break;
            case "ticket_user_type_id":
                esTicket.setTicketUserTypeId((longValue = ((Number) entry.getValue()).longValue()));
                break;
            case "ticket_user_type_name":
                esTicket.setTicketUserTypeName(String.valueOf(entry.getValue()));
                break;
            case "ticket_council_id":
                esTicket.setTicketCouncilId((longValue = ((Number) entry.getValue()).longValue()));
                break;
            case "ticket_council_name":
                esTicket.setTicketCouncilName(String.valueOf(entry.getValue()));
                break;
            case "ticket_department_id":
                esTicket.setTicketDepartmentId((longValue = ((Number) entry.getValue()).longValue()));
                break;
            case "ticket_department_name":
                esTicket.setTicketDepartmentName(String.valueOf(entry.getValue()));
                break;
            case "reminder_counter":
                esTicket.setReminderCounter((longValue = ((Number) entry.getValue()).longValue()));
                break;
            case "junked_by_name":
                esTicket.setJunkedByName(String.valueOf(entry.getValue()));
                break;
        }
    }

    /**
     * Key Performance Matrix
     * 1. Escalation Percentage - No of tickets escalated out of total tickets for the provided date range
     * 2. Nudge Ticket Percentage - No of tickets nudged out of total tickets for the provided date range
     * 3. Open Ticket > 21 - No of tickets open more than 21 out of total tickets for the provided date range
     * 4. Turn Around Time - Average closing time in days of a ticket for the provided date range
     *
     * @return
     */
    public ObjectNode createKeyPerformanceMatrixReport(Map<String,Object> filter, SearchDateRange date){
        // get total ticket count for provided filter and date range
        long totalTicketCount = getTotalTicketsCount(filter, date);
        // get total escalated ticket count
        long totalEscalatedTicketCount = getEscalatedTicketsCount(filter, date, true, null);
        // get total ticket nudged
        long totalNudgedTicketCount = getNudgedTicketsCount(filter, date);
        // get total ticket open greater than 21 days
        long totalOpenTicketCountGte21 = getOpenTicketCountGte21Count(filter, date);
        // get ticket turned around time in days
        long ticketsTurnAroundTimeInDays = getTicketsTurnAroundTimeInDays(filter, date);
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode keyPerformanceMatrixNodeData = objectMapper.createObjectNode();
        keyPerformanceMatrixNodeData.put("Total", totalTicketCount);
        keyPerformanceMatrixNodeData.put("Escalation Percentage", totalTicketCount > 0 ? BigDecimal.valueOf(totalEscalatedTicketCount).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totalTicketCount), 2, RoundingMode.HALF_UP).toString().concat("%"):"0%");
        keyPerformanceMatrixNodeData.put("Nudge Ticket Percentage", totalTicketCount > 0 ? BigDecimal.valueOf(totalNudgedTicketCount).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totalTicketCount), 2, RoundingMode.HALF_UP).toString().concat("%"):"0%");
        keyPerformanceMatrixNodeData.put("Turn Around Time", ticketsTurnAroundTimeInDays);
        return keyPerformanceMatrixNodeData;
    }

    /**
     * get all ticket count
     * @param filter
     * @param date
     * @return
     */
    private long getTotalTicketsCount(Map<String, Object> filter, SearchDateRange date) {
        BoolQueryBuilder esQuery = createESQuery(filter, date);
        return executeQueryForCount(esQuery);
    }

    /**
     * get escalated ticket count
     * @param filter
     * @param date
     * @param isEscalated
     * @return
     */
    private long getEscalatedTicketsCount(Map<String, Object> filter, SearchDateRange date, boolean isEscalated, Boolean isOpen) {
        BoolQueryBuilder esQuery = createESQuery(filter, date);
        if(isOpen != null && isOpen.booleanValue()) {
            esQuery.must(QueryBuilders.matchQuery("status", "OPEN"));
        }
        // adding condition to filter escalated ticket
        esQuery.must(QueryBuilders.matchQuery("is_escalated_to_admin", isEscalated));
        return executeQueryForCount(esQuery);
    }

    /**
     * get nudged ticket count
     * @param filter
     * @param date
     * @return
     */
    private long getNudgedTicketsCount(Map<String, Object> filter, SearchDateRange date) {
        BoolQueryBuilder esQuery = createESQuery(filter, date);
        // adding condition to filter nudged ticket
        esQuery.must(QueryBuilders.rangeQuery("reminder_counter").gt(0));
        return executeQueryForCount(esQuery);
    }

    /**
     *
     * @param filter
     * @param date
     * @return
     */
    private long getTicketsTurnAroundTimeInDays(Map<String, Object> filter, SearchDateRange date) {
        BoolQueryBuilder esQuery = createESQuery(filter, date);
        // adding condition to filter closed ticket
        esQuery.must(QueryBuilders.matchQuery("status", TicketStatus.CLOSED.name()));
        List<Ticket> closedTickets = executeQuery(esQuery);
        AtomicLong diffTime = new AtomicLong(0);
        // iterate through the tickets to get sum of all turn around time to get average
        closedTickets.stream().forEach(ticket -> {
            long updatedDateTS = ticket.getUpdatedDateTS();
            long createdDateTS = ticket.getCreatedDateTS();
            if(updatedDateTS >= 0 && createdDateTS >= 0){
                diffTime.addAndGet(updatedDateTS - createdDateTS);
            }
        });
        // covert sum to days
        long timeDiffInDays = diffTime.get() / 86400000;
        // return average
        if(closedTickets.size() > 0) {
            return timeDiffInDays / closedTickets.size();
        }
        return 0;
    }

    /**
     *
     * @param filter
     * @param date
     * @return
     */
    private long getOpenTicketCountGte21Count(Map<String, Object> filter, SearchDateRange date) {
        BoolQueryBuilder esQuery = createESQuery(filter, date);
        // adding condition to filter open ticket greater than 21 days
        esQuery.must(QueryBuilders.matchQuery("status", TicketStatus.OPEN.name()));
        esQuery.must(QueryBuilders.rangeQuery("updated_date_ts").lt(date.getFrom()));
        return executeQueryForCount(esQuery);
    }

    /**
     * get ticket count by ticket status
     * @param filter
     * @param date
     * @param ticketStatus
     * @return
     */
    private long getTicketCountByTicketStatus(Map<String, Object> filter, SearchDateRange date, TicketStatus ticketStatus) {
        BoolQueryBuilder esQuery = createESQuery(filter, date);
        // adding condition to filter open ticket greater than 21 days
        esQuery.must(QueryBuilders.matchQuery("status", ticketStatus.name()));
        log.debug("ES query - {}", esQuery);
        return executeQueryForCount(esQuery);
    }

    /**
     * Get ticket status count By Department and council
     * 1. Total
     * 2. Is Open
     * 3. Is Closed
     * 4. Is Junk
     * 5. Is Escalated
     * 6. Unassigned
     *
     * @param filter
     * @param date
     * @return
     */
    private Map<String, Map<String, ObjectNode>> createResolutionMatrixReport(Map<String, Object> filter, SearchDateRange date) {
        // get total ticket count for provided filter and date range
        List<Ticket> totalTicket = getTotalTickets(filter, date);
        if(totalTicket == null || totalTicket.isEmpty()) {
            return null;
        }
        log.debug("Total ticket found - {}", totalTicket.size());
        // create map
        Map<String, Map<String, ObjectNode>> councilDepartmentResolutionMap = new HashMap<>();
        // loop through each ticket
        totalTicket.stream().forEach( ticket -> {
            createResolutionMatrixData(date, ticket, councilDepartmentResolutionMap);
        });
        return councilDepartmentResolutionMap;
    }

    private void createResolutionMatrixData(SearchDateRange date, Ticket ticket, Map<String, Map<String, ObjectNode>> councilDepartmentResolutionMap) {
        Map<String, ObjectNode> departmentMap;
        if(ticket.getTicketCouncilName() == null || ticket.getTicketDepartmentName() == null
                || ticket.getTicketCouncilName().isBlank() || ticket.getTicketDepartmentName().isBlank()
                || ticket.getTicketCouncilId() == null || ticket.getTicketCouncilId() <= 0
                || ticket.getTicketDepartmentId() == null || ticket.getTicketDepartmentId() <= 0) {
            return;
        }
        if(councilDepartmentResolutionMap.containsKey(ticket.getTicketCouncilName())) {
            departmentMap = councilDepartmentResolutionMap.get(ticket.getTicketCouncilName());
            if(departmentMap == null) {
                departmentMap = new HashMap<>();
            }
        } else {
            departmentMap = new HashMap<>();
        }
        createCouncilDepartmentTicketMatrix(date, ticket, departmentMap, councilDepartmentResolutionMap);
    }

    private void createCouncilDepartmentTicketMatrix(SearchDateRange date, Ticket ticket,
             Map<String, ObjectNode> departmentMap, Map<String, Map<String, ObjectNode>> councilDepartmentResolutionMap) {
        if(departmentMap.containsKey(ticket.getTicketDepartmentName())){
            return;
        }
        // create new department map and assign value
        Map<String, Object> searchFilter = new HashMap<>();
        searchFilter.put("ticket_council_id", ticket.getTicketCouncilId());
        searchFilter.put("ticket_department_id", ticket.getTicketDepartmentId());
        // get statistics
        ObjectNode ticketAssignmentMatrixReport = createTicketAssignmentMatrixReport(searchFilter, date);
        // get value based on council and department and assign value
        departmentMap.put(ticket.getTicketDepartmentName(), ticketAssignmentMatrixReport);
        councilDepartmentResolutionMap.put(ticket.getTicketCouncilName(), departmentMap);
    }

    /**
     * get all ticket
     *
     * @param filter
     * @param date
     * @return
     */
    private List<Ticket> getTotalTickets(Map<String, Object> filter, SearchDateRange date) {
        BoolQueryBuilder esQuery = createESQuery(filter, date);
        return executeQuery(esQuery);
    }

    /**
     * Get ticket status count
     *  1. Total
     *  2. Is Open
     *  3. Is Closed
     *  4. Is Junk
     *  5. Is Escalated
     *  6. Unassigned
     * @param filter
     * @param date
     * @return
     */
    private ObjectNode createTicketAssignmentMatrixReport(Map<String, Object> filter, SearchDateRange date) {
        // get total ticket count for provided filter and date range
        long totalTicketCount = getTotalTicketsCount(filter, date);
        // get total escalated ticket count
        long totalEscalatedTicketCount = getEscalatedTicketsCount(filter, date, true, true);
        // get open ticket count
        long totalOpenTicketCount = getTicketCountByTicketStatus(filter, date, TicketStatus.OPEN);
        // get closed ticket count
        long totalClosedTicketCount = getTicketCountByTicketStatus(filter, date, TicketStatus.CLOSED);
        // get junked ticket count
        long totalJunkedTicketCount = getTicketCountByTicketStatus(filter, date, TicketStatus.INVALID);
        // get open and unassigned ticket count
        long totalOpenUnassignedTicketCount = getUnassignedTicketCountByTicketStatus(filter, date, TicketStatus.OPEN);
        // create response
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode ticketAssignmentMatrixNodeData = objectMapper.createObjectNode();
        ticketAssignmentMatrixNodeData.put("Total", totalTicketCount);
        ticketAssignmentMatrixNodeData.put("Pending", totalOpenTicketCount);
        ticketAssignmentMatrixNodeData.put("Closed", totalClosedTicketCount);
        ticketAssignmentMatrixNodeData.put("Junk", totalJunkedTicketCount);
        ticketAssignmentMatrixNodeData.put("Escalated", totalEscalatedTicketCount);
        ticketAssignmentMatrixNodeData.put("Unassigned", totalOpenUnassignedTicketCount);
        return ticketAssignmentMatrixNodeData;
    }

    /**
     * get ticket count by ticket status
     * @param filter
     * @param date
     * @param ticketStatus
     * @return
     */
    private long getUnassignedTicketCountByTicketStatus(Map<String, Object> filter, SearchDateRange date, TicketStatus ticketStatus) {
        BoolQueryBuilder esQuery = createESQuery(filter, date);
        // adding condition to filter open ticket greater than 21 days
        esQuery.must(QueryBuilders.matchQuery("status", ticketStatus.name()));
        // TODO get list of grievance nodal admin
        long nodalAdminId = -1;
        esQuery.must(QueryBuilders.matchQuery("assigned_to_id", nodalAdminId));
        return executeQueryForCount(esQuery);
    }

    private static BoolQueryBuilder createESQuery(Map<String, Object> filter, SearchDateRange date) {
        BoolQueryBuilder esQuery = QueryBuilders.boolQuery();
        // looping to add filter params in the main query
        filter.entrySet().stream().forEach(entry -> {
            if(entry.getValue() != null) {
                if(entry.getValue() instanceof Integer) {
                    if((Integer)entry.getValue() >= 0) {
                        esQuery.must(QueryBuilders.matchQuery(entry.getKey(), entry.getValue()));
                    }
                } else if(entry.getValue() instanceof Long) {
                    if((Long)entry.getValue() >= 0) {
                        esQuery.must(QueryBuilders.matchQuery(entry.getKey(), entry.getValue()));
                    }
                } else if(entry.getValue() instanceof String) {
                    if(!((String) entry.getValue()).isBlank()) {
                        esQuery.must(QueryBuilders.matchQuery(entry.getKey(), entry.getValue()));
                    }
                } else if(entry.getValue() instanceof Boolean) {
                    esQuery.must(QueryBuilders.matchQuery(entry.getKey(), ((Boolean) entry.getValue()).booleanValue()));
                } else {
                    esQuery.must(QueryBuilders.matchQuery(entry.getKey(), entry.getValue()));
                }
            }
        });
        // adding date range
        esQuery.must(QueryBuilders.rangeQuery("created_date_ts").gte(date.getFrom()).lte(date.getTo()));
        return esQuery;
    }

    public List<Feedback> getFeedbackByTicketId(String ticketId) {
        BoolQueryBuilder esQuery = createESQueryForFeedbackByTicketId(ticketId);
        return executeQueryForFeedback(esQuery);
    }

    private static BoolQueryBuilder createESQueryForFeedbackByTicketId(String ticketId) {
        BoolQueryBuilder esQuery = QueryBuilders.boolQuery();
        // looping to add filter params in the main query
        esQuery.must(QueryBuilders.matchQuery("ticket_id", ticketId));
        return esQuery;
    }

    private List<Feedback> executeQueryForFeedback(BoolQueryBuilder esQuery) {
        try {
            SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                    .query(esQuery).size(10000);
            org.elasticsearch.action.search.SearchRequest searchRequest = new org.elasticsearch.action.search.SearchRequest("feedback");
            searchRequest.searchType(SearchType.QUERY_THEN_FETCH);
            searchRequest.source(searchSourceBuilder);
            SearchResponse searchResponse = esConfig.elasticsearchClient().search(searchRequest, RequestOptions.DEFAULT);
            return getFeedbackDocumentsFromHits(searchResponse.getHits());
        } catch (IOException e) {
            log.error("Error while searching ticket by ticket id list", e);
        }
        return null;
    }

    private List<Feedback> getFeedbackDocumentsFromHits(SearchHits hits) {
        List<Feedback> documents = new ArrayList<>();
        for (SearchHit hit : hits) {
            Feedback feedback = new Feedback();
            for (Map.Entry entry : hit.getSourceAsMap().entrySet()) {
                String key = (String) entry.getKey();
                mapEsTicketDtoToFeedbackDto(entry, key, feedback);
            }
            if(hit.getId() != null && !hit.getId().isBlank()) {
                feedback.setId(hit.getId());
            }
            documents.add(feedback);
        }
        return documents;
    }

    private void mapEsTicketDtoToFeedbackDto(Map.Entry entry, String key, Feedback feedback) {
        switch (key) {
            case "ticket_id":
                feedback.setTicketId((String) entry.getValue());
                break;
            case "first_name":
                feedback.setFirstName((String) entry.getValue());
                break;
            case "last_name":
                feedback.setLastName((String) entry.getValue());
                break;
            case "phone":
                feedback.setPhone((String) entry.getValue());
                break;
            case "email":
                feedback.setEmail((String) entry.getValue());
                break;
            case "rating":
                Integer intValue = ((Number) entry.getValue()).intValue();
                feedback.setRating(intValue);
                break;
            case "comment":
                feedback.setComment((String) entry.getValue());
                break;
            default:
                break;
        }
    }

    /**
     * Method to give ticket statistical data for logged in user
     * @param userData
     * @return
     */
    @Override
    public TicketStatistics getTicketStatisticsByUser(JsonNode userData) {
        Long userId = null;
        log.info("Json node data - {}", userData);
        if(userData != null && userData.has("userId")) {
            userId = userData.get("userId").asLong();
        }
        log.info("assigned userID - {}", userId);
        // validate payload
        Long escalatedToMeCount = null;
        Long nudgedTicketCount = null;
        Long notAssignedTicketCount = null;
        Long priorityTicketCount = null;
        if(userId == null || userId <= 0) {
            log.info("creating response for Administrative role");
            // create Filter Payload for Escalated to me
            escalatedToMeCount = createAndExecuteFilterPayload(null, "OPEN", false, "MEDIUM");
            // create filter payload for Nudged
            nudgedTicketCount = createAndExecuteFilterPayload(null, "OPEN", false, "HIGH");
            // create filter payload for Not Assigned
            notAssignedTicketCount = createAndExecuteFilterPayload(-1l, "OPEN", false, "LOW");
        }
        if(userId > 0) {
            // create filter for priority tab
            priorityTicketCount = createAndExecuteFilterPayload(userId, "OPEN", false, "HIGH");
        }
        // create filter payload for pending
        Long pendingTicketCount = createAndExecuteFilterPayload(userId, "OPEN", false, "LOW");
        // create filter payload for resolved
        Long resolvedTicketCount = createAndExecuteFilterPayload(userId, "CLOSED", false, null);
        // create filter payload for junk
        Long junkTicketCount = createAndExecuteFilterPayload(userId, "INVALID", true, null);
        // create response
        TicketStatistics ticketStatistics = TicketStatistics.builder().nudgedTicketCount(nudgedTicketCount)
                .pendingTicketCount(pendingTicketCount)
                .notAssignedTicketCount(notAssignedTicketCount)
                .resolvedTicketCount(resolvedTicketCount)
                .escalatedToMeCount(escalatedToMeCount)
                .junkTicketCount(junkTicketCount)
                .priorityTicketCount(priorityTicketCount)
                .build();
        log.info("statistic data - {}", ticketStatistics);
        return ticketStatistics;
    }

    private Long createAndExecuteFilterPayload(Long userId, String status, boolean isJunk, String ticketPriority) {
        BoolQueryBuilder esQueryForTicketCount = createESQueryForTicketCount(userId, status, isJunk, ticketPriority);
        return executeQueryForCount(esQueryForTicketCount);
    }

    private BoolQueryBuilder createESQueryForTicketCount(Long userId, String status, boolean isJunk, String ticketPriority) {
        BoolQueryBuilder esQuery = QueryBuilders.boolQuery();
        // looping to add filter params in the main query
        if(userId != null) {
            if(userId == 0) {
                userId = -1l;
            }
            esQuery.must(QueryBuilders.matchQuery("assigned_to_id", userId));
        }
        esQuery.must(QueryBuilders.matchQuery("status", status));
        esQuery.must(QueryBuilders.matchQuery("priority", ticketPriority));
        esQuery.must(QueryBuilders.matchQuery("is_junk", isJunk));
        log.info("ticket escalated - ES query - {}", esQuery);
        return esQuery;
    }
}
