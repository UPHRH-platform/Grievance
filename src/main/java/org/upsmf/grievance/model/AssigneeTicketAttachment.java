package org.upsmf.grievance.model;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name ="ticket_assignee_attachment")
@Data
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class AssigneeTicketAttachment {

    @Id()
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Lob
    @Column(name = "attachment_url", nullable = false)
    private String attachment_url;
}
