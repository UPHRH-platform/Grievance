package org.upsmf.grievance.model;

import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String keycloakId;

    private String firstName;

    private String lastname;

    @Column(nullable=false)
    private String username;

    @Column(nullable=false, unique=true)
    private String email;

    private boolean emailVerified;

    private int status;

    private String phoneNumber;

    private String[] roles;

    /*@ElementCollection
    private List<String> roles;*/

//    @OneToMany(targetEntity = UserDepartment.class, mappedBy = "userId", fetch = FetchType.EAGER)
//    @Fetch(value = FetchMode.SUBSELECT)
//    private List<UserDepartment> userDepartment;

    @ManyToOne(targetEntity = UserDepartment.class, fetch = FetchType.EAGER)
    private UserDepartment userDepartment;

    @OneToMany(fetch = FetchType.EAGER)
    @Fetch(value = FetchMode.SUBSELECT)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
   private Set<Role> rolesList = new HashSet<>();

}
