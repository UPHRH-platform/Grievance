package org.upsmf.grievance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketCouncilDto {

    private Long ticketCouncilId;

    @NotBlank(message = "Ticket council name is required")
    private String ticketCouncilName;

    private Boolean status;

    private List<TicketDepartmentDto> ticketDepartmentDtoList;
}
