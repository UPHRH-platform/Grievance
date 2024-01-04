package org.upsmf.grievance.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.upsmf.grievance.dto.TicketAuditDto;
import org.upsmf.grievance.model.TicketAudit;
import org.upsmf.grievance.repository.TicketAuditRepository;
import org.upsmf.grievance.service.TicketAuditService;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TicketAuditServiceImpl implements TicketAuditService {

    @Autowired
    private TicketAuditRepository ticketAuditRepository;

    /**
     * @param ticketAuditDto
     */
    @Override
    public void saveTicketAudit(TicketAuditDto ticketAuditDto) {
        try {
            TicketAudit ticketAudit = TicketAudit.builder()
                    .ticketId(ticketAuditDto.getTicketId())
                    .createdBy(ticketAuditDto.getCreatedBy())
                    .updatedBy(ticketAuditDto.getUpdatedBy())
                    .updatedByUserId(ticketAuditDto.getUpdatedByUserId())
                    .oldValue(ticketAuditDto.getOldValue())
                    .newValue(ticketAuditDto.getNewValue())
                    .attribute(ticketAuditDto.getAttribute())
                    .remark(ticketAuditDto.getRemark())
                    .updatedTime(ticketAuditDto.getUpdatedTime())
                    .build();

            ticketAuditRepository.save(ticketAudit);
        } catch (Exception e) {
            log.error("Error while tyring to save ticket audit", e);
        }
    }

    /**
     * @param ticketId
     * @return
     */
    @Override
    public List<TicketAuditDto> getTicketAuditByTicketId(Long ticketId) {
        if (ticketId != null) {
            List<TicketAudit> ticketAudits = ticketAuditRepository.findByTicketId(ticketId);

            if (ticketAudits != null && !ticketAudits.isEmpty()) {
                return ticketAudits.stream()
                        .map(ticketAudit -> TicketAuditDto.builder()
                                .ticketId(ticketAudit.getTicketId())
                                .attribute(ticketAudit.getAttribute())
                                .oldValue(ticketAudit.getOldValue())
                                .newValue(ticketAudit.getNewValue())
                                .createdBy(ticketAudit.getCreatedBy())
                                .updatedBy(ticketAudit.getUpdatedBy())
                                .updatedByUserId(ticketAudit.getUpdatedByUserId())
                                .updatedTime(ticketAudit.getUpdatedTime())
                                .remark(ticketAudit.getRemark())
                                .build())
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
