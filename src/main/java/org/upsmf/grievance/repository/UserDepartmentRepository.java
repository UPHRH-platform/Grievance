package org.upsmf.grievance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.upsmf.grievance.model.UserDepartment;

import java.util.List;

@Repository
public interface UserDepartmentRepository extends JpaRepository<UserDepartment, Long> {

//    UserDepartment findByUserId(long userId);

    void deleteById(long userId);

    List<UserDepartment> findAllByDepartmentName(String departmentName);

    List<UserDepartment> findAllByDepartmentId(Long departmentId);

    List<UserDepartment> findAllByDepartmentIdAndCouncilId(Long departmentId, Long councilId);

}
