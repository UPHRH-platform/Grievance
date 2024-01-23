package org.upsmf.grievance.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.config.EsConfig;
import org.upsmf.grievance.constants.Constants;
import org.upsmf.grievance.dto.SearchDateRange;
import org.upsmf.grievance.dto.SearchRequest;
import org.upsmf.grievance.enums.TicketPriority;
import org.upsmf.grievance.enums.TicketStatus;
import org.upsmf.grievance.model.EmailDetails;
import org.upsmf.grievance.exception.InvalidDataException;
import org.upsmf.grievance.model.es.Ticket;
import org.upsmf.grievance.model.reponse.TicketResponse;
import org.upsmf.grievance.repository.es.TicketRepository;
import org.upsmf.grievance.service.EmailService;
import org.upsmf.grievance.service.SearchService;
import org.upsmf.grievance.service.TicketService;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
public class SearchServiceImpl implements SearchService {

    @Autowired
    private EmailService emailService;

    @Value("${ticket.escalation.mail.subject.for.raiser}")
    private String ticketEscalationMailSubjectForRaiser;

    @Value("${mail.reminder.subject}")
    private String mailReminderSubject;

    @Autowired
    private TicketRepository esTicketRepository;
    @Autowired
    private EsConfig esConfig;

    @Autowired
    private TicketService ticketService;

    @Override
    public TicketResponse search(SearchRequest searchRequest) {
        //Calculate
        String keyValue = searchRequest.getSort().keySet().iterator().next();
        Pageable pageable = PageRequest.of(searchRequest.getPage(), searchRequest.getSize(), Sort.Direction.valueOf(searchRequest.getSort().get(keyValue).toUpperCase()), keyValue);
        Page<Ticket> page = esTicketRepository.findAll(pageable);
        return TicketResponse.builder().count(page.getTotalElements()).data(page.getContent()).build();
    }

    /**
     * * mail to ticket owner
     * * mail to grievance raiser
     * @param lastUpdatedEpoch
     * @return
     */
    @Override
    public long escalateTickets(Long lastUpdatedEpoch) {
        BoolQueryBuilder finalQuery = createTicketEscalationQuery(lastUpdatedEpoch);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(finalQuery);

        SearchResponse searchResponse = getSearchResponseFromES(searchSourceBuilder);
        if(searchResponse != null) {
            TotalHits totalHits = searchResponse.getHits().getTotalHits();
            if(totalHits != null && totalHits.value > 0) {
                searchSourceBuilder = new SearchSourceBuilder()
                        .query(finalQuery).size(Integer.parseInt(String.valueOf(totalHits.value)));
                searchResponse = getSearchResponseFromES(searchSourceBuilder);
                if(searchResponse != null && searchResponse.getHits()!= null
                        && searchResponse.getHits().getTotalHits() != null
                        && searchResponse.getHits().getTotalHits().value > 0) {
                    escalatePendingTickets(searchResponse);
                }
            }
        }
        return searchResponse.getHits().getTotalHits().value;
    }

    @Override
    public List<Ticket> getOpenTicketsByID(Long id) {
        QueryBuilder ticketIdQueryBuilder = QueryBuilders.matchQuery("assigned_to_id", id);
        QueryBuilder statusQueryBuilder = QueryBuilders.matchQuery("status", TicketStatus.OPEN.name());
        BoolQueryBuilder ticketBooleanQueryBuilder = QueryBuilders.boolQuery().must(ticketIdQueryBuilder).must(statusQueryBuilder);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(ticketBooleanQueryBuilder);

        org.elasticsearch.action.search.SearchRequest search =
                new org.elasticsearch.action.search.SearchRequest("ticket");

        search.searchType(SearchType.QUERY_THEN_FETCH);
        search.source(searchSourceBuilder);

        log.debug(">>>>>>>>>>>> Ticket query for id list - {}", searchSourceBuilder);

        try {
            SearchResponse searchResponse = esConfig.elasticsearchClient().search(search, RequestOptions.DEFAULT);

            return getTicketDocumentsFromHits(searchResponse.getHits());
        } catch (IOException e) {
            log.error("Error while searching ticket by ticket id list", e);
        }

        return Collections.emptyList();
    }

