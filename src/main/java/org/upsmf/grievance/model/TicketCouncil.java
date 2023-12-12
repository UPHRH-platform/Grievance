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
@Table(name = "ticket_council")
public class TicketCouncil {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "ticket_council_name", unique = true)
    private String ticketCouncilName;

    @Column(name = "status")
    private Boolean status;

    @OneToMany(targetEntity = TicketDepartment.class, mappedBy = "ticketCouncilId", fetch = FetchType.EAGER)
    @Fetch(value = FetchMode.SUBSELECT)
    private List<TicketDepartment> ticketDepartments;

}
