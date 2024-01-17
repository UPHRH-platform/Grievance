package org.upsmf.grievance.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.upsmf.grievance.dto.TicketRequest;
import org.upsmf.grievance.dto.UpdateTicketRequest;
import org.upsmf.grievance.exception.CustomException;
import org.upsmf.grievance.exception.TicketException;
import org.upsmf.grievance.exception.UserException;
import org.upsmf.grievance.model.Ticket;
import org.upsmf.grievance.model.TicketStatistics;
import org.upsmf.grievance.model.reponse.Response;
import org.upsmf.grievance.service.AttachmentService;
import org.upsmf.grievance.service.DashboardService;
import org.upsmf.grievance.service.TicketService;
import org.upsmf.grievance.util.ErrorCode;

@Slf4j
@Controller
@RequestMapping("/api/ticket")
public class TicketController {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private DashboardService dashboardService;

    @PostMapping("/save")
    public ResponseEntity<Response> save(@RequestBody TicketRequest ticketRequest) {
        Ticket responseTicket = null;
        try {
            responseTicket = ticketService.save(ticketRequest);
        } catch (CustomException e) {
            log.error("Error in while creating ticket - at controller");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error while trying to create ticket");
        } catch (Exception e) {
            log.error("Internal server error while creating ticket", e);
            throw new TicketException("Internal server error while creating ticket", ErrorCode.TKT_002, e.getMessage());
        }
        Response response = new Response(HttpStatus.OK.value(), responseTicket);
        return new ResponseEntity<Response>(response, HttpStatus.OK);
    }

    @PostMapping("/update")
    public ResponseEntity<Response> update(@RequestBody UpdateTicketRequest updateTicketRequest) {
        Ticket responseTicket = null;
        try {
            responseTicket = ticketService.update(updateTicketRequest);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        Response response = new Response(HttpStatus.OK.value(), responseTicket);
        return new ResponseEntity<Response>(response, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Response> update(@RequestParam(name = "id") long id) {
        Ticket responseTicket = ticketService.getTicketById(id);
        Response response = new Response(HttpStatus.OK.value(), responseTicket);
        return new ResponseEntity<Response>(response, HttpStatus.OK);
    }

    @PostMapping("/file/upload")
    public ResponseEntity upload(@RequestParam("file") MultipartFile file) {
        try {
            return attachmentService.uploadObject(file);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getLocalizedMessage());
        }
    }

    @PostMapping("/statistic")
    public ResponseEntity upload(@RequestBody JsonNode userData) {
        try {
            TicketStatistics ticketStatisticsByUser = dashboardService.getTicketStatisticsByUser(userData);
            Response response = new Response(HttpStatus.OK.value(), ticketStatisticsByUser);
            return new ResponseEntity<Response>(response, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getLocalizedMessage());
        }
    }
}
