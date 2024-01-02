package org.upsmf.grievance.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.upsmf.grievance.dto.SearchRequest;
import org.upsmf.grievance.model.reponse.Response;
import org.upsmf.grievance.model.reponse.TicketResponse;
import org.upsmf.grievance.service.SearchService;
import org.upsmf.grievance.service.impl.DashboardServiceImpl;

import java.util.Map;

@Controller
@RequestMapping("/api/search")
public class SearchTicketController {

    @Autowired
    private SearchService searchService;

    @Autowired
    private DashboardServiceImpl dashboardService;

    @PostMapping("/getAllTickets")
    public ResponseEntity<Response> search(@RequestBody SearchRequest searchRequest) {
        TicketResponse responseTicket = searchService.search(searchRequest);
        Response response = new Response(HttpStatus.OK.value(), responseTicket);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/searchTickets")
    public ResponseEntity<Response> searchTickets(@RequestBody SearchRequest searchRequest) {
        Map<String, Object> responseTicket = null;
        try {
            responseTicket = searchService.searchTickets(searchRequest);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
        Response response = new Response(HttpStatus.OK.value(), responseTicket);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/dashboardReport")
    public ResponseEntity<Response> getDashboardReport(@RequestBody SearchRequest searchRequest) {
        Map<String, Object> responseTicket = null;
        try {
            //responseTicket = searchService.dashboardReport(searchRequest);
            responseTicket = dashboardService.dashboardReport(searchRequest);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        Response response = new Response(HttpStatus.OK.value(), responseTicket);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}