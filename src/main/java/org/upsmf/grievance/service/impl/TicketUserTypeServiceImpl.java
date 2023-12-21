package org.upsmf.grievance.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.dto.AdminTextSearchDto;
import org.upsmf.grievance.dto.TicketUserTypeDto;
import org.upsmf.grievance.exception.CustomException;
import org.upsmf.grievance.exception.DataUnavailabilityException;
import org.upsmf.grievance.exception.InvalidDataException;
import org.upsmf.grievance.model.TicketCouncil;
import org.upsmf.grievance.model.TicketUserType;
import org.upsmf.grievance.model.User;
import org.upsmf.grievance.repository.TicketUserTypeRepository;
import org.upsmf.grievance.service.TicketUserTypeService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TicketUserTypeServiceImpl implements TicketUserTypeService {

    @Autowired
    private TicketUserTypeRepository ticketUserTypeRepository;

    /**
     * @param ticketUserTypeDto
     */
    @Override
    public void save(TicketUserTypeDto ticketUserTypeDto) {
        Optional<TicketUserType> ticketUserTypeOptional = ticketUserTypeRepository
                .findByUserTypeName(StringUtils.upperCase(ticketUserTypeDto.getUserTypeName()));

        if (ticketUserTypeOptional.isPresent()) {
            log.error("Ticket user type name is already exist");
            throw new CustomException("Duplicate user type name not allowed");
        }

        TicketUserType ticketUserType = TicketUserType.builder()
                .userTypeName(StringUtils.upperCase(ticketUserTypeDto.getUserTypeName()))
                .status(true)
                .build();

        try {
            ticketUserTypeRepository.save(ticketUserType);
        } catch (Exception e) {
            log.error("Error while saving ticket user type", e);
            throw new CustomException("Error while saving ticket user type");
        }
    }

    /**
     * @param ticketUserTypeDto
     */
    @Override
    public void update(TicketUserTypeDto ticketUserTypeDto) {
        if (ticketUserTypeDto.getUserTypeId() == null) {
            log.error("Ticket council id is missing");
            throw new CustomException("Ticket council id is missing");
        }

        Optional<TicketUserType> ticketUserTypeNameOptional = ticketUserTypeRepository
                .findByUserTypeName(StringUtils.upperCase(ticketUserTypeDto.getUserTypeName()));

        if (ticketUserTypeNameOptional.isPresent()) {
            log.error("Ticket user type name is already exist");
            throw new CustomException("Duplicate user type name not allowed");
        }

        Optional<TicketUserType> ticketUserTypeOptional = ticketUserTypeRepository
                .findById(ticketUserTypeDto.getUserTypeId());

        if (ticketUserTypeOptional.isPresent()) {
            TicketUserType ticketUserType = ticketUserTypeOptional.get();

            ticketUserType.setUserTypeName(StringUtils.upperCase(ticketUserTypeDto.getUserTypeName()));

            ticketUserTypeRepository.save(ticketUserType);
        } else {
            throw new DataUnavailabilityException("Unable to find user type details");
        }
    }

    /**
     * @param ticketUserTypeDto
     */
    @Override
    public void updateUserTypeActivation(TicketUserTypeDto ticketUserTypeDto) {
        Optional<TicketUserType> ticketUserTypeOptional = ticketUserTypeRepository
                .findById(ticketUserTypeDto.getUserTypeId());

        if (!ticketUserTypeOptional.isPresent()) {
            throw new DataUnavailabilityException("Unable to find user type details");
        }

        if (ticketUserTypeDto.getStatus() == null) {
            throw new InvalidDataException("Invalid status value");
        }

        TicketUserType ticketUserType = ticketUserTypeOptional.get();
        ticketUserType.setStatus(ticketUserTypeDto.getStatus());

        ticketUserTypeRepository.save(ticketUserType);

    }

    /**
     * @return
     */
    @Override
    public List<TicketUserTypeDto> findAllUserType() {
        List<TicketUserType> ticketUserTypeList = new ArrayList<>();

        Iterable<TicketUserType> ticketUserTypeIterable = ticketUserTypeRepository.findAll();
        ticketUserTypeIterable.forEach(ticketUserTypeList::add);

        if (!ticketUserTypeList.isEmpty()) {
            return ticketUserTypeList.stream()
                    .map(ticketUserType -> TicketUserTypeDto.builder()
                            .userTypeId(ticketUserType.getId())
                            .userTypeName(ticketUserType.getUserTypeName())
                            .status(ticketUserType.getStatus())
                            .build())
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public List<TicketUserTypeDto> freeTextSearchByName(AdminTextSearchDto adminTextSearchDto) {
        if (adminTextSearchDto != null && !StringUtils.isBlank(adminTextSearchDto.getSearchKeyword())
                && adminTextSearchDto.getPage() != null && adminTextSearchDto.getSize() != null) {

            Pageable pageable = PageRequest.of(adminTextSearchDto.getPage(), adminTextSearchDto.getSize(),
                    Sort.by(Sort.Direction.DESC, "id"));

            List<TicketUserType> ticketUserTypeList = ticketUserTypeRepository
                    .freeTextSearchByName(adminTextSearchDto.getSearchKeyword(), pageable);

            if (ticketUserTypeList != null && !ticketUserTypeList.isEmpty()) {

                List<TicketUserTypeDto> ticketUserTypeDtoList = ticketUserTypeList.stream()
                        .map(ticketUserType -> TicketUserTypeDto.builder()
                                .userTypeId(ticketUserType.getId())
                                .userTypeName(ticketUserType.getUserTypeName())
                                .status(ticketUserType.getStatus())
                                .build())
                        .collect(Collectors.toList());

                return ticketUserTypeDtoList;
            }
        }
        return Collections.emptyList();
    }
}
