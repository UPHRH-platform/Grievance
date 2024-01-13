package org.upsmf.grievance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "ticket_department")
public class TicketDepartment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "ticket_department_name", unique = false)
    private String ticketDepartmentName;

    @Column(name = "ticket_council_id", nullable = false)
    private Long ticketCouncilId;

    @Column(name = "status")
    private Boolean status;

//    @OneToMany //(targetEntity = UserDepartment.class, mappedBy = "ticket_department_id", fetch = FetchType.EAGER)
//    @Fetch(value = FetchMode.SUBSELECT)
//    private List<User> user;
}
