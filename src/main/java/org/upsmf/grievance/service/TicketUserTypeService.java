package org.upsmf.grievance.service;

import org.upsmf.grievance.dto.AdminTextSearchDto;
import org.upsmf.grievance.dto.TicketUserTypeDto;

import java.util.List;

public interface TicketUserTypeService {
    void save(TicketUserTypeDto ticketUserTypeDto);

    void update(TicketUserTypeDto ticketUserTypeDto);

    void updateUserTypeActivation(TicketUserTypeDto ticketUserTypeDto);

    List<TicketUserTypeDto> findAllUserType();

    List<TicketUserTypeDto> freeTextSearchByName(AdminTextSearchDto adminTextSearchDto);
}
