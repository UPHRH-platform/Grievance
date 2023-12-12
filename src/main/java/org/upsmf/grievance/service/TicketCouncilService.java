package org.upsmf.grievance.service;

import org.springframework.lang.NonNull;
import org.upsmf.grievance.dto.TicketCouncilDto;
import org.upsmf.grievance.dto.TicketUserTypeDto;

import java.util.List;

public interface TicketCouncilService {
    void save(TicketCouncilDto ticketCouncilDto);

    void update(TicketCouncilDto ticketCouncilDto);

    List<TicketCouncilDto> findAllCouncil();

    void updateTicketCouncilActivation(TicketCouncilDto ticketCouncilDto);

    String getCouncilName(@NonNull Long councilId);
}
