package org.upsmf.grievance.repository;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.upsmf.grievance.model.Ticket;

import java.util.List;

@Repository("ticketRepository")
public interface TicketRepository extends CrudRepository<org.upsmf.grievance.model.Ticket, Long> {

    List<Ticket> findAllByAssignedToId(String assignId);

    List<Ticket> findAllByJunkedBy(String junkBy);
}
