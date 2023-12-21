package org.upsmf.grievance.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.upsmf.grievance.model.TicketDepartment;
import org.upsmf.grievance.model.TicketUserType;
import org.upsmf.grievance.model.UserDepartment;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketDepartmentRepository extends PagingAndSortingRepository<TicketDepartment, Long> {

    Optional<TicketDepartment> findByTicketDepartmentName(String ticketDepartmentName);
//    Optional<TicketDepartment> findByTicketDepartmentId(long ticketDepartmentId);

    Optional<TicketDepartment> findByIdAndTicketCouncilId(Long id, Long councilId);

    List<TicketDepartment> findByTicketCouncilIdAndTicketDepartmentName(Long councilId, String departmentName);

    @Query("SELECT d FROM TicketDepartment d WHERE d.ticketCouncilId =:councilId " +
            "AND LOWER(d.ticketDepartmentName) LIKE LOWER(CONCAT('%', :departmentName,'%'))" +
            "ORDER BY d.ticketDepartmentName ASC")
    List<TicketDepartment> freeTextSearchByNameAndCouncilId(@Param("departmentName") String departmentName,
                                                            @Param("councilId") Long councilId);

    @Query("SELECT d FROM TicketDepartment d WHERE LOWER(d.ticketDepartmentName) LIKE LOWER(CONCAT('%', ?1,'%'))")
    List<TicketDepartment> freeTextSearchByName(String departmentName, Pageable pageable);

    @Query("SELECT d FROM TicketDepartment d WHERE d.id IN :ids")
    List<TicketDepartment> findAllById(List<Long> ids);
}
