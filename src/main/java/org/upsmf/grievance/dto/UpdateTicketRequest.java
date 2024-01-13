package org.upsmf.grievance.dto;

import lombok.*;
import org.upsmf.grievance.enums.TicketPriority;
import org.upsmf.grievance.enums.TicketStatus;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(includeFieldNames = true)
public class UpdateTicketRequest {

    private long id;
    private String requestedBy;
    private TicketStatus status;
    //AssignedTo
    private String cc;
    private TicketPriority priority;
    private String comment;
    private List<String> assigneeAttachmentURLs;
    private Boolean isJunk;
    private Boolean isNudged;
    private Boolean isOther;
    private String junkByReason;
    private String otherByReason;
    private Long ticketCouncilId;
    private Long ticketDepartmentId;
}
