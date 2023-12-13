package org.upsmf.grievance.service;

import org.springframework.lang.NonNull;
import org.upsmf.grievance.dto.TicketCouncilDto;
import org.upsmf.grievance.dto.TicketDepartmentDto;

import java.util.List;

public interface TicketDepartmentService {
    void save(TicketDepartmentDto ticketDepartmentDto);

    void update(TicketDepartmentDto ticketDepartmentDto);

    List<TicketDepartmentDto> findAllTicketDepartment();

    void updateTicketDepartmentActivation(TicketDepartmentDto ticketDepartmentDto);

    String getDepartmentName(@NonNull Long departmentId, @NonNull Long councilId);

    boolean validateDepartmentInCouncil(Long departmentId, Long councilId);
}
