package org.upsmf.grievance.service.impl;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.dto.UpdateUserDto;
import org.upsmf.grievance.model.Ticket;
import org.upsmf.grievance.repository.es.TicketRepository;
import org.upsmf.grievance.service.EsTicketUpdateService;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class EsTicketUpdateServiceImpl implements EsTicketUpdateService {

    @Autowired
    @Qualifier("esTicketRepository")
    private TicketRepository esTicketRepository;

    @Autowired
    @Qualifier("ticketRepository")
    private org.upsmf.grievance.repository.TicketRepository ticketRepository;

//    @Async
    @Override
    public void updateEsTicketByUserId(UpdateUserDto updateUserDto) {
        if (updateUserDto != null && updateUserDto.getId() != null
                && !StringUtils.isBlank(updateUserDto.getFirstName()) && !StringUtils.isBlank(updateUserDto.getLastName())) {
            List<Ticket> ticketList = ticketRepository.findAllByAssignedToId(String.valueOf(updateUserDto.getId()));

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

    @Override
    public void updateJunkByEsTicketByUserId(UpdateUserDto updateUserDto) {
        if (updateUserDto != null && updateUserDto.getId() != null
                && !StringUtils.isBlank(updateUserDto.getFirstName()) && !StringUtils.isBlank(updateUserDto.getLastName())) {
            List<Ticket> ticketList = ticketRepository.findAllByJunkedBy(String.valueOf(updateUserDto.getId()));

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
    }
}
