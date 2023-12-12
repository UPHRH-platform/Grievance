package org.upsmf.grievance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.upsmf.grievance.model.TicketCouncil;
import org.upsmf.grievance.model.TicketUserType;

import java.util.Optional;

@Repository
public interface TicketCouncilRepository extends JpaRepository<TicketCouncil,Long> {

    Optional<TicketCouncil> findByTicketCouncilName(String ticketCouncilName);
}
