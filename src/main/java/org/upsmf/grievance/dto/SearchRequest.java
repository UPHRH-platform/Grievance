package org.upsmf.grievance.dto;

import lombok.*;
import org.upsmf.grievance.enums.TicketPriority;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@Builder
public class SearchRequest {

    private String searchKeyword;

    private Map<String,Object> filter;

    private List<String> status;

    private Long cc;

    private List<Integer> ccList;

    private SearchDateRange date;

    private Boolean isJunk;

    private Boolean isEscalated;

    private TicketPriority priority;

    private int page;

    private int size;

    private Map<String, String> sort;

}
