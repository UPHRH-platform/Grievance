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
import org.upsmf.grievance.repository.TicketCouncilRepository;
import org.upsmf.grievance.service.TicketCouncilService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TicketCouncilServiceImpl implements TicketCouncilService {

    @Autowired
    private TicketCouncilRepository ticketCouncilRepository;

    /**
     * @param ticketCouncilDto
     */
    @Override
    public void save(TicketCouncilDto ticketCouncilDto) {
        Optional<TicketCouncil> ticketCouncilOptional = ticketCouncilRepository
                .findByTicketCouncilName(StringUtils.upperCase(ticketCouncilDto.getTicketCouncilName()));

        if (ticketCouncilOptional.isPresent()) {
            log.error("Ticket council name is already exist");
            throw new CustomException("Duplicate council name not allowed");
        }

        TicketCouncil ticketCouncil = TicketCouncil.builder()
                .ticketCouncilName(StringUtils.upperCase(ticketCouncilDto.getTicketCouncilName()))
                .status(true)
                .build();

        try {
            ticketCouncilRepository.save(ticketCouncil);
        } catch (Exception e) {
            log.error("Error while saving ticket user type", e);
            throw new CustomException("Error while saving ticket user type", e.getMessage());
        }
    }

    /**
     * @param ticketCouncilDto
     */
    @Override
    public void update(TicketCouncilDto ticketCouncilDto) {
        if (ticketCouncilDto.getTicketCouncilId() == null) {
            log.error("Ticket council id is missing");
            throw new CustomException("Ticket council id is missing");
        }

        Optional<TicketCouncil> ticketCouncilNameOptional = ticketCouncilRepository
                .findByTicketCouncilName(ticketCouncilDto.getTicketCouncilName());

        if (ticketCouncilNameOptional.isPresent()) {
            log.error("Ticket council name is already exist");
            throw new CustomException("Duplicate council name not allowed");
        }

        Optional<TicketCouncil> ticketCouncilOptional = ticketCouncilRepository
                .findById(ticketCouncilDto.getTicketCouncilId());

        if (ticketCouncilOptional.isPresent()) {
            TicketCouncil ticketCouncil = ticketCouncilOptional.get();

            ticketCouncil.setTicketCouncilName(StringUtils.upperCase(ticketCouncilDto.getTicketCouncilName()));

            ticketCouncilRepository.save(ticketCouncil);
        } else {
            throw new DataUnavailabilityException("Unable to find ticket council details");
        }
    }

    /**
     * @return
     */
    @Override
    public List<TicketCouncilDto> findAllCouncil() {
        List<TicketCouncil> ticketCouncilList = ticketCouncilRepository.findAll();

        if (ticketCouncilList != null && !ticketCouncilList.isEmpty()) {

            List<TicketCouncilDto> ticketCouncilDtoList = ticketCouncilList.stream()
                    .map(ticketCouncil -> convertTicketCouncilEntityToDto(ticketCouncil))
                    .collect(Collectors.toList());

            return ticketCouncilDtoList;
        }

        return Collections.emptyList();
    }

    private @NonNull TicketCouncilDto convertTicketCouncilEntityToDto(@NonNull TicketCouncil ticketCouncil) {
        TicketCouncilDto ticketCouncilDto = TicketCouncilDto.builder()
                .ticketCouncilId(ticketCouncil.getId())
                .ticketCouncilName(ticketCouncil.getTicketCouncilName())
                .status(ticketCouncil.getStatus())
                .build();

        if (ticketCouncil.getTicketDepartments() != null && !ticketCouncil.getTicketDepartments().isEmpty()) {
            List<TicketDepartmentDto> ticketDepartmentDtoList = ticketCouncil.getTicketDepartments().stream()
                    .map(ticketDepartment -> TicketDepartmentDto.builder()
                            .ticketDepartmentId(ticketDepartment.getId())
                            .ticketDepartmentName(ticketDepartment.getTicketDepartmentName())
                            .build()
                    ).collect(Collectors.toList());

            ticketCouncilDto.setTicketDepartmentDtoList(ticketDepartmentDtoList);
        }

        return ticketCouncilDto;
    }

    /**
     * @param ticketCouncilDto
     */
    @Override
    public void updateTicketCouncilActivation(TicketCouncilDto ticketCouncilDto) {
        Optional<TicketCouncil> ticketUserTypeOptional = ticketCouncilRepository
                .findById(ticketCouncilDto.getTicketCouncilId());

        if (!ticketUserTypeOptional.isPresent()) {
            throw new DataUnavailabilityException("Unable to find ticket council details");
        }

        if (ticketCouncilDto.getStatus() == null) {
            throw new InvalidDataException("Invalid status value");
        }

        TicketCouncil ticketCouncil = ticketUserTypeOptional.get();
        ticketCouncil.setStatus(ticketCouncilDto.getStatus());

        ticketCouncilRepository.save(ticketCouncil);
    }

    /**
     * @param councilId
     * @return
     */
    public String getCouncilName(@NonNull Long councilId) {
        Optional<TicketCouncil> ticketCouncilOptional = ticketCouncilRepository.findById(councilId);

        if (!ticketCouncilOptional.isPresent()) {
            throw new DataUnavailabilityException("Unable to find council");
        }

        return ticketCouncilOptional.get().getTicketCouncilName();
    }
}