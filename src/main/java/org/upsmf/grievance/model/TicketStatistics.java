package org.upsmf.grievance.model;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class TicketStatistics {

    Long escalatedToMeCount;
    Long nudgedTicketCount;
    Long notAssignedTicketCount;
    Long priorityTicketCount;
    Long pendingTicketCount;
    Long resolvedTicketCount;
    Long junkTicketCount;
}
