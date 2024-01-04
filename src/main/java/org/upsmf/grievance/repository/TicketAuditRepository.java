package org.upsmf.grievance.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.upsmf.grievance.model.TicketAudit;
import org.upsmf.grievance.model.TicketUserType;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketAuditRepository extends PagingAndSortingRepository<TicketAudit,Long> {

    List<TicketAudit> findByTicketId(Long ticketId);
    @Query("SELECT ut FROM TicketUserType ut WHERE LOWER(ut.userTypeName) LIKE LOWER(CONCAT('%', ?1,'%'))" +
            "ORDER BY ut.userTypeName ASC")
    List<TicketUserType> freeTextSearchByName(String userTypeName);
}
