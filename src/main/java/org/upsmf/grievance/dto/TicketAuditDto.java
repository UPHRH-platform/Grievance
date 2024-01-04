package org.upsmf.grievance.dto;

import lombok.*;

import javax.persistence.Column;
import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketAuditDto {
    private Long ticketId;
    private String updatedBy;
    private String updatedByUserId;
    private String createdBy;
    private String oldValue;
    private String newValue;
    private String remark;
    private String attribute;
    private Timestamp updatedTime;
}
