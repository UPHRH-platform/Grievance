package org.upsmf.grievance.service.impl;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.dto.UpdateUserDto;
import org.upsmf.grievance.model.Ticket;
import org.upsmf.grievance.model.User;
import org.upsmf.grievance.model.UserDepartment;
import org.upsmf.grievance.repository.UserDepartmentRepository;
import org.upsmf.grievance.repository.UserRepository;
import org.upsmf.grievance.repository.es.TicketRepository;
import org.upsmf.grievance.service.EsTicketUpdateService;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
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

//    @Async
    @Override
    public void updateEsTicketByUserId(UpdateUserDto updateUserDto) {
        if (updateUserDto != null && updateUserDto.getId() != null
                && !StringUtils.isBlank(updateUserDto.getFirstName()) && !StringUtils.isBlank(updateUserDto.getLastName())) {
            List<Ticket> ticketList = ticketRepository.findAllByAssignedToId(String.valueOf(updateUserDto.getId()));

            Optional<User> userOptional = userRepository.findById(updateUserDto.getId());
            if (userOptional.isPresent()) {
                UserDepartment userDepartment = userOptional.get().getUserDepartment();

                if (userDepartment != null && "OTHER".equalsIgnoreCase(userDepartment.getDepartmentName())
                        && "OTHER".equalsIgnoreCase(userDepartment.getCouncilName())) {

                    ticketList = ticketRepository.findAllByJunkedBy("-1");
                }

                if (ticketList != null && !ticketList.isEmpty()) {
                    List<Long> ids = ticketList.stream().map(Ticket::getId).collect(Collectors.toList());

                    List<org.upsmf.grievance.model.es.Ticket> esTicketList = esTicketRepository.findAllByTicketIdIn(ids);

                    if (esTicketList != null && !esTicketList.isEmpty()) {
                        for (org.upsmf.grievance.model.es.Ticket ticket : esTicketList) {
                            String updatedName = updateUserDto.getFirstName() + " " + updateUserDto.getLastName();
                            ticket.setAssignedToName(updatedName);

                            if (!StringUtils.isBlank(ticket.getJunkedBy())) {
                                ticket.setJunkedBy(updatedName);
                            }
                        }

                        esTicketRepository.saveAll(esTicketList);
                    }
                }
            }
        }
    }

    @Override
    public void updateJunkByEsTicketByUserId(UpdateUserDto updateUserDto) {
        if (updateUserDto != null && updateUserDto.getId() != null
                && !StringUtils.isBlank(updateUserDto.getFirstName()) && !StringUtils.isBlank(updateUserDto.getLastName())) {

            List<Ticket> ticketList = ticketRepository.findAllByJunkedBy(String.valueOf(updateUserDto.getId()));

            Optional<User> userOptional = userRepository.findById(updateUserDto.getId());
            if (userOptional.isPresent()) {
                UserDepartment userDepartment = userOptional.get().getUserDepartment();

                if (userDepartment != null && "OTHER".equalsIgnoreCase(userDepartment.getDepartmentName())
                        && "OTHER".equalsIgnoreCase(userDepartment.getCouncilName())) {

                    ticketList = ticketRepository.findAllByJunkedBy("-1");
                }

                if (ticketList != null && !ticketList.isEmpty()) {
                    List<Long> ids = ticketList.stream().map(Ticket::getId).collect(Collectors.toList());

                    List<org.upsmf.grievance.model.es.Ticket> esTicketList = esTicketRepository.findAllByTicketIdIn(ids);

                    if (esTicketList != null && !esTicketList.isEmpty()) {
                        for (org.upsmf.grievance.model.es.Ticket ticket : esTicketList) {
                            String updatedName = updateUserDto.getFirstName() + " " + updateUserDto.getLastName();
                            ticket.setJunkedByName(updatedName);
                        }

                        esTicketRepository.saveAll(esTicketList);
                    }
                }
            }

//            List<Ticket> ticketList = ticketRepository.findAllByJunkedBy(String.valueOf(updateUserDto.getId()));


        }
    }
}