    @Override
    public List<Ticket> getOpenTicketsByID(Long id, SearchDateRange dateRange) {
        BoolQueryBuilder ticketBooleanQueryBuilder = QueryBuilders.boolQuery();
        if(id != null && id > 0) {
            QueryBuilder ticketIdQueryBuilder = QueryBuilders.matchQuery("assigned_to_id", id);
            ticketBooleanQueryBuilder.must(ticketIdQueryBuilder);
        }
        if(dateRange != null && dateRange.getFrom() != null && dateRange.getFrom() > 0) {
            QueryBuilder rangeBuilder = QueryBuilders.rangeQuery("updated_date_ts").gt(dateRange.getFrom());
            if(dateRange.getTo() != null && dateRange.getTo() > 0){
                rangeBuilder = QueryBuilders.rangeQuery("updated_date_ts").gt(dateRange.getFrom()).lte(dateRange.getTo());
            }
            ticketBooleanQueryBuilder.must(rangeBuilder);
        }
        QueryBuilder statusQueryBuilder = QueryBuilders.matchQuery("status", TicketStatus.OPEN.name());
        ticketBooleanQueryBuilder.must(statusQueryBuilder);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(ticketBooleanQueryBuilder);

        org.elasticsearch.action.search.SearchRequest search =
                new org.elasticsearch.action.search.SearchRequest("ticket");

        search.searchType(SearchType.QUERY_THEN_FETCH);
        search.source(searchSourceBuilder);

        log.info(">>>>>>>>>>>> Ticket query for id list - {}", searchSourceBuilder);

        try {
            SearchResponse searchResponse = esConfig.elasticsearchClient().search(search, RequestOptions.DEFAULT);

            return getTicketDocumentsFromHits(searchResponse.getHits());
        } catch (IOException e) {
            log.error("Error while searching ticket by ticket id list", e);
        }

        return Collections.emptyList();
    }

    @Override
    public List<Ticket> getEscalatedTicketsByID(Long id, SearchDateRange dateRange) {
        BoolQueryBuilder ticketBooleanQueryBuilder = QueryBuilders.boolQuery();
        if(id != null && id > 0) {
            QueryBuilder ticketIdQueryBuilder = QueryBuilders.matchQuery("assigned_to_id", id);
            ticketBooleanQueryBuilder.must(ticketIdQueryBuilder);
        }
        if(dateRange != null && dateRange.getFrom() != null && dateRange.getFrom() > 0) {
            QueryBuilder rangeBuilder = QueryBuilders.rangeQuery("updated_date_ts").gt(dateRange.getFrom());
            if(dateRange.getTo() != null && dateRange.getTo() > 0){
                rangeBuilder = QueryBuilders.rangeQuery("updated_date_ts").gt(dateRange.getFrom()).lte(dateRange.getTo());
            }
            ticketBooleanQueryBuilder.must(rangeBuilder);
        }
        QueryBuilder priorityQueryBuilder = QueryBuilders.matchQuery("priority", "MEDIUM");
        ticketBooleanQueryBuilder.must(priorityQueryBuilder);
        QueryBuilder statusQueryBuilder = QueryBuilders.matchQuery("status", TicketStatus.OPEN.name());
        ticketBooleanQueryBuilder.must(statusQueryBuilder);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(ticketBooleanQueryBuilder);

        org.elasticsearch.action.search.SearchRequest search =
                new org.elasticsearch.action.search.SearchRequest("ticket");

        search.searchType(SearchType.QUERY_THEN_FETCH);
        search.source(searchSourceBuilder);

        log.info(">>>>>>>>>>>> Ticket query for id list - {}", searchSourceBuilder);

        try {
            SearchResponse searchResponse = esConfig.elasticsearchClient().search(search, RequestOptions.DEFAULT);

            return getTicketDocumentsFromHits(searchResponse.getHits());
        } catch (IOException e) {
            log.error("Error while searching ticket by ticket id list", e);
        }
        return Collections.emptyList();
    }

