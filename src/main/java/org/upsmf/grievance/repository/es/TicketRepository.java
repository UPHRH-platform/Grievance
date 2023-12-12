package org.upsmf.grievance.repository.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.upsmf.grievance.model.es.Ticket;

import java.util.List;
import java.util.Optional;

@Repository("esTicketRepository")
public interface TicketRepository extends ElasticsearchRepository<Ticket, String> {
    @Query("{'ticket_id': ?0}")
    Optional<Ticket> findOneByTicketId(long id);

    List<Ticket> findAllByTicketIdIn(List<Long> ticketIdList);

}
