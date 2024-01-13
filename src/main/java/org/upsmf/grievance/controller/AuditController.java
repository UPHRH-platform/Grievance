package org.upsmf.grievance.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.upsmf.grievance.dto.*;
import org.upsmf.grievance.exception.CustomException;
import org.upsmf.grievance.exception.TicketException;
import org.upsmf.grievance.model.reponse.Response;
import org.upsmf.grievance.service.TicketAuditService;
import org.upsmf.grievance.service.TicketCouncilService;
import org.upsmf.grievance.service.TicketDepartmentService;
import org.upsmf.grievance.service.TicketUserTypeService;
import org.upsmf.grievance.util.ErrorCode;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping(path = "/api/audit")
public class AuditController {

    @Autowired
    private TicketAuditService ticketAuditService;

    @GetMapping("/ticket")
    public ResponseEntity getTicketAuditById(@RequestParam Long ticketId) {
        try {
            List<TicketAuditDto> ticketAuditDtoList = ticketAuditService.getTicketAuditByTicketId(ticketId);

            return new ResponseEntity<>(ticketAuditDtoList, HttpStatus.OK);
        } catch (CustomException e) {
            log.error("Error in while getting assigned department");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error in while getting assigned department");
        } catch (Exception e) {
            log.error("Error in while getting assigned department", e);
            throw new TicketException("Error in while getting assigned department", ErrorCode.TKT_002, e.getMessage());
        }
    }


}
