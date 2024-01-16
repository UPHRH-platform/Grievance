package org.upsmf.grievance.repository.es;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.upsmf.grievance.model.es.Feedback;

import java.util.List;

@Repository
public interface FeedbackRepository extends ElasticsearchRepository<Feedback, String> {

    @Query("{'ticket_id': ?0}")
    List<Feedback> findAllByTicketId(String id);
}