    private void escalatePendingTickets(SearchResponse searchResponse) {
        Iterator<SearchHit> hits = searchResponse.getHits().iterator();
        TaskExecutor taskExecutor = new ConcurrentTaskScheduler();
        while(hits.hasNext()) {
            Map<String, Object> searchHit = hits.next().getSourceAsMap();
            taskExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    ticketService.updateTicket(Long.parseLong(searchHit.get("ticket_id").toString()));
                    // send mail to raiser
                    notifyRaiser(Long.parseLong(searchHit.get("ticket_id").toString()));
                }
            });
        }
    }

    private void notifyRaiser(Long ticketId) {
        org.upsmf.grievance.model.Ticket ticket = ticketService.getTicketById(ticketId);
        EmailDetails resolutionOfYourGrievance = EmailDetails.builder().subject(ticketEscalationMailSubjectForRaiser.concat(" - ").concat(String.valueOf(ticket.getId()))).recipient(ticket.getEmail()).build();
        // send mail to raiser
        emailService.sendMailToRaiserForEscalatedTicket(resolutionOfYourGrievance, ticket);
        if(ticket.getAssignedToId().equalsIgnoreCase("-1")) {
            String subject = "Escalation of Unassigned Ticket - Ticket ID:".concat(String.valueOf(ticket.getId()));
            EmailDetails escalationNodalSubject = EmailDetails.builder().subject(subject).recipient(ticket.getEmail()).build();
            // send mail to ticket owner
            emailService.sendEscalationMailToGrievanceNodal(escalationNodalSubject, ticket);
        } else {
            String subjectNodalOfficer = "Escalation of Assigned Ticket - Ticket ID:".concat(String.valueOf(ticket.getId()));
            EmailDetails escalationNodalSubject = EmailDetails.builder().subject(subjectNodalOfficer).recipient(ticket.getEmail()).build();
            // send mail to ticket owner
            emailService.sendMailToNodalForEscalatedTicket(escalationNodalSubject, ticket);
        }
    }

    private SearchResponse getSearchResponseFromES(SearchSourceBuilder searchSourceBuilder) {
        SearchResponse searchResponse;
        org.elasticsearch.action.search.SearchRequest search = new org.elasticsearch.action.search.SearchRequest("ticket");
        search.searchType(SearchType.QUERY_THEN_FETCH);
        search.source(searchSourceBuilder);
        try {
            searchResponse = esConfig.elasticsearchClient().search(search, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return searchResponse;
    }

    @Override
    public Map<String, Object> searchTickets(SearchRequest searchRequest) {
        //Create query for search by keyword
        SearchResponse searchResponse = null;
        searchResponse = getSearchResponse(searchRequest);
        Map<String, Object> response = new HashMap<>();
        List<Object> results = getDocumentsFromSearchResult(searchResponse);
        response.put("count", searchResponse.getHits().getTotalHits().value);
        response.put("results", results);
        return response;
    }

    private SearchResponse getSearchResponse(SearchRequest searchRequest) {
        SearchResponse searchResponse;
        String keyValue = searchRequest.getSort().keySet().iterator().next();
        keyValue = getKeyValue(keyValue);
        int from = searchRequest.getPage() > 0 ? (searchRequest.getPage() * searchRequest.getSize()) : 0;
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(createTicketSearchQuery(searchRequest))
                .from(from)
                .size(searchRequest.getSize())
                .sort(keyValue, SortOrder.valueOf(searchRequest.getSort().get(searchRequest.getSort().keySet().iterator().next()).toUpperCase()));

        org.elasticsearch.action.search.SearchRequest search = new org.elasticsearch.action.search.SearchRequest("ticket");
        search.searchType(SearchType.QUERY_THEN_FETCH);
        search.source(searchSourceBuilder);
        log.info("query string - {}", searchSourceBuilder);
        try {
            searchResponse = esConfig.elasticsearchClient().search(search, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return searchResponse;
    }

    private String getKeyValue(String keyValue) {
        switch (keyValue) {
            case "ticketId":
                keyValue = "ticket_id";
                break;
            case "firstName":
                keyValue = "requester_first_name";
                break;
            case "lastName":
                keyValue = "requester_last_name";
                break;
            case "phone":
                keyValue = "requester_phone";
                break;
            case "email":
                keyValue = "requester_email";
            case "ownerEmail":
                keyValue = "owner_email";
                break;
            case "requesterType":
                keyValue = "requester_type";
                break;
            case "assignedToId":
                keyValue = "assigned_to_id";
                break;
            case "assignedToName":
                keyValue = "assigned_to_name";
                break;
            case "description":
                keyValue = "description";
                break;
            case "junk":
                keyValue = "is_junk";
                break;
            case "createdDate":
                keyValue = "created_date";
                break;
            case "updatedDate":
                keyValue = "updated_date";
                break;
            case "createdDateTS":
                keyValue = "created_date_ts";
                break;
            case "updatedDateTS":
                keyValue = "updated_date_ts";
                break;
            case "lastUpdatedBy":
                keyValue = "last_updated_by";
                break;
            case "escalated":
                keyValue = "is_escalated";
                break;
            case "escalatedDate":
                keyValue = "escalated_date";
                break;
            case "escalatedDateTS":
                keyValue = "escalated_date_ts";
                break;
            case "escalatedTo":
                keyValue = "escalated_to";
                break;
            case "status":
                keyValue = "status";
                break;
            case "requestType":
                keyValue = "request_type";
                break;
            case "priority":
                keyValue = "priority";
                break;
            case "escalatedBy":
                keyValue = "escalated_by";
                break;
        }
        return keyValue;
    }

    private List<Object> getDocumentsFromSearchResult(SearchResponse result) {
        SearchHits hits = result.getHits();
        return getDocumentsFromHits(hits);
    }

    private List<Object> getDocumentsFromHits(SearchHits hits) {
        List<Object> documents = new ArrayList<Object>();
        for (SearchHit hit : hits) {
            Ticket esTicket = new Ticket();
            for (Map.Entry entry : hit.getSourceAsMap().entrySet()) {
                String key = (String) entry.getKey();
                mapEsTicketDtoToTicketDto(entry, key, esTicket);
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
            default:
                break;
        }
    }

    private BoolQueryBuilder createTicketSearchQuery(SearchRequest searchRequest) {
        BoolQueryBuilder finalQuery = QueryBuilders.boolQuery();
        // search by keyword
        if (searchRequest.getSearchKeyword() != null && !searchRequest.getSearchKeyword().isBlank()) {
            RegexpQueryBuilder firstNameKeywordMatchQuery = QueryBuilders.regexpQuery("requester_first_name", ".*" + searchRequest.getSearchKeyword().toLowerCase() + ".*");
            RegexpQueryBuilder lastNameKeywordMatchQuery = QueryBuilders.regexpQuery("requester_last_name", ".*" + searchRequest.getSearchKeyword().toLowerCase() + ".*");
            RegexpQueryBuilder escalatedDateKeywordMatchQuery = QueryBuilders.regexpQuery("escalated_date", ".*" + searchRequest.getSearchKeyword().toLowerCase() + ".*");
            RegexpQueryBuilder ticketUserTypeNameKeywordMatchQuery = QueryBuilders.regexpQuery("ticket_user_type_name", ".*"+searchRequest.getSearchKeyword().toLowerCase()+".*");
            RegexpQueryBuilder ticketCouncilNameKeywordMatchQuery = QueryBuilders.regexpQuery("ticket_council_name", ".*"+searchRequest.getSearchKeyword().toLowerCase()+".*");
            RegexpQueryBuilder ticketDepartmentNameKeywordMatchQuery = QueryBuilders.regexpQuery("ticket_department_name", ".*"+searchRequest.getSearchKeyword().toLowerCase()+".*");
            RegexpQueryBuilder junkedByKeywordMatchQuery = QueryBuilders.regexpQuery("junked_by_name", ".*"+searchRequest.getSearchKeyword().toLowerCase()+".*");
            RegexpQueryBuilder createdDateKeywordMatchQuery = QueryBuilders.regexpQuery("created_date", ".*" + searchRequest.getSearchKeyword().toLowerCase() + ".*");
            RegexpQueryBuilder assignedToNameKeywordMatchQuery = QueryBuilders.regexpQuery("assigned_to_name", ".*" + searchRequest.getSearchKeyword().toLowerCase() + ".*");

            BoolQueryBuilder keywordSearchQuery = QueryBuilders.boolQuery();
            keywordSearchQuery.should(lastNameKeywordMatchQuery)
                    .should(escalatedDateKeywordMatchQuery)
                    .should(ticketUserTypeNameKeywordMatchQuery)
                    .should(ticketCouncilNameKeywordMatchQuery)
                    .should(ticketDepartmentNameKeywordMatchQuery)
                    .should(createdDateKeywordMatchQuery)
                    .should(assignedToNameKeywordMatchQuery);
            if(searchRequest.getIsJunk().booleanValue()) {
                keywordSearchQuery.should(junkedByKeywordMatchQuery);
            }
            try {
                Integer intValue = Integer.parseInt(searchRequest.getSearchKeyword());
                MatchQueryBuilder ticketIdKeywordMatchQuery = QueryBuilders.matchQuery("ticket_id",  intValue);
                keywordSearchQuery.should(ticketIdKeywordMatchQuery);
            } catch (NumberFormatException e) {
                log.error("search string is not INTEGER - {} ", searchRequest.getSearchKeyword());
            }

            keywordSearchQuery.should(firstNameKeywordMatchQuery);
            finalQuery.must(keywordSearchQuery);
        }
        if(searchRequest.getPriority() != null) {
            getPriority(String.valueOf(searchRequest.getPriority()), finalQuery);
        }
        if(searchRequest.getFilter().get("cc") != null) {
            if(String.valueOf(searchRequest.getFilter().get("cc")).equals("0")) {
                getCCRangeQueryNot(String.valueOf(-1), finalQuery);
            } else {
                getCCRangeQuery(String.valueOf(searchRequest.getFilter().get("cc")), finalQuery);
            }
        }
        getDateRangeQuery(searchRequest, finalQuery);
        if(searchRequest.getFilter().get("status") != null) {
            getStatusQuery((List<String>) searchRequest.getFilter().get("status"), finalQuery);
        }
        getJunkQuery(searchRequest.getIsJunk(), finalQuery);

        if (searchRequest.getFilter().get("ticketUserTypeId") != null) {
            Long ticketUserTypeId = null;

            try {
                ticketUserTypeId = Long.parseLong(String.valueOf(searchRequest.getFilter().get("ticketUserTypeId")));
            } catch (NumberFormatException e) {
                log.error("Error while reading value for ticket user type id");
                throw new InvalidDataException("Invalid type of ticket user type id - Supports long/numeric");
            }

            getUserTypeQuery(ticketUserTypeId, finalQuery);
        }

        if (searchRequest.getFilter().get("ticketCouncilId") != null) {
            Long tickectCouncilId = null;

            try {
                tickectCouncilId = Long.parseLong(String.valueOf(searchRequest.getFilter().get("ticketCouncilId")));
            } catch (NumberFormatException e) {
                log.error("Error while reading value for ticket council id");
                throw new InvalidDataException("Invalid type of ticket council id - Supports long/numeric");
            }

            getTicketCouncilQuery(tickectCouncilId, finalQuery);
        }

        if (searchRequest.getFilter().get("ticketDepartmentId") != null) {
            Long ticketDepartmentID = null;

            try {
                ticketDepartmentID = Long.parseLong(String.valueOf(searchRequest.getFilter().get("ticketDepartmentId")));
            } catch (NumberFormatException e) {
                log.error("Error while reading value for ticket department id");
                throw new InvalidDataException("Invalid type of ticket department id - Supports long/numeric");
            }

            getTicketDepartmentQuery(ticketDepartmentID, finalQuery);
        }
        // add rating filter
        addRatingFilter(searchRequest.getRating(), finalQuery);

        getEscalatedTicketsQuery(searchRequest.getIsEscalated(), finalQuery);
        return finalQuery;
    }

    private void addRatingFilter(Long rating, BoolQueryBuilder finalQuery) {
        if(rating == null || rating < 0 || rating > 5) {
            log.error("Rating value should be between 0 to 5");
            return;
        }
        MatchQueryBuilder ratingQuery = QueryBuilders.matchQuery("rating", rating);
        BoolQueryBuilder ratingSearchQuery = QueryBuilders.boolQuery();
        ratingSearchQuery.must(ratingQuery);
        finalQuery.must(ratingQuery);
    }

    private BoolQueryBuilder createTicketEscalationQuery(Long lastUpdatedEpoch) {
        BoolQueryBuilder finalQuery = QueryBuilders.boolQuery();
        // search query
        RangeQueryBuilder createdDateKeywordMatchQuery = QueryBuilders.rangeQuery("updated_date_ts").lte(lastUpdatedEpoch);
        MatchQueryBuilder escalatedMatchQuery = QueryBuilders.matchQuery("is_escalated", false);
        MatchQueryBuilder statusMatchQuery = QueryBuilders.matchQuery("status", "OPEN");
        BoolQueryBuilder keywordSearchQuery = QueryBuilders.boolQuery();
        keywordSearchQuery.must(createdDateKeywordMatchQuery).must(escalatedMatchQuery).must(statusMatchQuery);
        finalQuery.must(keywordSearchQuery);
        return finalQuery;
    }

    private BoolQueryBuilder getPriority(String priority, BoolQueryBuilder finalQuery) {
        if (priority !=null && !priority.isBlank()) {
            MatchQueryBuilder priorityMatchQuery = QueryBuilders.matchQuery("priority", priority);
            BoolQueryBuilder prioritySearchQuery = QueryBuilders.boolQuery();
            prioritySearchQuery.must(priorityMatchQuery);
            finalQuery.must(prioritySearchQuery);
        }
        return finalQuery;
    }

    private BoolQueryBuilder getCCRangeQuery(String cc, BoolQueryBuilder finalQuery) {
        if (cc != null) {
            MatchQueryBuilder ccMatchQuery = QueryBuilders.matchQuery("assigned_to_id", cc);
            BoolQueryBuilder ccSearchQuery = QueryBuilders.boolQuery();
            ccSearchQuery.must(ccMatchQuery);
            finalQuery.must(ccSearchQuery);
        }
        return finalQuery;
    }

    private BoolQueryBuilder getCCRangeQueryNot(String cc, BoolQueryBuilder finalQuery) {
        if (cc != null) {
            MatchQueryBuilder ccMatchQuery = QueryBuilders.matchQuery("assigned_to_id", cc);
            BoolQueryBuilder ccSearchQuery = QueryBuilders.boolQuery();
            ccSearchQuery.mustNot(ccMatchQuery);
            finalQuery.must(ccSearchQuery);
        }
        return finalQuery;
    }

    private BoolQueryBuilder getJunkQuery(Boolean isJunk, BoolQueryBuilder finalQuery) {
        if (isJunk != null) {
            MatchQueryBuilder junkMatchQuery = QueryBuilders.matchQuery("is_junk", isJunk);
            BoolQueryBuilder junkSearchQuery = QueryBuilders.boolQuery();
            junkSearchQuery.must(junkMatchQuery);
            finalQuery.must(junkSearchQuery);
        }
        return finalQuery;
    }

    /**
     * @param userTypeId
     * @param finalQuery
     * @return
     */
    private BoolQueryBuilder getUserTypeQuery(Long userTypeId, BoolQueryBuilder finalQuery) {
        if (userTypeId != null) {
            MatchQueryBuilder ticketUserTypeQuery = QueryBuilders.matchQuery("ticket_user_type_id", userTypeId);
            BoolQueryBuilder userTypeSearchQuery = QueryBuilders.boolQuery();
            userTypeSearchQuery.must(ticketUserTypeQuery);
            finalQuery.must(userTypeSearchQuery);
        }
        return finalQuery;
    }

    private BoolQueryBuilder getTicketCouncilQuery(Long ticketCouncilId, BoolQueryBuilder finalQuery) {
        if (ticketCouncilId != null) {
            MatchQueryBuilder ticketCouncilQuery = QueryBuilders.matchQuery("ticket_council_id", ticketCouncilId);
            BoolQueryBuilder councilSearchQuery = QueryBuilders.boolQuery();
            councilSearchQuery.must(ticketCouncilQuery);
            finalQuery.must(councilSearchQuery);
        }
        return finalQuery;
    }
    private BoolQueryBuilder getTicketDepartmentQuery(Long ticketDepartmentId, BoolQueryBuilder finalQuery) {
        if (ticketDepartmentId != null) {
            MatchQueryBuilder ticketDepartmentQuery = QueryBuilders.matchQuery("ticket_department_id", ticketDepartmentId);
            BoolQueryBuilder ticketDepartmentSearchQuery = QueryBuilders.boolQuery();
            ticketDepartmentSearchQuery.must(ticketDepartmentQuery);
            finalQuery.must(ticketDepartmentSearchQuery);
        }
        return finalQuery;
    }

    private BoolQueryBuilder getEscalatedTicketsQuery(Boolean isEscalated, BoolQueryBuilder finalQuery) {
        if (isEscalated != null) {
            MatchQueryBuilder escalatedMatchQuery = QueryBuilders.matchQuery("is_escalated_to_admin", isEscalated);
            BoolQueryBuilder escalatedSearchQuery = QueryBuilders.boolQuery();
            escalatedSearchQuery.must(escalatedMatchQuery);
            finalQuery.must(escalatedSearchQuery);
        }
        return finalQuery;
    }

    private BoolQueryBuilder getStatusQuery(List<String> statusList, BoolQueryBuilder finalQuery) {
        if (statusList != null) {
            MatchQueryBuilder statusMatchQuery = null;
            BoolQueryBuilder statusSearchQuery = QueryBuilders.boolQuery();
            for (int i = 0; i < statusList.size(); i++) {
                statusMatchQuery = QueryBuilders.matchQuery("status", statusList.get(i));
                statusSearchQuery.should(statusMatchQuery);
            }
            finalQuery.must(statusSearchQuery);

        }
        return finalQuery;
    }

    private BoolQueryBuilder getDateRangeQuery(SearchRequest searchRequest, BoolQueryBuilder finalQuery) {
        if (searchRequest.getDate() != null && searchRequest.getDate().getFrom() != null && searchRequest.getDate().getFrom() > 0) {
            RangeQueryBuilder fromTimestampMatchQuery = QueryBuilders.rangeQuery("created_date_ts").gte(searchRequest.getDate().getFrom());
            if (searchRequest.getDate().getTo() != null && searchRequest.getDate().getTo() > 0) {
                fromTimestampMatchQuery.lt(searchRequest.getDate().getTo());
            }
            BoolQueryBuilder timestampSearchQuery = QueryBuilders.boolQuery();
            timestampSearchQuery.must(fromTimestampMatchQuery);
            finalQuery.must(timestampSearchQuery);
        }
        return finalQuery;
    }

    @Override
    public List<Ticket> getAllTicketByIdList(List<Long> ids) {
        QueryBuilder ticketIdQueryBuilder = QueryBuilders.termsQuery("ticket_id", ids);
        BoolQueryBuilder ticketBooleanQueryBuilder = QueryBuilders.boolQuery().filter(ticketIdQueryBuilder);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder().query(ticketBooleanQueryBuilder);

        org.elasticsearch.action.search.SearchRequest search =
                new org.elasticsearch.action.search.SearchRequest("ticket");

        search.searchType(SearchType.QUERY_THEN_FETCH);
        search.source(searchSourceBuilder);

        log.info(">>>>>>>>>>>> Ticket query for id list - {}", searchSourceBuilder);

        try {
            SearchResponse searchResponse = esConfig.elasticsearchClient().search(search, RequestOptions.DEFAULT);

            return getTicketDocumentsFromHits(searchResponse.getHits());
        } catch (IOException e) {
            log.error("Error while searching ticket by ticket id list", e);
        }

        return Collections.emptyList();
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

    /**
     * Method to get statistical data on below values
     * "match": {
     *       "assigned_to_id": 484,
     *       "create_date": "2023-12-13",
     *       "status": "OPEN"
     *     }
     */
    public SearchResponse filterTicketByUserAndStatusAndRange(Long assignedUserId, List<String> status, SearchDateRange searchDateRange) {
        SearchResponse searchResponse;
        // bool query
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        getCCRangeQuery(String.valueOf(assignedUserId), boolQueryBuilder);
        // date range query
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setDate(searchDateRange);
        getDateRangeQuery(searchRequest, boolQueryBuilder);
        // search by status
        getStatusQuery(status, boolQueryBuilder);
        log.debug("Created final query - {}", boolQueryBuilder.toString());
        // fire query
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
                .query(createTicketSearchQuery(searchRequest))
                .sort(SortBuilders.fieldSort("ticket_id").order(SortOrder.DESC));

        org.elasticsearch.action.search.SearchRequest search = new org.elasticsearch.action.search.SearchRequest("ticket");
        search.searchType(SearchType.QUERY_THEN_FETCH);
        search.source(searchSourceBuilder);
        log.info("query string - {}", searchSourceBuilder);
        try {
            searchResponse = esConfig.elasticsearchClient().search(search, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return searchResponse;
    }
}