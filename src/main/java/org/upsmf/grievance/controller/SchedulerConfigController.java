package org.upsmf.grievance.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.upsmf.grievance.dto.MailConfigDto;
import org.upsmf.grievance.dto.SearchMailConfigDto;
import org.upsmf.grievance.dto.SearchMailConfigResponseDto;
import org.upsmf.grievance.exception.CustomException;
import org.upsmf.grievance.exception.MailConfigException;
import org.upsmf.grievance.model.reponse.Response;
import org.upsmf.grievance.service.SchedulerConfigService;
import org.upsmf.grievance.util.ErrorCode;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/api/config/mail")
public class SchedulerConfigController {

    @Autowired
    private SchedulerConfigService schedulerConfigService;

    @PutMapping("/save")
    public ResponseEntity saveConfig(@RequestBody MailConfigDto mailConfigDto) {
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

    @PostMapping("/update")
    public ResponseEntity updateConfig(@RequestBody MailConfigDto mailConfigDto) {
        try {
            MailConfigDto mailConfig = schedulerConfigService.update(mailConfigDto);
            return new ResponseEntity(new Response(HttpStatus.OK.value(), mailConfig), HttpStatus.OK);
        } catch (CustomException e) {
            log.error("Error in while creating user - at controller");
            throw new MailConfigException(e.getMessage(), ErrorCode.MAIL_002, "Error in updating configuration");
        } catch (Exception e) {
            log.error("Error while creating config", e);
            return ResponseEntity.internalServerError().body(e.getLocalizedMessage());
        }
    }

    @PostMapping("/search")
    public ResponseEntity searchMailConfig(@RequestBody SearchMailConfigDto searchMailConfigDto) {
        try {
            SearchMailConfigResponseDto mailConfigs = schedulerConfigService.searchMailConfig(searchMailConfigDto);
            return new ResponseEntity(new Response(HttpStatus.OK.value(), mailConfigs), HttpStatus.OK);
        } catch (CustomException e) {
            log.error("Error in while creating user - at controller");
            throw new MailConfigException(e.getMessage(), ErrorCode.MAIL_003, "Error in fetching configurations");
        } catch (Exception e) {
            log.error("Error while creating config", e);
            return ResponseEntity.internalServerError().body(e.getLocalizedMessage());
        }
    }

    @PostMapping("/status/update")
    public ResponseEntity activateMailConfig(@RequestParam Long id, @RequestParam Boolean active, @RequestParam Long userId) {
        try {
            MailConfigDto mailConfig = schedulerConfigService.activateConfigById(id, active, userId);
            return new ResponseEntity(new Response(HttpStatus.OK.value(), mailConfig), HttpStatus.OK);
        } catch (CustomException e) {
            log.error("Error in while creating user - at controller");
            throw new MailConfigException(e.getMessage(), ErrorCode.MAIL_004, "Error in activating configuration");
        } catch (Exception e) {
            log.error("Error while creating config", e);
            return ResponseEntity.internalServerError().body(e.getLocalizedMessage());
        }
    }

    @GetMapping("/all")
    public ResponseEntity getAllMailConfig() {
        try {
            List<MailConfigDto> mailConfig = schedulerConfigService.getAll();
            return new ResponseEntity(new Response(HttpStatus.OK.value(), mailConfig), HttpStatus.OK);
        } catch (CustomException e) {
            log.error("Error in while creating user - at controller");
            throw new MailConfigException(e.getMessage(), ErrorCode.MAIL_005, "Error in deactivating configuration");
        } catch (Exception e) {
            log.error("Error while creating config", e);
            return ResponseEntity.internalServerError().body(e.getLocalizedMessage());
        }
    }

}
