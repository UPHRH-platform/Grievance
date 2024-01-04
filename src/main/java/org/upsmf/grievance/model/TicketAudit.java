package org.upsmf.grievance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.sql.Timestamp;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "ticket_audit")
public class TicketAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "ticket_id")
    private Long ticketId;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_by_user_id")
    private String updatedByUserId;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "attribute")
    private String attribute;

    @Column(name = "remark")
    private String remark;

    @Column(name = "updated_time")
    private Timestamp updatedTime;
}
