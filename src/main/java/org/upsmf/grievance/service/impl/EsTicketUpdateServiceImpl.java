package org.upsmf.grievance.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.dto.UpdateUserDto;
import org.upsmf.grievance.model.Ticket;
import org.upsmf.grievance.model.User;
import org.upsmf.grievance.model.UserDepartment;
import org.upsmf.grievance.repository.UserDepartmentRepository;
import org.upsmf.grievance.repository.UserRepository;
import org.upsmf.grievance.repository.es.TicketRepository;
import org.upsmf.grievance.service.EsTicketUpdateService;
import org.upsmf.grievance.service.SearchService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EsTicketUpdateServiceImpl implements EsTicketUpdateService {

    @Autowired
    @Qualifier("esTicketRepository")
    private TicketRepository esTicketRepository;

    @Autowired
    @Qualifier("ticketRepository")
    private org.upsmf.grievance.repository.TicketRepository ticketRepository;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserDepartmentRepository userDepartmentRepository;

    @Lazy
    @Autowired
    private SearchService searchService;

//    @Async
    @Override
    public void updateEsTicketByUserId(UpdateUserDto updateUserDto) {
        try {
            if (updateUserDto != null && updateUserDto.getId() != null
                    && !StringUtils.isBlank(updateUserDto.getFirstName()) && !StringUtils.isBlank(updateUserDto.getLastName())) {
                List<Ticket> ticketList = ticketRepository.findAllByAssignedToId(String.valueOf(updateUserDto.getId()));
                if(ticketList == null) {
                    ticketList = new ArrayList<>();
                }
                List<Ticket> allJunkedTicketById = ticketRepository.findAllByJunkedBy(String.valueOf(updateUserDto.getId()));
                if(allJunkedTicketById != null && !allJunkedTicketById.isEmpty()){
                    ticketList.addAll(allJunkedTicketById);
                }

                Optional<User> userOptional = userRepository.findById(updateUserDto.getId());
                if (userOptional.isPresent()) {
                    UserDepartment userDepartment = userOptional.get().getUserDepartment();

                    if (userDepartment != null && "OTHER".equalsIgnoreCase(userDepartment.getDepartmentName())
                            && "OTHER".equalsIgnoreCase(userDepartment.getCouncilName())) {

                        List<Ticket> allJunkedTicketList = ticketRepository.findAllByJunkedBy("-1");
                        if(allJunkedTicketList != null && !allJunkedTicketList.isEmpty()){
                            ticketList.addAll(allJunkedTicketList);
                        }
                    }

                    if (ticketList != null && !ticketList.isEmpty()) {
                        List<Long> ids = ticketList.stream().map(Ticket::getId).collect(Collectors.toList());

                        log.info(">>>>>>>>>>>>>>>>>>>>>>>>>> assignedTo id list: " + ids);

//                        List<org.upsmf.grievance.model.es.Ticket> esTicketList = esTicketRepository.findByTicketIdIn(ids);
                        List<org.upsmf.grievance.model.es.Ticket> esTicketList = searchService.getAllTicketByIdList(ids);

                        if (esTicketList != null && !esTicketList.isEmpty()) {
                            for (org.upsmf.grievance.model.es.Ticket ticket : esTicketList) {
                                String updatedName = updateUserDto.getFirstName() + " " + updateUserDto.getLastName();
                                ticket.setAssignedToName(updatedName);

                                if (!StringUtils.isBlank(ticket.getJunkedBy())) {
                                    ticket.setJunkedBy(updatedName);
                                }
                            }

                            esTicketRepository.saveAll(esTicketList);
                        } else {
                            log.warn(">>>>>>>>>>> Marked Other: Unable to find any ticket to update for ids {}", ids);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error while updating assignedTo ticket info for profile update");
            e.printStackTrace();
        }
    }

    @Override
    public void updateJunkByEsTicketByUserId(UpdateUserDto updateUserDto) {
        try {
            if (updateUserDto != null && updateUserDto.getId() != null
                    && !StringUtils.isBlank(updateUserDto.getFirstName()) && !StringUtils.isBlank(updateUserDto.getLastName())) {

                List<Ticket> ticketList = ticketRepository.findAllByJunkedBy(String.valueOf(updateUserDto.getId()));
                if(ticketList == null) {
                    ticketList = new ArrayList<>();
                }
                List<Ticket> allJunkedTicketById = ticketRepository.findAllByJunkedBy(String.valueOf(updateUserDto.getId()));
                if(allJunkedTicketById != null && !allJunkedTicketById.isEmpty()){
                    ticketList.addAll(allJunkedTicketById);
                }

                Optional<User> userOptional = userRepository.findById(updateUserDto.getId());
                if (userOptional.isPresent()) {
                    UserDepartment userDepartment = userOptional.get().getUserDepartment();

                    if (userDepartment != null && "OTHER".equalsIgnoreCase(userDepartment.getDepartmentName())
                            && "OTHER".equalsIgnoreCase(userDepartment.getCouncilName())) {
                        // find all tickets marked by -1
                        List<Ticket> allJunkedTicketList = ticketRepository.findAllByJunkedBy("-1");
                        if(allJunkedTicketList != null && !allJunkedTicketList.isEmpty()){
                            ticketList.addAll(allJunkedTicketList);
                        }
                    }

                    if (ticketList != null && !ticketList.isEmpty()) {
                        List<Long> ids = ticketList.stream().map(Ticket::getId).collect(Collectors.toList());

                        log.info(">>>>>>>>>>>>>>>>>>>>>>>>>> junked id list: " + ids);

//                        List<org.upsmf.grievance.model.es.Ticket> esTicketList = esTicketRepository.findByTicketIdIn(ids);
                        List<org.upsmf.grievance.model.es.Ticket> esTicketList = searchService.getAllTicketByIdList(ids);
                        log.info(">>>>>>>>>>>>>>>>>>>>>>>>>> es ticket list: " + esTicketList);
                        if (esTicketList != null && !esTicketList.isEmpty()) {
                            for (org.upsmf.grievance.model.es.Ticket ticket : esTicketList) {
                                // change to omit failure due to old ticket structure
                                try{
                                    String updatedName = updateUserDto.getFirstName() + " " + updateUserDto.getLastName();
                                    ticket.setJunkedByName(updatedName);
                                    esTicketRepository.save(ticket);
                                } catch (Exception e){
                                    log.error("Error in updating name in ticket - {}", e.getLocalizedMessage());
                                }
                            }
                        } else {
                            log.warn(">>>>>>>>>>> Marked Junked: Unable to find any ticket to update for ids {}", ids);
                        }
                    }
                }
            }
        }catch (Exception e) {
            log.error("Error while updating junked by ticket info for profile update");
            e.printStackTrace();
        }
    }
}
