package org.upsmf.grievance.model.es;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.upsmf.grievance.enums.RequesterType;
import org.upsmf.grievance.enums.TicketPriority;
import org.upsmf.grievance.enums.TicketStatus;

import javax.persistence.Column;

@Document(indexName = "ticket", createIndex = false)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString(includeFieldNames = true)
public class Ticket {

    @Id
    private String id;

    @Field(name = "ticket_id")
    private Long ticketId;

    @Field(name = "requester_first_name")
    private String firstName;

    @Field(name = "requester_last_name")
    private String lastName;

    @Field(name = "requester_phone")
    private String phone;

    @Field(name = "requester_email")
    private String email;

    @Field(name = "owner_email")
    private String ownerEmail;

    @Field(name = "assigned_to_id")
    private String assignedToId;

    @Field(name = "assigned_to_name")
    private String assignedToName;

    @Field(name = "description")
    private String description;

    @Field(name = "is_junk")
    private Boolean junk = false;

    @Field(name = "is_other")
    private Boolean other = false;

    @Field(name = "junked_by")
    private String junkedBy;

    @Field(name = "junked_by_name")
    private String junkedByName;

    @Field(name = "created_date")
    private String createdDate;

    @Field(name = "updated_date")
    private String updatedDate;

    @Field(name = "created_date_ts")
    private Long createdDateTS;

    @Field(name = "updated_date_ts")
    private Long updatedDateTS;

    @Field(name = "last_updated_by")
    private String lastUpdatedBy;

    @Field(name = "is_escalated")
    private Boolean escalated;

    @Field(name = "escalated_date")
    private String escalatedDate;

    @Field(name = "escalated_date_ts")
    private Long escalatedDateTS;

    @Field(name = "escalated_to")
    private String escalatedTo;

    @Field(name = "status")
    private TicketStatus status = TicketStatus.OPEN;

    @Field(name = "request_type")
    private String requestType;

    @Field(name = "priority")
    private TicketPriority priority = TicketPriority.LOW;

    // if the ticket is escalated by system, value will be -1 else superAdmin ID
    @Field(name = "escalated_by")
    private String escalatedBy = "-1";

    @Field(name = "rating")
    private Long rating = 0L;

    @Field(name ="is_escalated_to_admin")
    private Boolean escalatedToAdmin;

    @Field(name = "reminder_counter")
    private Long reminderCounter;

    @Field(name = "ticket_user_type_id")
    private Long ticketUserTypeId;

    @Field(name = "ticket_user_type_name")
    private String ticketUserTypeName;

    @Field(name = "ticket_council_id")
    private Long ticketCouncilId;

    @Field(name = "ticket_council_name")
    private String ticketCouncilName;

    @Field(name = "ticket_department_id")
    private Long ticketDepartmentId;

    @Field(name = "ticket_department_name")
    private String ticketDepartmentName;

}
