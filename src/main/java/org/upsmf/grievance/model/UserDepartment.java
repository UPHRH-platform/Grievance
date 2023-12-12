package org.upsmf.grievance.model;

import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name = "userDepartment")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class UserDepartment {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(name = "department_Id")
    private Long departmentId;

    @Column(name = "department_name", nullable = false)
    private String departmentName;

    @Column(name = "council_Id")
    private Long councilId;

    @Column(name = "council_name")
    private String councilName;

}
