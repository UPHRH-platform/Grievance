package org.upsmf.grievance.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.upsmf.grievance.model.TicketCouncil;
import org.upsmf.grievance.model.TicketUserType;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketCouncilRepository extends PagingAndSortingRepository<TicketCouncil,Long> {

    Optional<TicketCouncil> findByTicketCouncilName(String ticketCouncilName);

    @Query("SELECT c FROM TicketCouncil c WHERE LOWER(c.ticketCouncilName) LIKE LOWER(CONCAT('%', ?1,'%')) " +
            "ORDER BY c.ticketCouncilName ASC")
    List<TicketCouncil> freeTextSearchByName(String councilName);
}
