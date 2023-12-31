package org.upsmf.grievance.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.upsmf.grievance.dto.FeedbackDto;
import org.upsmf.grievance.model.reponse.Response;
import org.upsmf.grievance.service.FeedbackService;

import javax.validation.Valid;

@Controller
@RequestMapping("/api/feedback")
@Validated
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    @PostMapping("/save")
    public ResponseEntity saveFeedback(@Valid  @RequestBody FeedbackDto feedbackDto) {
        try {
            feedbackService.saveFeedback(feedbackDto);
            return new ResponseEntity(new Response(HttpStatus.OK.value(), null), HttpStatus.OK);
        }catch (Exception e){
            return new ResponseEntity(new Response(HttpStatus.INTERNAL_SERVER_ERROR.value(), e.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/all")
    public ResponseEntity<Response> getFeedbacks() {
        return new ResponseEntity<Response>(new Response(HttpStatus.OK.value(), feedbackService.getFeedbacks()), HttpStatus.OK);
    }
}
