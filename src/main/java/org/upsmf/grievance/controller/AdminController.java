package org.upsmf.grievance.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.upsmf.grievance.dto.TicketCouncilDto;
import org.upsmf.grievance.dto.TicketDepartmentDto;
import org.upsmf.grievance.dto.TicketRequest;
import org.upsmf.grievance.dto.TicketUserTypeDto;
import org.upsmf.grievance.exception.CustomException;
import org.upsmf.grievance.exception.DataUnavailabilityException;
import org.upsmf.grievance.exception.TicketException;
import org.upsmf.grievance.model.Ticket;
import org.upsmf.grievance.model.reponse.Response;
import org.upsmf.grievance.service.TicketCouncilService;
import org.upsmf.grievance.service.TicketDepartmentService;
import org.upsmf.grievance.service.TicketUserTypeService;
import org.upsmf.grievance.util.ErrorCode;

import javax.validation.Valid;
import java.util.List;

@Slf4j
@RestController
@RequestMapping(path = "/api/admin")
public class AdminController {

    @Autowired
    private TicketUserTypeService ticketUserTypeService;

    @Autowired
    private TicketCouncilService ticketCouncilService;

    @Autowired
    private TicketDepartmentService ticketDepartmentService;

    /**
     * @param ticketUserTypeDto
     * @return
     */
    @PostMapping("/ticket/userType/save")
    public ResponseEntity<Response> saveUserType(@Valid @RequestBody TicketUserTypeDto ticketUserTypeDto) {

        try {
            ticketUserTypeService.save(ticketUserTypeDto);
        } catch (CustomException e) {
            log.error("Error in while creating ticket user type - at controller");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error while trying to create ticket user type");
        } catch (Exception e) {
            log.error("Internal server error while creating ticket user type", e);
            throw new TicketException("Internal server error while creating ticket user type", ErrorCode.TKT_002, e.getMessage());
        }
        Response response = new Response(HttpStatus.OK.value(), "Ticket user type has been created successfully");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * @param ticketUserTypeDto
     * @return
     */
    @PostMapping("/ticket/userType/update")
    public ResponseEntity<Response> updateUserType(@Valid @RequestBody TicketUserTypeDto ticketUserTypeDto) {

        try {
            ticketUserTypeService.update(ticketUserTypeDto);
        } catch (CustomException e) {
            log.error("Error in while updating ticket user type - at controller");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error while trying to update ticket user type");
        } catch (Exception e) {
            log.error("Internal server error while updating ticket user type", e);
            throw new TicketException("Internal server error while updating update user type", ErrorCode.TKT_002, e.getMessage());
        }
        Response response = new Response(HttpStatus.OK.value(), "Ticket user type has been updated successfully");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * @param ticketUserTypeDto
     * @return
     */
    @PostMapping("/ticket/userType/updateActivation")
    public ResponseEntity<Response> updateUserTypeActivation(@RequestBody TicketUserTypeDto ticketUserTypeDto) {

        try {
            ticketUserTypeService.updateUserTypeActivation(ticketUserTypeDto);
        } catch (CustomException e) {
            log.error("Error in while updating ticket user type status- at controller");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error while trying to update ticket user type status");
        } catch (Exception e) {
            log.error("Internal server error while updating ticket user type status", e);
            throw new TicketException("Internal server error while updating update user type status", ErrorCode.TKT_002, e.getMessage());
        }
        Response response = new Response(HttpStatus.OK.value(), "Ticket user type status has been updated successfully");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * @return
     */
    @GetMapping("/ticket/userType/searchAll")
    public ResponseEntity<List<TicketUserTypeDto>> searchAllUserType() {

        try {
            List<TicketUserTypeDto> ticketUserTypeList =  ticketUserTypeService.findAllUserType();

            return new ResponseEntity<>(ticketUserTypeList, HttpStatus.OK);

        } catch (CustomException e) {
            log.error("Error in while fetching all ticket user type - at controller");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error while fetching all ticket user type");
        } catch (Exception e) {
            log.error("Internal server error while fetching all ticket user type", e);
            throw new TicketException("Internal server error while fetching all ticket user type", ErrorCode.TKT_002, e.getMessage());
        }
    }

    /**
     * @param ticketCouncilDto
     * @return
     */
    @PostMapping("/ticket/council/save")
    public ResponseEntity<Response> ticketCouncilSave(@Valid @RequestBody TicketCouncilDto ticketCouncilDto) {

        try {
            ticketCouncilService.save(ticketCouncilDto);
        } catch (CustomException e) {
            log.error("Error in while creating ticket council - at controller");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error while trying to create ticket council");
        } catch (Exception e) {
            log.error("Internal server error while creating ticket council", e);
            throw new TicketException("Internal server error while creating ticket council", ErrorCode.TKT_002, e.getMessage());
        }
        Response response = new Response(HttpStatus.OK.value(), "Ticket council has been created successfully");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * @param ticketCouncilDto
     * @return
     */
    @PostMapping("/ticket/council/update")
    public ResponseEntity<Response> ticketCouncilUpdate(@Valid @RequestBody TicketCouncilDto ticketCouncilDto) {

        try {
            ticketCouncilService.update(ticketCouncilDto);
        } catch (CustomException e) {
            log.error("Error in while updating ticket council - at controller");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error while trying to update ticket council");
        } catch (Exception e) {
            log.error("Internal server error while updating ticket council", e);
            throw new TicketException("Internal server error while updating ticket council", ErrorCode.TKT_002, e.getMessage());
        }
        Response response = new Response(HttpStatus.OK.value(), "Ticket council has been created successfully");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * @return
     */
    @GetMapping("/ticket/council/searchAll")
    public ResponseEntity<List<TicketCouncilDto>> searchAllCouncil() {

        try {
            List<TicketCouncilDto> ticketCouncilDtoList = ticketCouncilService.findAllCouncil();

            return new ResponseEntity<>(ticketCouncilDtoList, HttpStatus.OK);

        } catch (CustomException e) {
            log.error("Error in while fetching all ticket council data- at controller");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error while fetching all ticket council data");
        } catch (Exception e) {
            log.error("Internal server error while fetching all ticket council data", e);
            throw new TicketException("Internal server error while fetching all ticket council data", ErrorCode.TKT_002, e.getMessage());
        }
    }

    /**
     * @param ticketCouncilDto
     * @return
     */
    @PostMapping("/ticket/council/updateActivation")
    public ResponseEntity<Response> updateTicketCouncilActivation(@RequestBody TicketCouncilDto ticketCouncilDto) {

        try {
            ticketCouncilService.updateTicketCouncilActivation(ticketCouncilDto);
        } catch (CustomException e) {
            log.error("Error in while updating ticket council activation status- at controller");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error while trying to update ticket council activation status");
        } catch (Exception e) {
            log.error("Internal server error while updating ticket council activation status", e);
            throw new TicketException("Internal server error while updating update council activation status", ErrorCode.TKT_002, e.getMessage());
        }
        Response response = new Response(HttpStatus.OK.value(), "Ticket council activation status has been updated successfully");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * @param ticketDepartmentDto
     * @return
     */
    @PostMapping("/ticket/department/save")
    public ResponseEntity<Response> ticketDepartmentSave(@Valid @RequestBody TicketDepartmentDto ticketDepartmentDto) {

        try {
            ticketDepartmentService.save(ticketDepartmentDto);
        } catch (CustomException e) {
            log.error("Error in while creating ticket department - at controller");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error while trying to create ticket department");
        } catch (Exception e) {
            log.error("Internal server error while creating ticket department", e);
            throw new TicketException("Internal server error while creating ticket department", ErrorCode.TKT_002, e.getMessage());
        }
        Response response = new Response(HttpStatus.OK.value(), "Ticket department has been created successfully");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }


    /**
     * @param ticketDepartmentDto
     * @return
     */
    @PostMapping("/ticket/department/update")
    public ResponseEntity<Response> ticketDepartmentUpdate(@Valid @RequestBody TicketDepartmentDto ticketDepartmentDto) {

        try {
            ticketDepartmentService.update(ticketDepartmentDto);
        } catch (CustomException e) {
            log.error("Error in while updating ticket department - at controller");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error while trying to update ticket department");
        } catch (Exception e) {
            log.error("Internal server error while updating ticket department", e);
            throw new TicketException("Internal server error while updating ticket department", ErrorCode.TKT_002, e.getMessage());
        }
        Response response = new Response(HttpStatus.OK.value(), "Ticket department has been updated successfully");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    /**
     * @return
     */
    @GetMapping("/ticket/department/searchAll")
    public ResponseEntity<List<TicketDepartmentDto>> searchAllTicketDepartment() {

        try {
            List<TicketDepartmentDto> ticketDepartmentDtoList = ticketDepartmentService.findAllTicketDepartment();

            return new ResponseEntity<>(ticketDepartmentDtoList, HttpStatus.OK);

        } catch (CustomException e) {
            log.error("Error in while fetching all ticket department data- at controller");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error while fetching all ticket department data");
        } catch (Exception e) {
            log.error("Internal server error while fetching all ticket department data", e);
            throw new TicketException("Internal server error while fetching all ticket department data", ErrorCode.TKT_002, e.getMessage());
        }
    }

    @PostMapping("/ticket/department/updateActivation")
    public ResponseEntity<Response> updateTicketDepartmentActivation(@RequestBody TicketDepartmentDto ticketDepartmentDto) {

        try {
            ticketDepartmentService.updateTicketDepartmentActivation(ticketDepartmentDto);
        } catch (CustomException e) {
            log.error("Error in while updating ticket department activation status- at controller");
            throw new TicketException(e.getMessage(), ErrorCode.TKT_001, "Error while trying to update ticket department activation status");
        } catch (Exception e) {
            log.error("Internal server error while updating ticket department activation status", e);
            throw new TicketException("Internal server error while updating update department activation status", ErrorCode.TKT_002, e.getMessage());
        }
        Response response = new Response(HttpStatus.OK.value(), "Ticket department activation status has been updated successfully");
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
