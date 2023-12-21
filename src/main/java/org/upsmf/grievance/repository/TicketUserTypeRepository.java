package org.upsmf.grievance.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.upsmf.grievance.model.Role;
import org.upsmf.grievance.model.TicketUserType;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketUserTypeRepository extends PagingAndSortingRepository<TicketUserType,Long> {

    Optional<TicketUserType> findByUserTypeName(String userTypeName);

    @Query("SELECT ut FROM TicketUserType ut WHERE LOWER(ut.userTypeName) LIKE LOWER(CONCAT('%', ?1,'%'))")
    List<TicketUserType> freeTextSearchByName(String userTypeName, Pageable pageable);
}
