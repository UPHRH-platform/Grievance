package org.upsmf.grievance.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.upsmf.grievance.dto.FeedbackDto;
import org.upsmf.grievance.model.es.Feedback;
import org.upsmf.grievance.model.reponse.FeedbackResponse;
import org.upsmf.grievance.model.reponse.Response;
import org.upsmf.grievance.service.DashboardService;
import org.upsmf.grievance.service.FeedbackService;

import javax.validation.Valid;
import java.util.List;

@Controller
@RequestMapping("/api/feedback")
@Validated
@Slf4j
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private DashboardService dashboardService;

    @PostMapping("/save")
    public ResponseEntity saveFeedback(@Valid  @RequestBody FeedbackDto feedbackDto) {
        try {
            feedbackService.saveFeedback(feedbackDto);
            return new ResponseEntity(new Response(HttpStatus.OK.value(), null), HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity(new Response(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/id")
    public ResponseEntity saveFeedback(@Valid @RequestParam(name = "id") String id) {
        try {
            log.info(" get feedback by ID payload - {} ", id);
            List<Feedback> feedbackByTicketId = dashboardService.getFeedbackByTicketId(id);
            log.info(" get feedback by ID response - {} ", feedbackByTicketId);
            return new ResponseEntity(new Response(HttpStatus.OK.value(), feedbackByTicketId), HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity(new Response(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/all")
    public ResponseEntity<Response> getFeedbacks() {
        return new ResponseEntity<Response>(new Response(HttpStatus.OK.value(), feedbackService.getFeedbacks()), HttpStatus.OK);
    }
}
