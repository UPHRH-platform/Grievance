package org.upsmf.grievance.service.impl;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.gax.rpc.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.upsmf.grievance.dto.CreateUserDto;
import org.upsmf.grievance.dto.UpdateUserDto;
import org.upsmf.grievance.dto.UserCredentials;
import org.upsmf.grievance.dto.UserResponseDto;
import org.upsmf.grievance.enums.Department;
import org.upsmf.grievance.exception.*;
import org.upsmf.grievance.exception.runtime.InvalidRequestException;
import org.upsmf.grievance.model.*;
import org.upsmf.grievance.repository.UserDepartmentRepository;
import org.upsmf.grievance.repository.RoleRepository;
import org.upsmf.grievance.repository.UserRepository;
import org.upsmf.grievance.repository.UserRoleRepository;
import org.upsmf.grievance.service.*;
import org.upsmf.grievance.util.DateUtil;
import org.upsmf.grievance.util.ErrorCode;

import javax.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class IntegrationServiceImpl implements IntegrationService {

    public static final String ROLE = "Role";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Value("${api.user.createUrl}")
    private String createUserUrl;

    @Value("${api.user.updateUrl}")
    private String updateUserUrl;

    @Value("${api.user.searchUrl}")
    private String apiUrl;
    @Value("${api.user.searchUserUrl}")
    private String searchUserUrl;

    @Value("${api.user.listUrl}")
    private String listUserUrl;

    @Value("${api.user.activeUserUrl}")
    private String activeUserUrl;

    @Value("${api.user.deactivateUserUrl}")
    private String deactivateUserUrl;

    @Value("${api.user.loginUserUrl}")
    private String loginUserUrl;
    @Value("${mobile.sms.uri}")
    private String mobileSmsUri;

    @Value("${mobile.sms.apikey}")
    private String mobileSmsApiKey;

    @Value("${mobile.sms.senderid}")
    private String mobileSmsSenderId;

    @Value("${mobile.sms.channel}")
    private String mobileSmsChannel;

    @Value("${mobile.sms.DCS}")
    private String mobileSmsDCS;

    @Value("${mobile.sms.flashsms}")
    private String mobileSmsFlashsms;

    @Value("${mobile.sms.text}")
    private String mobileSmsText;

    @Value("${mobile.sms.route}")
    private String mobileSmsRoute;

    @Value("${mobile.sms.DLTTemplateId}")
    private String mobileSmsDLTTemplateId;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${otp.expiration.minutes}")
    private int otpExpirationMinutes;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserDepartmentRepository userDepartmentRepository;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TicketDepartmentService ticketDepartmentService;

    @Autowired
    private TicketCouncilService ticketCouncilService;

    @Autowired
    private EsTicketUpdateService esTicketUpdateService;

    @Autowired
    private EmailService emailService;

    @Override
    public User addUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public ResponseEntity<User> createUser(CreateUserDto user) throws Exception {
        validateUserPayload(user);
        checkUserInCenterUM(user.getUsername());
        // check for userDepartment
        String module = user.getAttributes().get("module");
        if (module != null) {
            user.getAttributes().put("module", module);
        } else {
            user.getAttributes().put("module", "grievance");
        }

        Map<String, String> attributeMap = processUserDepartmentAndCouncil(user.getAttributes());

        String generatePassword = validateAndCreateDefaultPassword(user);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        JsonNode jsonNodeObject = mapper.convertValue(user, JsonNode.class);
        JsonNode root = mapper.createObjectNode();
        ((ObjectNode) root).put("request", jsonNodeObject);
        log.info("Create user Request - {}", root);
        ResponseEntity response = restTemplate.exchange(createUserUrl, HttpMethod.POST,
                new HttpEntity<>(root, headers), String.class);
        log.info("Create user Response - {}", response);
        if (response.getStatusCode() == HttpStatus.OK) {
            String userContent = response.getBody().toString();
            JsonNode responseNode = null;
            try {
                responseNode = mapper.readTree(userContent);
            } catch (JsonParseException jp) {
                log.error("Error while parsing success response", jp);
            }
            if (responseNode != null) {
                if (responseNode.has("errorMessage")) {
                    throw new RuntimeException(responseNode.get("errorMessage").textValue());
                }
            }
            ObjectNode requestNode = mapper.createObjectNode();
            requestNode.put("userName", userContent);
            JsonNode payload = requestNode;
            JsonNode payloadRoot = mapper.createObjectNode();
            ((ObjectNode) payloadRoot).put("request", payload);
            ResponseEntity<String> getUsersResponse = searchUsers(payloadRoot);
            if (getUsersResponse.getStatusCode() == HttpStatus.OK) {
                String getUsersResponseBody = getUsersResponse.getBody();
                JsonNode getUsersJsonNode = mapper.readTree(getUsersResponseBody);
                if (getUsersJsonNode.size() > 0) {
                    JsonNode userContentData = getUsersJsonNode;
                    User newUser = createUserWithApiResponse(userContentData);

                    // create user userDepartment mapping
                    if (attributeMap.get("departmentId") != null) {
                        UserDepartment userDepartment = UserDepartment.builder()
                                .departmentId(Long.valueOf(attributeMap.get("departmentId")))
                                .departmentName(attributeMap.get("departmentName"))
                                .councilId(Long.valueOf(attributeMap.get("councilId")))
                                .councilName(attributeMap.get("councilName"))
//                            .userId(savedUser.getId())
                                .build();

                        UserDepartment savedUserDepartment = userDepartmentRepository.save(userDepartment);
                        newUser.setUserDepartment(savedUserDepartment);
                    }

                    User savedUser = userRepository.save(newUser);
                    // create user role mapping
                    createUserRoleMapping(user, savedUser);

                    // send mail with password
//                    sendCreateUserEmail(savedUser.getEmail(), savedUser.getUsername(), generatePassword);
                    emailService.sendUserCreationMail(savedUser, generatePassword);
                    return new ResponseEntity<>(savedUser, HttpStatus.OK);
                }
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            } else {
                // Handle error cases here
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    /**
     * @param attributeMap
     * @return
     */
    private Map<String, String> processUserDepartmentAndCouncil(Map<String, String> attributeMap) {
        if (attributeMap != null) {
            if (( attributeMap.containsKey("departmentId") && !attributeMap.containsKey("councilId") )
                    || ( !attributeMap.containsKey("departmentId") && attributeMap.containsKey("councilId") ) ) {
                log.error("Missing one of attrbutes department id or council id - both are allowed or none");
                throw new InvalidDataException("Both council and department id are allowed or none");
            }

            if (attributeMap.containsKey("departmentId") && attributeMap.containsKey("councilId")
                    && attributeMap.get("departmentId") != null && attributeMap.get("councilId") != null) {
                try {
                    Long departmentId = Long.valueOf(attributeMap.get("departmentId"));
                    Long councilId = Long.valueOf(attributeMap.get("councilId"));

                    boolean validDepartment = ticketDepartmentService.validateDepartmentInCouncil(departmentId, councilId);

                    if (!validDepartment) {
                        log.error("Failed to validate department id and council id");
                        throw new InvalidDataException("Failed to validate department and coucil id mapping");
                    }

                    String departmentName = ticketDepartmentService.getDepartmentName(departmentId, councilId);
                    String councilName = ticketCouncilService.getCouncilName(councilId);

//                  Prevent creating multiple grievance nodal admin
                    if (findGrievanceNodalAdmin(departmentName).isPresent()) {
                        log.error("User has already been created for other department");
                        throw new InvalidDataException("User has already been created for other department");
                    }

                    attributeMap.put("departmentId", String.valueOf(departmentId));
                    attributeMap.put("departmentName", departmentName);
                    attributeMap.put("councilId", String.valueOf(councilId));
                    attributeMap.put("councilName", councilName);

                    return attributeMap;
                } catch (NumberFormatException e) {
                    log.error("Error while parsing departmetn | council id");
                    throw new InvalidDataException("Department | coucil id only support number");
                } catch (CustomException e) {
                    log.error("Error while checking department and council for user");
                    throw new DataUnavailabilityException(e.getMessage(), "Error while checking department and council for user");
                } catch (Exception e) {
                    log.error("Error while calculating department and council details");
                    throw new DataUnavailabilityException("Unable to get department | council details");
                }
            }
        }
        return Collections.emptyMap();
    }

    /**
     * @param departmentName
     * @return
     */
    private Optional<User> findGrievanceNodalAdmin(@NonNull String departmentName) {
        if ("OTHER".equalsIgnoreCase(departmentName)) {
            Optional<UserDepartment> userDepartmentOptional = userDepartmentRepository
                    .findByCouncilNameAndCouncilName("OTHER", "OTHER");

            if (userDepartmentOptional.isPresent()) {
                return userRepository.findByUserDepartment(userDepartmentOptional.get());
            }
        }
        return Optional.empty();
    }

    private void validateUserPayload(CreateUserDto userDto) {
        if (userDto == null) {
            throw new UserException("Invalid payload or missing payload", ErrorCode.USER_003);
        }

        if (StringUtils.isEmpty(userDto.getFirstName())) {
            throw new UserException("First name is missing", ErrorCode.USER_002);
        }
        if (StringUtils.isEmpty(userDto.getLastName())) {
            throw new UserException("Last name is missing", ErrorCode.USER_002);
        }
        if (StringUtils.isEmpty(userDto.getUsername())) {
            throw new UserException("Username is missing", ErrorCode.USER_002);
        }
        if (StringUtils.isEmpty(userDto.getEmail())) {
            throw new UserException("Email is missing", ErrorCode.USER_002);
        }

        Map<String, String> attributeMap = userDto.getAttributes();

        if (attributeMap == null || attributeMap.isEmpty()) {
            throw new UserException("User attributes are missing", ErrorCode.USER_002);
        }

        if (StringUtils.isEmpty(attributeMap.get("module"))  ) {
            throw new UserException("module is missing", ErrorCode.USER_002);
        }

        if (StringUtils.isEmpty(attributeMap.get("phoneNumber"))  ) {
            throw new UserException("Phone numeber is missing", ErrorCode.USER_002);
        }

        if (StringUtils.isEmpty(attributeMap.get("Role"))  ) {
            throw new UserException("Role is missing", ErrorCode.USER_002);
        }

        String role = userDto.getAttributes().get(ROLE);

        if (role != null && !role.isBlank() && ("SUPERADMIN".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role))) {
            Role roleDetails = roleRepository.findByName(role);

            if(roleDetails == null) {
                log.warn("SUPERADMIN/ADMIN role is not available");
                throw new UserException("SUPERADMIN/ADMIN Role is missing", ErrorCode.USER_002);
            }

            List<UserRole> userRoleList = userRoleRepository.findByRoleId(roleDetails.getId());

            if (userRoleList == null || userRoleList.isEmpty()) {
                log.info("Unable to find UserRole record");
                return;
            }

            List<Long> userIdList = userRoleList.stream()
                    .map(userRole -> userRole.getUserId())
                    .collect(Collectors.toList());

            List<User> userList = userRepository.findAllUserInIds(userIdList);

            if (userList == null || userList.isEmpty()) {
                log.info("There is broken mapping with user and userRole mapping");
                return;
            }

            Optional<User> userOptional = userList.stream()
                    .filter(user -> user.getStatus() == 1)
                    .findAny();

            if (userOptional.isPresent()) {
                if (("SUPERADMIN".equalsIgnoreCase(role))) {
                    throw new UserException("Secratary already exist", ErrorCode.USER_002);
                }

                if (("ADMIN".equalsIgnoreCase(role))) {
                    throw new UserException("Admin already exist", ErrorCode.USER_002);
                }
            }
        }
    }

    /**
     * @param username
     */
    private void checkUserInCenterUM(String username) {
        try {
            ObjectNode requestNode = mapper.createObjectNode();
            requestNode.put("userName", username);
            JsonNode payload = requestNode;
            JsonNode payloadRoot = mapper.createObjectNode();
            ((ObjectNode) payloadRoot).put("request", payload);

            ResponseEntity<String> response = restTemplate.exchange(
                    searchUserUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(payloadRoot),
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                String getUsersResponseBody = response.getBody();
                JsonNode getUsersJsonNode = mapper.readTree(getUsersResponseBody);
                if (getUsersJsonNode.size() > 0) {
                    throw new UserException("User already exist", ErrorCode.USER_002, "User exist in central UM");
                }
            }
        } catch (CustomException e) {
            throw new UserException(e.getMessage(), ErrorCode.USER_002, "User exist in central UM");
        } catch (Exception e) {
            log.error("Error while checking user existance through central UM");
            throw new UserException("Due to technical issue unable to process your request", ErrorCode.USER_001,
                    "Error while finding user in central UM");
        }
    }

    private String validateAndCreateDefaultPassword(CreateUserDto user) {
        if (user != null) {
            if (user.getCredentials() != null && !user.getCredentials().isEmpty()) {
                boolean autoCreate = true;
                String existingPassword = null;
                for (UserCredentials credentials : user.getCredentials()) {
                    if (credentials.getType() != null && credentials.getType().equalsIgnoreCase("password")) {
                        if (credentials.getValue() != null && !credentials.getValue().isBlank()) {
                            autoCreate = false;
                            existingPassword = credentials.getValue();
                        }
                    }
                }
                if (autoCreate) {
                    return generatePassword(user);
                }
                return existingPassword;
            } else {
                // generate random password and set in user
                return generatePassword(user);
            }
        }
        throw new RuntimeException("Error while generating password");
    }

    private String generatePassword(CreateUserDto user) {
        String randomPassword = generateRandomPassword();
        UserCredentials userCredential = UserCredentials.builder().type("password").value(randomPassword).temporary(false).build();
        user.setCredentials(Collections.singletonList(userCredential));
        return randomPassword;
    }

    private String generateRandomPassword() {
        int leftLimit = 97; // letter 'a'
        int rightLimit = 122; // letter 'z'
        int targetStringLength = 10;
        Random random = new Random();
        String generatedString = random.ints(leftLimit, rightLimit + 1)
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        log.debug("random password - {}", generatedString);
        return generatedString;
    }

    private ResponseEntity<String> getUserDetailsFromKeycloak(ResponseEntity response, ObjectMapper mapper) throws Exception {
        String userContent = response.getBody().toString();
        // if error then error body will be sent
        if (userContent.startsWith("{")) {
            JsonNode createUserResponseNode = mapper.readTree(userContent);
            if (createUserResponseNode != null && createUserResponseNode.has("errorMessage")) {
                throw new RuntimeException("User exists with same username");
            }
        }
        ObjectNode requestNode = mapper.createObjectNode();
        requestNode.put("userName", userContent);
        JsonNode payload = requestNode;
        JsonNode payloadRoot = mapper.createObjectNode();
        ((ObjectNode) payloadRoot).put("request", payload);
        ResponseEntity<String> getUsersResponse = searchUsers(payloadRoot);
        return getUsersResponse;
    }

    private static void getCreateUserRequest(CreateUserDto user, List<Department> departmentList) {
        // check for userDepartment
        String module = user.getAttributes().get("module");
        if (module != null) {
            user.getAttributes().put("module", module);
        } else {
            user.getAttributes().put("module", "grievance");
        }
        String departmentId = user.getAttributes().get("departmentName");
        if (departmentId != null) {
            departmentList = Department.getById(Integer.valueOf(departmentId));
            if (departmentList != null && !departmentList.isEmpty()) {
                user.getAttributes().put("departmentName", departmentList.get(0).getCode());
            }
        }

    }

    private void createUserRoleMapping(CreateUserDto user, User savedUser) {
        if (savedUser != null && savedUser.getId() > 0) {
            String role = user.getAttributes().get(ROLE);
            if (role != null && !role.isBlank()) {
                Role roleDetails = roleRepository.findByName(role);
                if (roleDetails != null) {
                    UserRole userRole = UserRole.builder().userId(savedUser.getId()).roleId(roleDetails.getId()).build();
                    userRoleRepository.save(userRole);
                }
            }
        }
    }


    @Transactional
    @Override
    public ResponseEntity<String> updateUser(UpdateUserDto userDto) throws Exception {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            JsonNode root = mapper.createObjectNode();
            JsonNode jsonNodeObject = mapper.convertValue(userDto, JsonNode.class);
            ((ObjectNode) jsonNodeObject).remove("keycloakId");
            ((ObjectNode) jsonNodeObject).remove("id");
            ((ObjectNode) root).put("userName", userDto.getKeycloakId());
            ((ObjectNode) root).put("request", jsonNodeObject);
            restTemplate.put(updateUserUrl, root);

            // update postgres db and also update user department in below call.
            // Removed old user department mapping that was happening above
            updateUserData(userDto);

            esTicketUpdateService.updateEsTicketByUserId(userDto);
            esTicketUpdateService.updateJunkByEsTicketByUserId(userDto);

            return ResponseEntity.ok().body("User updated successful");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getLocalizedMessage());
        }
    }

    private void updateUserData(UpdateUserDto userDto) {
        Optional<User> user = userRepository.findById(userDto.getId());
        if (user.isPresent()) {
            User userDetails = user.get();
            int status = 0;
            if (userDto.isEnabled()) {
                status = 1;
            }
            userDetails.setFirstName(userDto.getFirstName());
            userDetails.setLastname(userDto.getLastName());
            userDetails.setEmail(userDto.getEmail());
            userDetails.setStatus(status);
            if (userDto.getAttributes() != null && !userDto.getAttributes().isEmpty() && userDto.getAttributes().containsKey("phoneNumber")) {
                userDetails.setPhoneNumber(userDto.getAttributes().get("phoneNumber"));
            }
            if (userDto.getAttributes() != null && !userDto.getAttributes().isEmpty() && userDto.getAttributes().containsKey("Role")) {
                String[] role = new String[1];
                role[0] = userDto.getAttributes().get("Role");
                userDetails.setRoles(role);
                if (role[0].equalsIgnoreCase("SUPERADMIN") && userDetails.getUserDepartment() != null) {
//                  Replacing user departmetn mapping in case of super admin
                    userDepartmentRepository.deleteById(userDetails.getUserDepartment().getId());
                }
            }
            // updating user
            userDetails = userRepository.save(userDetails);

            // updating user department mapping
            updateUserDepartmentForUser(userDto.getAttributes(), userDetails);
        }
    }

    private void updateUserDepartmentForUser(Map<String, String> attributeMap, User user){
        if (attributeMap != null) {
            if ((attributeMap.containsKey("departmentId") && !attributeMap.containsKey("councilId"))
                    || (!attributeMap.containsKey("departmentId") && attributeMap.containsKey("councilId"))) {
                log.error("Missing one of attrbutes department id or council id - both are allowed or none");
                throw new InvalidDataException("Both council and department id are allowed or none");
            }

            if (attributeMap.containsKey("departmentId") && attributeMap.containsKey("councilId")
                    && attributeMap.get("departmentId") != null && attributeMap.get("councilId") != null) {
                try {
                    Long departmentId = Long.valueOf(attributeMap.get("departmentId"));
                    Long councilId = Long.valueOf(attributeMap.get("councilId"));

                    boolean validDepartment = ticketDepartmentService.validateDepartmentInCouncil(departmentId, councilId);

                    if (!validDepartment) {
                        log.error("Failed to validate department id and council id");
                        throw new InvalidDataException("Failed to validate department and coucil id mapping");
                    }

                    String departmentName = ticketDepartmentService.getDepartmentName(departmentId, councilId);
                    String councilName = ticketCouncilService.getCouncilName(councilId);

                    UserDepartment userDepartment = user.getUserDepartment();
                    userDepartment.setDepartmentId(departmentId);
                    userDepartment.setDepartmentName(departmentName);
                    userDepartment.setCouncilId(councilId);
                    userDepartment.setCouncilName(councilName);

                    UserDepartment savedUserDepartment = userDepartmentRepository.save(userDepartment);
                } catch (NumberFormatException e) {
                    log.error("Error while parsing department | council id");
                    throw new InvalidDataException("Department | coucil id only support number");
                } catch (Exception e) {
                    log.error("Error while calculating department and council details", e);
                    throw new DataUnavailabilityException("Unable to get department | council details");
                }
            }
        }
    }

    @Override
    public ResponseEntity<String> getUsers(JsonNode payload) throws Exception {

        List<UserResponseDto> childNodes = new ArrayList<>();
        Pageable pageable = PageRequest.of(payload.get("page").asInt(), payload.get("size").asInt(), Sort.by(Sort.Direction.DESC, "id"));
        Page<User> users = Page.empty();

        if (payload.get("searchKeyword") != null && !payload.get("searchKeyword").asText().isBlank()) {
            String email = payload.get("searchKeyword").asText();

            users = userRepository.findByEmailWithPagination(email, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        if (users.hasContent()) {
            for (User user : users.getContent()) {
                childNodes.add(createUserResponse(user));
            }
        }
        JsonNode userResponse = mapper.createObjectNode();
        ((ObjectNode) userResponse).put("count", users.getTotalElements());
        ArrayNode nodes = mapper.valueToTree(childNodes);
        ((ObjectNode) userResponse).put("result", nodes);
        return ResponseEntity.ok(mapper.writeValueAsString(userResponse));
    }

    @Override
    public List<UserResponseDto> getUserByCouncilAndDepartment(Long departmentId, Long councilId, Optional<Boolean> allUserOptional) {
        List<UserResponseDto> userResponseDtoList = new ArrayList<>();

        try {
            boolean validDepartment = ticketDepartmentService.validateDepartmentInCouncil(departmentId, councilId);

            if (!validDepartment) {
                log.error("Failed to validate department id and council id");
                throw new InvalidDataException("Failed to validate department and coucil id mapping");
            }

            List<UserDepartment> userDepartmentList = userDepartmentRepository
                    .findAllByDepartmentIdAndCouncilId(departmentId, councilId);

            if (userDepartmentList != null && !userDepartmentList.isEmpty()) {
                List<User> userList = userRepository.findAllByUserDepartmentIn(userDepartmentList);

                if (userList != null && !userList.isEmpty()) {
                    if (allUserOptional.isPresent() && Boolean.TRUE.equals(allUserOptional.get())) {
                        userResponseDtoList = userList.stream()
                                .map(user -> createUserResponse(user))
                                .collect(Collectors.toList());
                    } else {
                        userResponseDtoList = userList.stream()
                                .filter(user -> user.getStatus() == 1) //Used premitive type in entity - validation not needed
                                .map(user -> createUserResponse(user))
                                .collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error while fetching user list based on department id and council id", e);
            throw new DataUnavailabilityException("Unable to get user detils");
        }

        return userResponseDtoList;
    }

    @Override
    public void assignRole(Long userId, Long roleId) throws NotFoundException {
        try {
            Optional<User> user = userRepository.findById(userId);
            if (user.isPresent()) {
                userRepository.save(user.get());
            }
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User or Role not found", e);
        }
    }


    private User createUserWithApiResponse(JsonNode userContent) throws Exception {
        String[] rolesArray = new String[0];
        String[] departmentArray = new String[0];

        JsonNode rolesNode = userContent.path("attributes").path(ROLE);
        JsonNode departmentNode = userContent.path("attributes").path("departmentName");
        if (rolesNode.isArray()) {
            rolesArray = new String[rolesNode.size()];
            for (int i = 0; i < rolesNode.size(); i++) {
                rolesArray[i] = rolesNode.get(i).asText();
            }
        }

        if (userContent.path("attributes").has("departmentName") && departmentNode.isArray() && !departmentNode.isEmpty()) {
            departmentArray = new String[departmentNode.size()];
            for (int i = 0; i < departmentNode.size(); i++) {
                departmentArray[i] = departmentNode.get(i).asText();
            }
        }

        return User.builder()
                .keycloakId(userContent.path("id").asText())
                .firstName(userContent.path("firstName").asText())
                .lastname(userContent.path("lastName").asText())
                .username(userContent.path("username").asText())
                .phoneNumber(userContent.path("attributes").path("phoneNumber").get(0).asText())
                .email(userContent.path("email").asText())
                .emailVerified(userContent.path("emailVerified").asBoolean())
                .status(userContent.path("enabled").asInt())
                .roles(rolesArray)
                .build();

    }

    @Override
    public ResponseEntity<String> getUsersFromKeycloak(JsonNode payload) throws Exception {

        List<UserResponseDto> childNodes = new ArrayList<>();
        int i = 0;
        ResponseEntity<String> response = restTemplate.exchange(
                listUserUrl,
                HttpMethod.POST,
                new HttpEntity<>(payload),
                String.class
        );
        if (response.getStatusCode() == HttpStatus.OK) {
            String getUsersResponseBody = response.getBody();
            ArrayNode getUsersJsonNode = (ArrayNode) mapper.readTree(getUsersResponseBody);
            if (getUsersJsonNode.size() > 0) {
                for (JsonNode node : getUsersJsonNode) {
                    if (node.path("attributes") != null && !node.path("attributes").isEmpty()
                            && node.path("attributes").path("module") != null && !node.path("attributes").path("module").isEmpty()
                            && node.get("attributes").path("module").get(0).textValue().equalsIgnoreCase("grievance")) {
                        log.info("Grievance user node found || {} -- {}", i++, node);
                        User user = createUserWithApiResponse(node);
                        childNodes.add(createUserResponse(user));
                    }
                }
            }
        }
        JsonNode userResponse = mapper.createObjectNode();
        ((ObjectNode) userResponse).put("count", i);
        ArrayNode nodes = mapper.valueToTree(childNodes);
        ((ObjectNode) userResponse).put("result", nodes);
        return ResponseEntity.ok(mapper.writeValueAsString(userResponse));
    }

    @Override
    public ResponseEntity<String> searchUsers(JsonNode payload) throws Exception {

        ResponseEntity<String> response = restTemplate.exchange(
                apiUrl,
                HttpMethod.POST,
                new HttpEntity<>(payload),
                String.class
        );
        return response;
    }

    @Override
    public ResponseEntity<UserResponseDto> getUserById(String id) throws RuntimeException {
        Optional<User> user = userRepository.findByKeycloakId(id);
        if (user.isPresent()) {
            User userDetails = user.get();
            return new ResponseEntity<>(createUserResponse(userDetails), HttpStatus.OK);
        }
        throw new RuntimeException("User details not found.");
    }

    @Override
    public User activateUser(JsonNode payload) throws Exception {
        long id = payload.get("id").asLong(-1);
        if (id > 0) {
            Optional<User> user = userRepository.findById(id);
            if (user.isPresent()) {
                User userDetails = user.get();
                try {

                    ObjectNode request = mapper.createObjectNode();
                    ObjectNode root = mapper.createObjectNode();
                    root.put("userName", userDetails.getKeycloakId());
                    request.put("request", root);
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    ResponseEntity<String> response = restTemplate.exchange(
                            activeUserUrl, HttpMethod.POST,
                            new HttpEntity<JsonNode>(request, headers), String.class
                    );
                    if (response.getStatusCode() == HttpStatus.OK) {
                        userDetails.setStatus(1);
                        emailService.sendUserActivationMail(userDetails, true);
                        return userRepository.save(userDetails);
                    }
                    throw new RuntimeException("Error in activating user.");
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException("Error in activating user.");
                }
            }
        }
        throw new RuntimeException("Unable to find user details for provided Id.");
    }

    @Override
    public User deactivateUser(JsonNode payload) throws Exception {
        long id = payload.get("id").asLong(-1);
        if (id > 0) {
            Optional<User> user = userRepository.findById(id);
            if (user.isPresent()) {
                User userDetails = user.get();

                ObjectNode request = mapper.createObjectNode();
                ObjectNode root = mapper.createObjectNode();
                root.put("userName", userDetails.getKeycloakId());
                request.put("request", root);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                try {
                    restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
                    ResponseEntity<String> response = restTemplate.exchange(
                            deactivateUserUrl, HttpMethod.POST,
                            new HttpEntity<>(request, headers), String.class);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        userDetails.setStatus(0);
                        emailService.sendUserActivationMail(userDetails, false);
                        return userRepository.save(userDetails);
                    }
                    throw new RuntimeException("Error in deactivating user.");
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException("Error in deactivating user.");
                }
            }
        }
        throw new RuntimeException("Unable to find user details for provided Id.");
    }

    @Override
    public ResponseEntity<String> login(JsonNode body) {
        ResponseEntity<String> response = restTemplate.exchange(
                loginUserUrl, HttpMethod.POST,
                new HttpEntity<>(body), String.class
        );
        return response;
    }


    /**
     * API to change password
     * sample body -
     * {
     * "credentials": [
     * {
     * "type": "password",
     * "value": "ka09eF$299",
     * "temporary": "false"
     * }
     * ]
     * }
     * }
     *
     * @param userCredentials
     */
    public void changePassword(UserCredentials userCredentials) {
        // validate Request
        validateChangePasswordRequest(userCredentials);


    }

    private void validateChangePasswordRequest(UserCredentials userCredentials) {
        if (userCredentials == null) {
            throw new InvalidRequestException("Invalid Request");
        }

    }

    private UserResponseDto createUserResponse(User user) {
//        Set department name and department id

        Map<String, Object> attributeMap = new HashMap<>();
        attributeMap.put("phoneNumber", user.getPhoneNumber());
        attributeMap.put("role", user.getRoles());

        UserDepartment userDepartment = user.getUserDepartment();

        if (userDepartment != null) {
            attributeMap.put("departmentId", userDepartment.getDepartmentId());
            attributeMap.put("departmentName", userDepartment.getDepartmentName());
            attributeMap.put("councilId", userDepartment.getCouncilId());
            attributeMap.put("councilName", userDepartment.getCouncilName());
        } else {
            attributeMap.put("departmentId", null);
            attributeMap.put("departmentName", null);
            attributeMap.put("councilId", null);
            attributeMap.put("councilName", null);
        }

        boolean enabled = false;
        if (user.getStatus() == 1) {
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
                .createdDate(DateUtil.getFormattedDateInString(user.getCreatedDate()))
                .updatedDate(DateUtil.getFormattedDateInString(user.getUpdatedDate()))
                .build();
        return userResponseDto;
    }

    private void sendCreateUserEmail(String email, String userName, String password) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Grievance Account Details");
        message.setText("Account is created for Grievance.\nPlease find the login credentials for your account.\n\nUsername: " + userName + " \nPassword: " + password);
        mailSender.send(message);
    }

    /**
     * @param name
     * @param phoneNumber
     * @param otp
     * @return
     */
    @Override
    public Boolean sendMobileOTP(String name, String phoneNumber, String otp) {
        String smsText = mobileSmsText.replace("{USER}", name);
        smsText = smsText.replace("{OTP}", otp);

        UriComponents uriComponents = UriComponentsBuilder.fromHttpUrl(mobileSmsUri)
                .queryParam("apikey", mobileSmsApiKey)
                .queryParam("senderid", mobileSmsSenderId)
                .queryParam("channel", mobileSmsChannel)
                .queryParam("DCS", mobileSmsDCS)
                .queryParam("flashsms", mobileSmsFlashsms)
                .queryParam("number", phoneNumber)
                .queryParam("text", smsText)
                .queryParam("route", mobileSmsRoute)
                .queryParam("DLTTemplateId", mobileSmsDLTTemplateId)
                .build();

        ResponseEntity<JsonNode> response = null;

        try {
            response = restTemplate.exchange(uriComponents.toString(), HttpMethod.GET,
                    new HttpEntity<>(null), JsonNode.class);
        } catch (Exception e) {
            log.error("Error while calling external OTP service", e);
            throw new OtpException("Error reponse from external service", ErrorCode.OTP_004,
                    "While calling upsmf otp servcie it's thrwoing 400 or 500 response");
        }

        processResponseMessage(response);

        return true;
    }

    /**
     * @param response
     */
    private void processResponseMessage(ResponseEntity<JsonNode> response) {
        if (response.getStatusCode() == HttpStatus.OK) {
            JsonNode resonseJsonNode = response.getBody();
            JsonNode errorCodeNode = resonseJsonNode.get("ErrorCode");
            JsonNode errorMessage = resonseJsonNode.get("ErrorMessage");

            if (errorCodeNode == null || errorCodeNode.isEmpty()) {
                log.error("Error while processing opt response data");
            }

            if (errorCodeNode.asInt() != 0) {
                log.error("Unable to send mobile otp: " + errorMessage.asText());
                throw new OtpException("Unable to send OTP", ErrorCode.OTP_001, errorMessage.asText());
            }
        } else {
            JsonNode resonseJsonNode = response.getBody();
            JsonNode errorCodeNode = resonseJsonNode.get("ErrorCode");
            JsonNode errorMessage = resonseJsonNode.get("ErrorMessage");

            if (errorCodeNode == null || errorCodeNode.isEmpty()) {
                log.error("Error while processing opt response data");
            }

            throw new OtpException("Unable to send OTP", ErrorCode.OTP_004, errorMessage.asText());
        }
    }

    @Override
    public List<User> getAllUsersByRole(String role) {
        List<User> allActiveUsers = userRepository.findAllActiveUsers();
        if(allActiveUsers == null) {
            return new ArrayList<>();
        }
        List<User> users = allActiveUsers.stream().filter(x -> Arrays.stream(x.getRoles()).anyMatch(userRole -> userRole.equalsIgnoreCase(role))).collect(Collectors.toList());
        return users;
    }

    @Override
    public ResponseEntity<Boolean> getUserStatusByEmail(String userName) {
        Optional<User> user = userRepository.findByUsernameAndStatus(userName, 1);
        if(user.isPresent()) {
            return ResponseEntity.ok(Boolean.TRUE);
        }
        return ResponseEntity.ok(Boolean.FALSE);
    }
}
