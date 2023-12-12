package org.upsmf.grievance.model;

import lombok.*;

import javax.persistence.*;

@Entity
@Table(name ="ticket_raiser_attachments")
@Data
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class RaiserTicketAttachment {

    @Id()
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_id", nullable = false)
    private Long ticketId;

    @Lob
    @Column(name = "attachment_url", nullable = false)
    private String attachment_url;
}
