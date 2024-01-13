package org.upsmf.grievance.dto;

import lombok.*;

import javax.validation.constraints.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketUserTypeDto {

    private Long userTypeId;

    @NotBlank(message = "Ticket user type name is required")
    private String userTypeName;

    private Boolean status;
}
