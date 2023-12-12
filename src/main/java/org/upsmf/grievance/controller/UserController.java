package org.upsmf.grievance.controller;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.upsmf.grievance.dto.CreateUserDto;
import org.upsmf.grievance.dto.UpdateUserDto;
import org.upsmf.grievance.dto.UserResponseDto;
import org.upsmf.grievance.exception.CustomException;
import org.upsmf.grievance.exception.UserException;
import org.upsmf.grievance.model.UserDepartment;
import org.upsmf.grievance.model.User;
import org.upsmf.grievance.service.IntegrationService;
import org.upsmf.grievance.util.ErrorCode;

import java.util.*;

@Slf4j
@Controller
@RequestMapping("/api/user")
public class UserController {


    @Autowired
    private IntegrationService integrationService;


    @PostMapping("/assignRole")
    public ResponseEntity<String> assignRole(@RequestParam Long userId, @RequestParam Long roleId) {
        try {
            integrationService.assignRole(userId, roleId);
            return ResponseEntity.ok("Role assigned successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getLocalizedMessage());
        }
    }

    @PostMapping("/create-user")
    public ResponseEntity createUser(@RequestBody CreateUserDto userRequest) {
        try {
            ResponseEntity<User> user = integrationService.createUser(userRequest);
            if (user.getStatusCode() == HttpStatus.OK) {
                return createUserResponse(user.getBody());
            } else {
                log.error("Error unable to create user - doesn't receive created user");
                throw new UserException("Unable to create user", ErrorCode.USER_003, "Error while trying to create user");
            }
        } catch (CustomException e) {
            log.error("Error in while creating user - at controller");
            throw new UserException(e.getMessage(), ErrorCode.USER_003, "Error while trying to create user");
        } catch (Exception e) {
            log.error("Error while creating user", e);
            return ResponseEntity.internalServerError().body(e.getLocalizedMessage());
        }
    }

    private ResponseEntity<UserResponseDto> createUserResponse(User user) {
//        Map<String, List<String>> attributes = new HashMap<>();
//        attributes.put("Role", Arrays.asList(body.getRoles()));
//        List<String> department = new ArrayList<>();
//        if(body.getUserDepartment() != null && !body.getUserDepartment().isEmpty()) {
//            for(UserDepartment depart : body.getUserDepartment()) {
//                department.add(depart.getDepartmentName());
//            }
//        }
//        attributes.put("departmentName", department);
//        attributes.put("phoneNumber", Arrays.asList(body.getPhoneNumber()));



        Map<String, Object> attributeMap = new HashMap<>();
        attributeMap.put("phoneNumber", user.getPhoneNumber());
        attributeMap.put("role", user.getRoles());

        UserDepartment userDepartment = user.getUserDepartment();

        if (userDepartment != null) {
            attributeMap.put("departmentId", userDepartment.getDepartmentId());
            attributeMap.put("departmentName", userDepartment.getDepartmentName());
            attributeMap.put("councilId", userDepartment.getCouncilId());
            attributeMap.put("councilName", userDepartment.getCouncilName());
        }

        boolean enabled = false;
        if(user.getStatus() == 1) {
            enabled = true;
        }
        UserResponseDto userResponseDto = UserResponseDto.builder()
                .email(user.getEmail())
                .emailVerified(user.isEmailVerified())
                .enabled(enabled)
                .firstName(user.getFirstName())
                .lastName(user.getLastname())
                .id(user.getId())
                .keycloakId(user.getKeycloakId())
                .username(user.getUsername())
                .attributes(attributeMap)
                .build();
        return ResponseEntity.ok().body(userResponseDto);
    }

    @PostMapping("/user")
    public User addUser(@RequestBody User userRequest) {
        try {
            return integrationService.addUser(userRequest);
        } catch (Exception e) {
            throw new RuntimeException(e.getLocalizedMessage());
        }
    }

    @PostMapping("/update-user")
    public ResponseEntity<String> updateUser(@RequestBody UpdateUserDto userDto) {
        try {
            integrationService.updateUser(userDto);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getLocalizedMessage());
        }
        return ResponseEntity.ok("user updated successfully");
    }

    @GetMapping("/search")
    public ResponseEntity<String> search(@RequestBody JsonNode payload) {
        try {
            return integrationService.getUsers(payload);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getLocalizedMessage());
        }
    }

    @PostMapping("/users")
    public ResponseEntity<String> getUsers(@RequestBody JsonNode payload) {
        try {
            return integrationService.getUsers(payload);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getLocalizedMessage());
        }
    }

    @GetMapping("/info")
    public ResponseEntity getUsersById(@RequestParam("id") String id) throws RuntimeException{
        try {
            return integrationService.getUserById(id);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(e.getLocalizedMessage());
        }
    }

    @PostMapping("/activate")
    public ResponseEntity activateUser(@RequestBody JsonNode payload) {
        try {
            User user = integrationService.activateUser(payload);
            return createUserResponse(user);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error in activating user.");
        }
    }

    @PostMapping("/deactivate")
    public ResponseEntity deactivateUser(@RequestBody JsonNode payload) {
        try {
            User user = integrationService.deactivateUser(payload);
            return createUserResponse(user);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body("Error in de-activating user.");
        }
    }

    @PostMapping("/login")
    public ResponseEntity<String> loginUser(@RequestBody JsonNode body) {
        try {
            return integrationService.login(body);
        } catch (Exception e) {
            throw new RuntimeException(e.getLocalizedMessage());
        }
    }

}
