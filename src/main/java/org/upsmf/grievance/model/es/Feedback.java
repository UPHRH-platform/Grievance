package org.upsmf.grievance.model.es;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;

@Document(indexName = "feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Feedback {

    @Id
    private String id;

    @Field(name = "first_name")
    private String firstName;

    @Field(name = "last_name")
    private String lastName;

    @Field(name = "phone")
    private String phone;

    @Field(name = "email")
    private String email;

    @Field(name = "rating")
    private Integer rating;

    @Field(name = "comment")
    private String comment;

    @Field(name = "ticket_id")
    private String ticketId;
}
