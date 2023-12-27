package org.upsmf.grievance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.api.gax.rpc.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.upsmf.grievance.dto.CreateUserDto;
import org.upsmf.grievance.dto.UpdateUserDto;
import org.upsmf.grievance.dto.UserResponseDto;
import org.upsmf.grievance.model.OtpRequest;
import org.upsmf.grievance.model.User;

import java.util.List;
import java.util.Optional;

public interface IntegrationService {

    User addUser(User user);
    ResponseEntity<User> createUser(CreateUserDto user) throws Exception;

    ResponseEntity<String> updateUser(UpdateUserDto userDto) throws Exception;

    ResponseEntity<String> getUsers(JsonNode payload) throws Exception;

    ResponseEntity<String> getUsersFromKeycloak(JsonNode payload) throws Exception;

    ResponseEntity<String> searchUsers(JsonNode payload) throws Exception;

    ResponseEntity<UserResponseDto> getUserById(String id) throws RuntimeException;

    void assignRole(Long userId, Long roleId) throws NotFoundException;

    User activateUser(JsonNode payload) throws Exception;

    User deactivateUser(JsonNode payload) throws Exception;

    ResponseEntity<String> login(JsonNode body);

    Boolean sendMobileOTP(String name, String phoneNumber, String otp);

    List<UserResponseDto> getUserByCouncilAndDepartment(Long departmentId, Long councilId, Optional<Boolean> allUser);
}
