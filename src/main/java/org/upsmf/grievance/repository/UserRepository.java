package org.upsmf.grievance.repository;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.upsmf.grievance.model.User;

import java.util.Optional;

@Repository("userRepository")
public interface UserRepository extends PagingAndSortingRepository<User,Long> {

    User findByUsername(String username);
    User findByEmail(String email);
    Optional<User> findByKeycloakId(String id);

    @Query(value = "select u from users u where u.id = :id", nativeQuery = true)
    User findByUserId(@Param("id") Long id);

    @Query("SELECT u FROM User u WHERE LOWER(u.email) LIKE LOWER(CONCAT('%', ?1,'%')) "
            + "OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', ?1,'%'))"
            + "OR LOWER(u.lastname) LIKE LOWER(CONCAT('%', ?1,'%'))"
            + "OR LOWER(u.phoneNumber) LIKE LOWER(CONCAT('%', ?1,'%'))")
    Page<User> findByEmailWithPagination(String searchKeyword, Pageable pageable);
}
