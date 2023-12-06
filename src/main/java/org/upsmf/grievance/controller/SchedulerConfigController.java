package org.upsmf.grievance.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.upsmf.grievance.dto.MailConfigDto;
import org.upsmf.grievance.exception.CustomException;
import org.upsmf.grievance.exception.MailConfigException;
import org.upsmf.grievance.model.reponse.Response;
import org.upsmf.grievance.service.SchedulerConfigService;
import org.upsmf.grievance.util.ErrorCode;

@Slf4j
@Controller
@RequestMapping("/api/config/mail")
public class SchedulerConfigController {

    @Autowired
    private SchedulerConfigService schedulerConfigService;

    @PostMapping("/save")
    public ResponseEntity createUser(@RequestBody MailConfigDto mailConfigDto) {
        try {
            MailConfigDto mailConfig = schedulerConfigService.save(mailConfigDto);
            return new ResponseEntity(new Response(HttpStatus.OK.value(), mailConfig), HttpStatus.OK);
        } catch (CustomException e) {
            log.error("Error in while creating user - at controller");
            throw new MailConfigException(e.getMessage(), ErrorCode.MAIL_001, "Error in saving configuration");
        } catch (Exception e) {
            log.error("Error while creating config", e);
            return ResponseEntity.internalServerError().body(e.getLocalizedMessage());
        }
    }

}
