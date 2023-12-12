package org.upsmf.grievance.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.dto.TicketCouncilDto;
import org.upsmf.grievance.dto.TicketDepartmentDto;
import org.upsmf.grievance.exception.CustomException;
import org.upsmf.grievance.exception.DataUnavailabilityException;
import org.upsmf.grievance.exception.InvalidDataException;
import org.upsmf.grievance.model.TicketCouncil;
import org.upsmf.grievance.model.TicketDepartment;
import org.upsmf.grievance.repository.TicketCouncilRepository;
import org.upsmf.grievance.repository.TicketDepartmentRepository;
import org.upsmf.grievance.service.TicketDepartmentService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TicketDepartmentServiceImpl implements TicketDepartmentService {

    @Autowired
    private TicketDepartmentRepository ticketDepartmentRepository;

    @Autowired
    private TicketCouncilRepository ticketCouncilRepository;

    /**
     * @param ticketDepartmentDto
     */
    @Override
    public void save(TicketDepartmentDto ticketDepartmentDto) {
//        Todo: allow duplicate department name
//        Optional<TicketDepartment> ticketDepartmentOptional = ticketDepartmentRepository
//                .findByTicketDepartmentName(StringUtils.upperCase(ticketDepartmentDto.getTicketDepartmentName()));
//
//        if (ticketDepartmentOptional.isPresent()) {
//            log.error("Ticket department name is already exist");
//            throw new CustomException("Duplicate department name not allowed");
//        }

        Optional<TicketCouncil> ticketCouncilOptional = ticketCouncilRepository
                .findById(ticketDepartmentDto.getTicketCouncilId());

        if (!ticketCouncilOptional.isPresent()) {
            log.error("Unable to find council details");
            throw new DataUnavailabilityException("Unable to find council details");
        }

        TicketDepartment ticketDepartment = TicketDepartment.builder()
                .ticketCouncilId(ticketDepartmentDto.getTicketCouncilId())
                .ticketDepartmentName(StringUtils.upperCase(ticketDepartmentDto.getTicketDepartmentName()))
                .status(true)
                .build();

        try {
            ticketDepartmentRepository.save(ticketDepartment);
        } catch (Exception e) {
            log.error("Error while saving ticket department", e);
            throw new CustomException("Error while saving ticket department");
        }
    }

    /**
     * @param ticketDepartmentDto
     */
    @Override
    public void update(TicketDepartmentDto ticketDepartmentDto) {
        if (ticketDepartmentDto.getTicketDepartmentId() == null) {
            log.error("Ticket department id is missing");
            throw new CustomException("Ticket department id is missing");
        }

        Optional<TicketCouncil> ticketCouncilOptional = ticketCouncilRepository
                .findById(ticketDepartmentDto.getTicketCouncilId());

        if (!ticketCouncilOptional.isPresent()) {
            log.error("Unable to find council details");
            throw new DataUnavailabilityException("Unable to find council details");
        }

        Optional<TicketDepartment> ticketDepartmentOptional = ticketDepartmentRepository
                .findById(ticketDepartmentDto.getTicketDepartmentId());

        if (ticketDepartmentOptional.isPresent()) {
//            Todo: allow duplicate name update
//            Optional<TicketDepartment> ticketDepartmentNameOptional = ticketDepartmentRepository
//                    .findByTicketDepartmentName(StringUtils.upperCase(ticketDepartmentDto.getTicketDepartmentName()));
//
//            if (ticketDepartmentNameOptional.isPresent() ){
////                    && !ticketDepartmentOptional.get().getTicketDepartmentName()
////                    .equals(StringUtils.upperCase(ticketDepartmentDto.getTicketDepartmentName()))) {
//                log.error("Ticket department name is already exist");
//                throw new CustomException("Duplicate department name not allowed");
//            }

            TicketDepartment ticketDepartment = ticketDepartmentOptional.get();

            ticketDepartment.setTicketDepartmentName(StringUtils.upperCase(ticketDepartmentDto.getTicketDepartmentName()));
            ticketDepartment.setTicketCouncilId(ticketDepartmentDto.getTicketCouncilId());

            ticketDepartmentRepository.save(ticketDepartment);
        } else {
            throw new DataUnavailabilityException("Unable to find department details");
        }
    }

    @Override
    public List<TicketDepartmentDto> findAllTicketDepartment() {
        List<TicketDepartment> ticketDepartmentList = ticketDepartmentRepository.findAll();

        if (ticketDepartmentList != null && !ticketDepartmentList.isEmpty()) {

            List<TicketDepartmentDto> ticketDepartmentDtoList = ticketDepartmentList.stream()
                    .map(ticketDepartment -> TicketDepartmentDto.builder()
                            .ticketDepartmentId(ticketDepartment.getId())
                            .ticketDepartmentName(ticketDepartment.getTicketDepartmentName())
                            .status(ticketDepartment.getStatus())
                            .build())
                    .collect(Collectors.toList());

            return ticketDepartmentDtoList;
        }

        return Collections.emptyList();
    }

    /**
     * @param ticketDepartmentDto
     */
    @Override
    public void updateTicketDepartmentActivation(TicketDepartmentDto ticketDepartmentDto) {
        Optional<TicketDepartment> ticketDepartmentOptional = ticketDepartmentRepository
                .findById(ticketDepartmentDto.getTicketDepartmentId());

        if (!ticketDepartmentOptional.isPresent()) {
            throw new DataUnavailabilityException("Unable to find ticket department details");
        }

        if (ticketDepartmentDto.getStatus() == null) {
            throw new InvalidDataException("Invalid status value");
        }

        TicketDepartment ticketDepartment = ticketDepartmentOptional.get();
        ticketDepartment.setStatus(ticketDepartmentDto.getStatus());

        ticketDepartmentRepository.save(ticketDepartment);
    }

    @Override
    public String getDepartmentName(@NonNull Long departmentId, @NonNull Long councilId) {
        Optional<TicketDepartment> ticketDepartmentOptional = ticketDepartmentRepository
                .findById(departmentId);

        if (!ticketDepartmentOptional.isPresent()) {
            throw new DataUnavailabilityException("Unable to find department details");
        }

        TicketDepartment ticketDepartment = ticketDepartmentOptional.get();

        if (!councilId.equals(ticketDepartment.getTicketCouncilId())) {
            throw new InvalidDataException("Incorrect department and council tagging");
        }

        return ticketDepartment.getTicketDepartmentName();
    }

    @Override
    public boolean validateDepartmentInCouncil(Long departmentId, Long councilId) {
        if (departmentId != null && councilId != null) {
            Optional<TicketDepartment> ticketDepartmentOptional = ticketDepartmentRepository
                    .findByIdAndTicketCouncilId(departmentId, councilId);

            if (!ticketDepartmentOptional.isPresent()) {
                log.error("Unable to find user ticket department");
                return false;
            }else {
                return true;
            }
        }

        return false;
    }
}
