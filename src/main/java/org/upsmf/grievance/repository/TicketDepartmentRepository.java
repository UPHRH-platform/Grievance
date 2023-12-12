package org.upsmf.grievance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.upsmf.grievance.model.TicketDepartment;
import org.upsmf.grievance.model.UserDepartment;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketDepartmentRepository extends JpaRepository<TicketDepartment, Long> {

    Optional<TicketDepartment> findByTicketDepartmentName(String ticketDepartmentName);
//    Optional<TicketDepartment> findByTicketDepartmentId(long ticketDepartmentId);

    Optional<TicketDepartment> findByIdAndTicketCouncilId(Long id, Long councilId);

}
