package org.upsmf.grievance.dto;

import lombok.*;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class UserResponseDto implements Serializable {

    private Long id;

    private String keycloakId;

    private String firstName;

    private String lastName;

    private String username;

    private String email;

    private boolean emailVerified;

    private boolean enabled;

//    private Map<String, List<String>> attributes;

    private Map<String, Object> attributes;
}
