package org.upsmf.grievance.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.dto.AdminTextSearchDto;
import org.upsmf.grievance.dto.TicketCouncilDto;
import org.upsmf.grievance.dto.TicketDepartmentDto;
import org.upsmf.grievance.dto.TicketUserTypeDto;
import org.upsmf.grievance.exception.CustomException;
import org.upsmf.grievance.exception.DataUnavailabilityException;
import org.upsmf.grievance.exception.InvalidDataException;
import org.upsmf.grievance.model.*;
import org.upsmf.grievance.repository.TicketCouncilRepository;
import org.upsmf.grievance.repository.TicketDepartmentRepository;
import org.upsmf.grievance.repository.UserDepartmentRepository;
import org.upsmf.grievance.repository.UserRepository;
import org.upsmf.grievance.service.TicketDepartmentService;

import java.util.ArrayList;
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

    @Autowired
    private UserDepartmentRepository userDepartmentRepository;

    @Autowired
    private UserRepository userRepository;

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

        if (isDepartmentNameExistInCouncil(ticketDepartmentDto.getTicketDepartmentName(),
                ticketDepartmentDto.getTicketCouncilId())) {

            log.error("Department name is already exist in the same council");
            throw new InvalidDataException("Department name is already exist in the same council");
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

        if (isDepartmentNameExistInCouncil(ticketDepartmentDto.getTicketDepartmentName(),
                ticketDepartmentDto.getTicketCouncilId())) {

            log.error("Department name is already exist in the same council");
            throw new InvalidDataException("Department name is already exist in the same council");
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

    private boolean isDepartmentNameExistInCouncil(@NonNull String departmentName, @NonNull Long councilId) {

        List<TicketDepartment> ticketDepartmentList = ticketDepartmentRepository
                .findByTicketCouncilIdAndTicketDepartmentName(councilId, StringUtils.upperCase(departmentName));

        if (ticketDepartmentList != null && !ticketDepartmentList.isEmpty()) {
            return true;
        }

        return false;
    }

    @Override
    public List<TicketDepartmentDto> findAllTicketDepartment() {
        List<TicketDepartment> ticketDepartmentList = new ArrayList<>();

        Iterable<TicketDepartment> ticketDepartmentPage = ticketDepartmentRepository.findAll();
        ticketDepartmentPage.forEach(ticketDepartmentList::add);

        if (!ticketDepartmentList.isEmpty()) {
            return ticketDepartmentList.stream()
                    .map(ticketDepartment -> TicketDepartmentDto.builder()
                            .ticketDepartmentId(ticketDepartment.getId())
                            .ticketDepartmentName(ticketDepartment.getTicketDepartmentName())
                            .ticketCouncilId(ticketDepartment.getTicketCouncilId())
                            .ticketCouncilName(getCouncilName(ticketDepartment.getTicketCouncilId()))
                            .status(ticketDepartment.getStatus())
                            .build())
                    .sorted(((ticketDepartment1, ticketDepartment2) ->
                            ticketDepartment1.getTicketDepartmentName()
                                    .compareToIgnoreCase(ticketDepartment2.getTicketDepartmentName())))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public List<TicketDepartmentDto> freeTextSearchByName(AdminTextSearchDto adminTextSearchDto) {
        List<TicketDepartment> ticketDepartmentList = null;
        if (adminTextSearchDto != null && !StringUtils.isBlank(adminTextSearchDto.getSearchKeyword())
                && adminTextSearchDto.getCouncilId() != null && adminTextSearchDto.getCouncilId() > 0) {
            // search based on council ID and freeText
            ticketDepartmentList = ticketDepartmentRepository
                    .freeTextSearchByNameAndCouncilId(adminTextSearchDto.getSearchKeyword(),
                            adminTextSearchDto.getCouncilId());
        } else if (adminTextSearchDto != null && !StringUtils.isBlank(adminTextSearchDto.getSearchKeyword())
                && adminTextSearchDto.getCouncilId() != null && adminTextSearchDto.getDepartmentId() != null
                && adminTextSearchDto.getCouncilId() > 0 && adminTextSearchDto.getDepartmentId() > 0) {
            // search based on council ID and department ID and free text
            ticketDepartmentList = ticketDepartmentRepository.freeTextSearchByNameAndCouncilIdAndDepartmentId(adminTextSearchDto.getSearchKeyword(),
                    adminTextSearchDto.getCouncilId(), adminTextSearchDto.getDepartmentId());

        } else if (adminTextSearchDto != null && adminTextSearchDto.getCouncilId() != null && adminTextSearchDto.getDepartmentId() != null
                && adminTextSearchDto.getCouncilId() > 0 && adminTextSearchDto.getDepartmentId() > 0) {
            // search based on council ID and department ID
            ticketDepartmentList = ticketDepartmentRepository.SearchByCouncilIdAndDepartmentId(adminTextSearchDto.getCouncilId(),
                    adminTextSearchDto.getDepartmentId());
        } else if (adminTextSearchDto != null && !StringUtils.isBlank(adminTextSearchDto.getSearchKeyword())) {
            // search based on free text
            ticketDepartmentList = ticketDepartmentRepository.freeTextSearchByName(adminTextSearchDto.getSearchKeyword());
        } else if (adminTextSearchDto != null && adminTextSearchDto.getCouncilId() != null && adminTextSearchDto.getCouncilId() > 0) {
            // search based on council ID
            ticketDepartmentList = ticketDepartmentRepository.SearchByCouncilId(adminTextSearchDto.getCouncilId());
        }
        if (ticketDepartmentList != null && !ticketDepartmentList.isEmpty()) {
            // create response
            return ticketDepartmentList.stream()
                    .map(ticketDepartment -> TicketDepartmentDto.builder()
                            .ticketDepartmentId(ticketDepartment.getId())
                            .ticketDepartmentName(ticketDepartment.getTicketDepartmentName())
                            .ticketCouncilId(ticketDepartment.getTicketCouncilId())
                            .ticketCouncilName(getCouncilName(ticketDepartment.getTicketCouncilId()))
                            .status(ticketDepartment.getStatus())
                            .build())
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private String getCouncilName(Long councilId) {
        String councilName = "";
        if (councilId != null) {
            Optional<TicketCouncil> ticketCouncilOptional = ticketCouncilRepository.findById(councilId);

            if (ticketCouncilOptional.isPresent()) {
                councilName = ticketCouncilOptional.get().getTicketCouncilName();
            }
        }

        return councilName;
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

    /**
     * @param councilId
     * @return
     */
    public List<TicketDepartmentDto> getAssignedDepartment(Long councilId) {
        if (councilId != null) {
            List<UserDepartment> userDepartmentList = userDepartmentRepository.findAllByCouncilId(councilId);

            if (userDepartmentList == null || userDepartmentList.isEmpty()) {
                log.warn(">>>>>>>>>>>>> Unable to find User Department for given council Id {}", councilId);
                return Collections.emptyList();
            }

            List<User> userList = userRepository.findAllByUserDepartmentIn(userDepartmentList);

            if (userList == null || userList.isEmpty()) {
                log.warn(">>>>>>>>>>>>> Unable to find User in user department {}", userDepartmentList);
                return Collections.emptyList();
            }

            List<Long> departmentIds = userList.stream()
                    .filter(user -> user.getStatus() == 1)
                    .filter(user -> user.getUserDepartment() != null)
                    .map(user -> user.getUserDepartment().getDepartmentId())
                    .collect(Collectors.toList());

            if (departmentIds != null && !departmentIds.isEmpty()) {
                List<TicketDepartment> ticketDepartmentList = ticketDepartmentRepository.findAllById(departmentIds);

                if (ticketDepartmentList != null && !ticketDepartmentList.isEmpty()) {
                    return ticketDepartmentList.stream()
                            .filter(ticketDepartment -> Boolean.TRUE.equals(ticketDepartment.getStatus()))
                            .map(ticketDepartment -> TicketDepartmentDto.builder()
                                    .ticketDepartmentName(ticketDepartment.getTicketDepartmentName())
                                    .ticketDepartmentId(ticketDepartment.getId())
                                    .status(ticketDepartment.getStatus())
                                    .build())
                            .collect(Collectors.toList());
                }
            }
        }
        return Collections.emptyList();
    }
}
