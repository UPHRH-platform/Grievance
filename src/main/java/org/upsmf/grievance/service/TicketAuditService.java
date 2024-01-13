package org.upsmf.grievance.service;

import org.upsmf.grievance.dto.TicketAuditDto;

import java.util.List;

public interface TicketAuditService {

    void saveTicketAudit(TicketAuditDto ticketAuditDto);

    List<TicketAuditDto> getTicketAuditByTicketId(Long ticketId);
}
