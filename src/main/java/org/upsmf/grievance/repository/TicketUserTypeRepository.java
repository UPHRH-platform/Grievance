package org.upsmf.grievance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.upsmf.grievance.model.Role;
import org.upsmf.grievance.model.TicketUserType;

import java.util.Optional;

@Repository
public interface TicketUserTypeRepository extends JpaRepository<TicketUserType,Long> {

    Optional<TicketUserType> findByUserTypeName(String userTypeName);
}
