package org.upsmf.grievance.repository;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;
import org.upsmf.grievance.model.MailConfig;

@Repository
public interface MailConfigRepository extends PagingAndSortingRepository<MailConfig, Long> {
}
