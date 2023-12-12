package org.upsmf.grievance.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TicketDepartmentDto {

    private Long ticketDepartmentId;

    @NotBlank(message = "Ticket department name is required")
    private String ticketDepartmentName;

    @NotNull(message = "Ticket council id is required")
    private Long ticketCouncilId;

    private Boolean status;
}
