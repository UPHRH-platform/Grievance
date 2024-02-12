package org.upsmf.grievance.service.impl;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import org.upsmf.grievance.dto.*;
import org.upsmf.grievance.enums.Department;
import org.upsmf.grievance.exception.*;
import org.upsmf.grievance.exception.runtime.InvalidRequestException;
import org.upsmf.grievance.model.Role;
import org.upsmf.grievance.model.User;
import org.upsmf.grievance.model.UserDepartment;
import org.upsmf.grievance.model.UserRole;
import org.upsmf.grievance.model.reponse.Response;
import org.upsmf.grievance.repository.RoleRepository;
import org.upsmf.grievance.repository.UserDepartmentRepository;
import org.upsmf.grievance.repository.UserRepository;
import org.upsmf.grievance.repository.UserRoleRepository;
import org.upsmf.grievance.service.*;
import org.upsmf.grievance.util.DateUtil;
import org.upsmf.grievance.util.ErrorCode;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
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

    @Autowired
    private SchedulerConfigService schedulerConfigService;

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
                log.error("Ignore || Error while parsing success response", jp.getLocalizedMessage());
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
            log.info("create user search user response - {}", getUsersResponse);
            if (getUsersResponse.getStatusCode() == HttpStatus.OK) {
                String getUsersResponseBody = getUsersResponse.getBody();
                JsonNode getUsersJsonNode = mapper.readTree(getUsersResponseBody);
                if (getUsersJsonNode.size() > 0) {
                    JsonNode userContentData = getUsersJsonNode;
                    User newUser = createUserWithApiResponse(userContentData);

                    // create user userDepartment mapping
                    if (attributeMap.get("departmentId") != null) {
                        // if user role is grievance nodal then we will reuse the same entry
                        if(attributeMap.get("departmentName").equalsIgnoreCase("OTHER")
                                && attributeMap.get("councilName").equalsIgnoreCase("OTHER")) {
                            log.info("inside user department creation for other department");
                            String departmentName = getDepartmentByCouncilIDAndDepartmentId(attributeMap);
                            log.info("inside user department creation for department - {}", departmentName);
                            Optional<List<User>> byUserDepartment = findGrievanceNodalAdmin(departmentName);
                            log.info("inside user department creation for other department - {}", byUserDepartment);
                            //Prevent creating multiple grievance nodal admin
                            if (byUserDepartment.isPresent()) {
                                List<User> activeGrievanceUsers = byUserDepartment.get().stream().filter(x -> x.getStatus() == 1).collect(Collectors.toList());
                                log.info("inside user department creation for other department || active users - {}", activeGrievanceUsers);
                                List<User> inactiveGrievanceUsers = byUserDepartment.get().stream().filter(x -> x.getStatus() == 0).collect(Collectors.toList());
                                log.info("inside user department creation for other department || inactive users - {}", inactiveGrievanceUsers);
                                if((activeGrievanceUsers == null || (activeGrievanceUsers != null && activeGrievanceUsers.size() == 0))
                                        && inactiveGrievanceUsers != null && inactiveGrievanceUsers.size() > 0) {
                                    log.info("inside user department creation for other department, setting department");
                                    newUser.setUserDepartment(inactiveGrievanceUsers.get(0).getUserDepartment());
                                } else {
                                    log.error("User has already been created for other department");
                                    throw new InvalidDataException("User has already been created for other department");
                                }
                            } else {
                                log.info("inside user department creation for other department || creating default");
                                createUserDepartment(attributeMap, newUser);
                            }
                        } else {
                            createUserDepartment(attributeMap, newUser);
                        }
                    }

                    User savedUser = userRepository.save(newUser);
                    // create user role mapping
                    createUserRoleMapping(user, savedUser);
                    // update mail config if user role secretary
                    boolean superadmin = Arrays.stream(savedUser.getRoles()).anyMatch(role -> role.equalsIgnoreCase("SUPERADMIN"));
                    if(superadmin) {
                        updateMailConfigEmail(savedUser.getEmail());
                    }

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

    private void createUserDepartment(Map<String, String> attributeMap, User newUser) {
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

    private String getDepartmentByCouncilIDAndDepartmentId(Map<String, String> attributeMap) {
        Long departmentId = Long.valueOf(attributeMap.get("departmentId"));
        Long councilId = Long.valueOf(attributeMap.get("councilId"));
        boolean validDepartment = ticketDepartmentService.validateDepartmentInCouncil(departmentId, councilId);
        if (!validDepartment) {
            log.error("Failed to validate department id and council id");
            throw new InvalidDataException("Failed to validate department and coucil id mapping");
        }

        String departmentName = ticketDepartmentService.getDepartmentName(departmentId, councilId);
        return departmentName;
    }


    /**
     * @param attributeMap
     * @return
     */
    private Map<String, String> processUserDepartmentAndCouncil(Map<String, String> attributeMap) {
        if (attributeMap != null) {
            if (( attributeMap.containsKey("departmentId") && !attributeMap.containsKey("councilId") )
                    || ( !attributeMap.containsKey("departmentId") && attributeMap.containsKey("councilId") ) ) {
                log.error("Missing one of attributes department id or council id - both are allowed or none");
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
                        throw new InvalidDataException("Failed to validate department and council id mapping");
                    }

                    String departmentName = ticketDepartmentService.getDepartmentName(departmentId, councilId);
                    String councilName = ticketCouncilService.getCouncilName(councilId);

                    Optional<List<User>> byUserDepartment = findGrievanceNodalAdmin(departmentName);
//                  Prevent creating multiple grievance nodal admin
                    if (byUserDepartment.isPresent()) {
                        List<User> users = byUserDepartment.get().stream().filter(x -> x.getStatus() == 1).collect(Collectors.toList());
                        if(users != null && users.size() > 0){
                            log.info("user department mapping present for active user - {}", users);
                            log.error("User has already been created for other department");
                            throw new InvalidDataException("User has already been created for other department");
                        }
                    }

                    attributeMap.put("departmentId", String.valueOf(departmentId));
                    attributeMap.put("departmentName", departmentName);
                    attributeMap.put("councilId", String.valueOf(councilId));
                    attributeMap.put("councilName", councilName);

                    return attributeMap;
                } catch (NumberFormatException e) {
                    log.error("Error while parsing department | council id");
                    throw new InvalidDataException("Department | council id only support number");
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
    private Optional<List<User>> findGrievanceNodalAdmin(@NonNull String departmentName) {
        if ("OTHER".equalsIgnoreCase(departmentName)) {
            Optional<UserDepartment> userDepartmentOptional = userDepartmentRepository
                    .findByCouncilNameAndCouncilName("OTHER", "OTHER");

            if (userDepartmentOptional.isPresent()) {
                return userRepository.findAllByUserDepartment(userDepartmentOptional.get());
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
            throw new UserException("Phone number is missing", ErrorCode.USER_002);
        }

        if (StringUtils.isEmpty(attributeMap.get("Role"))  ) {
            throw new UserException("Role is missing", ErrorCode.USER_002);
        }

        String role = userDto.getAttributes().get(ROLE);

        if (role != null && !role.isBlank() && ("SUPERADMIN".equalsIgnoreCase(role)
                || "ADMIN".equalsIgnoreCase(role) || "GRIEVANCEADMIN".equalsIgnoreCase(role))) {
            Role roleDetails = roleRepository.findByName(role);

            if(roleDetails == null) {
                log.warn("Secretary/Admin/Grievance Nodal role is not available");
                throw new UserException("Secretary/Admin/Grievance Nodal Role is missing", ErrorCode.USER_002);
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
                    throw new UserException("Application is designed to have one Secretary.", ErrorCode.USER_002);
                }

                if (("ADMIN".equalsIgnoreCase(role))) {
                    throw new UserException("Application is designed to have one Admin.", ErrorCode.USER_002);
                }

                if (("GRIEVANCEADMIN".equalsIgnoreCase(role))) {
                    throw new UserException("Application is designed to have one Grievance Nodal.", ErrorCode.USER_002);
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
            log.error("Error while checking user existence through central UM");
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
            // update mail config if user role secretary
            updateSecretaryMailAddress(userDto);

            return ResponseEntity.ok().body("User updated successful");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getLocalizedMessage());
        }
    }

    /**
     *
     * @param userDto
     */
    private void updateSecretaryMailAddress(UpdateUserDto userDto) {
        if(userDto.getAttributes() != null) {
            String role = userDto.getAttributes().get("Role");
            if(role != null && !role.isBlank() && role.equalsIgnoreCase("SUPERADMIN")) {
                updateMailConfigEmail(userDto.getEmail());
            }
        }
    }

    /**
     *
     * @param email
     */
    private void updateMailConfigEmail(String email) {
        List<MailConfigDto> schedulerConfigServiceAll = schedulerConfigService.getAll();
        if(schedulerConfigServiceAll != null && !schedulerConfigServiceAll.isEmpty()) {
            List<MailConfigDto> secretary = schedulerConfigServiceAll.stream().filter(config -> config.isActive()
                    && config.getAuthorityTitle().equalsIgnoreCase("SECRETARY")).collect(Collectors.toList());
            if(secretary != null && !secretary.isEmpty()) {
                secretary.stream().forEach(secConf -> {
                    List<String> emails = new ArrayList<>();
                    emails.add(email);
                    secConf.setAuthorityEmails(emails);
                    schedulerConfigService.update(secConf);
                });
            }
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
    public ResponseEntity<Response> activateUser(JsonNode payload) throws Exception {
        long id = payload.get("id").asLong(-1);
        if (id > 0) {
            Optional<User> user = userRepository.findById(id);
            if (user.isPresent()) {
                User userDetails = user.get();
                // if role is admin/secretary/Grievance Admin
                // then only one user can be active at a time
                ResponseEntity<Response> checkRoleAndActiveCount = checkRoleAndActiveCount(userDetails);
                if(checkRoleAndActiveCount.getStatusCode().value() != HttpStatus.OK.value()) {
                    return checkRoleAndActiveCount;
                }
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
                        User data = userRepository.save(userDetails);
                        // update mail config if user role secretary
                        boolean superadmin = Arrays.stream(data.getRoles()).anyMatch(role -> role.equalsIgnoreCase("SUPERADMIN"));
                        if(superadmin) {
                            updateMailConfigEmail(data.getEmail());
                        }
                        return ResponseEntity.ok(Response.builder().body(data).status(HttpStatus.OK.value()).build());
                    }

                    throw new CustomException("Error in activating user.", "Error in activating user.");
                } catch (Exception e) {
                    log.error("Error in activating user ", e);
                    throw new CustomException("Error in activating user.", "Error in activating user.");
                }
            }
        }
        throw new CustomException("Unable to find user details for provided Id.", "Unable to find user details for provided Id.");
    }

    private ResponseEntity<Response> checkRoleAndActiveCount(User userDetails) {
        if(userDetails == null || userDetails.getRoles() == null
                || Arrays.stream(userDetails.getRoles()).count() <= 0) {
            log.error("Failed to check user role");
            throw new CustomException("Failed to check user role", "Failed to check user role");
        }
        List<User> users = userRepository.findAll();
        AtomicLong matchCount = new AtomicLong();
        matchCount.set(0);
        Arrays.stream(userDetails.getRoles()).forEach(role -> {
            log.debug("matching current role - {}", role);
            if(role.equalsIgnoreCase("SUPERADMIN")
                    || role.equalsIgnoreCase("GRIEVANCEADMIN")
                    || role.equalsIgnoreCase("ADMIN")) {
                // get existing user for role
                long count = users.stream().filter(user ->
                        Arrays.stream(user.getRoles()).anyMatch(userRole -> userRole.equalsIgnoreCase(role))
                                && user.getStatus() == 1 && user.getId() != userDetails.getId()).count();
                log.debug("Active user count - {}", count);
                matchCount.set(count);
            }
        });
        log.debug("match count for user role - {}", matchCount.get());
        if(matchCount.get() > 0) {
            throw new CustomException("Application is designed to have only one active Secretary or Admin or Grievance Nodal.", "Application is designed to have only one active Secretary or Admin or Grievance Nodal.");
        }
        return ResponseEntity.ok(Response.builder().body("Success").status(HttpStatus.OK.value()).build());
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
            throw new OtpException("Error response from external service", ErrorCode.OTP_004,
                    "While calling UPSMF otp service it's throwing 400 or 500 response");
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

    @Override
    public ResponseEntity<String> filterUsers(JsonNode payload) throws Exception {

        List<UserResponseDto> childNodes = new ArrayList<>();
        Pageable pageable = PageRequest.of(payload.get("page").asInt(), payload.get("size").asInt(), Sort.by(Sort.Direction.DESC, "id"));
        Page<User> users = Page.empty();

        if (payload.get("filter") != null && payload.get("filter").size() > 0) {
            // filter users
            return getFilteredResponseEntity(payload, childNodes, users);
        } else if (payload.get("searchKeyword") != null && !payload.get("searchKeyword").asText().isBlank()) {
            String email = payload.get("searchKeyword").asText();
            users = userRepository.findByEmailWithPagination(email, pageable);
        }  else {
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

    private ResponseEntity<String> getFilteredResponseEntity(JsonNode payload, List<UserResponseDto> childNodes, Page<User> users) throws JsonProcessingException {
        String email = null;
        if(payload.get("searchKeyword") != null && !payload.get("searchKeyword").asText().isBlank()) {
            email = payload.get("searchKeyword").asText();
        }
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> filter = mapper.convertValue(payload.get("filter"), new TypeReference<Map<String, String>>(){});
        List<User> userList = filterUserData(filter, email);
        JsonNode userResponse = mapper.createObjectNode();
        if (userList != null) {
            for (User user : userList) {
                childNodes.add(createUserResponse(user));
            }
            ((ObjectNode) userResponse).put("count", userList.size());
        }
        ArrayNode nodes = mapper.valueToTree(childNodes);
        ((ObjectNode) userResponse).put("result", nodes);
        return ResponseEntity.ok(mapper.writeValueAsString(userResponse));
    }

    @Transactional(readOnly = true)
    private List<User> filterUserData(Map<String, String> map, String searchString) {
        List<User> users = null;
        String roleValue = null;
        String councilId = null;
        String departmentId = null;
        if(map.containsKey("role")) {
            roleValue = map.get("role");
        }
        if(map.containsKey("councilId")) {
            councilId = map.get("councilId");
        }
        if(map.containsKey("departmentId")) {
            departmentId = map.get("departmentId");
        }
        if(searchString != null && !searchString.isBlank()) {
            users = userRepository.findAllByKeyword(searchString);
        } else {
            users = userRepository.findAll();
        }
        // filter all users by role
        if(users!= null && !users.isEmpty() && roleValue != null && !roleValue.isBlank()) {
            String finalRoleValue = roleValue;
            users = users.stream().filter(x -> Arrays.stream(x.getRoles())
                    .anyMatch(role -> role.equalsIgnoreCase(finalRoleValue))).collect(Collectors.toList());
        }
        // filter on council
        if(users!= null && !users.isEmpty() && councilId != null && !councilId.isBlank()) {
            Long finalCouncilId = Long.parseLong(councilId);
            users = users.stream().filter(x -> x.getUserDepartment() != null && x.getUserDepartment().getCouncilId().longValue() == finalCouncilId.longValue()).collect(Collectors.toList());
        }
        // filter on department
        if(users!= null && !users.isEmpty() && departmentId != null && !departmentId.isBlank()) {
            Long finalDepartmentId = Long.parseLong(departmentId);
            users = users.stream().filter(x -> x.getUserDepartment() != null && x.getUserDepartment().getDepartmentId().longValue() == finalDepartmentId.longValue()).collect(Collectors.toList());
        }
        return users;
    }

    @Override
    public ResponseEntity<String> sendTestMail(String email) throws Exception {
        emailService.sendTestMail(email);
        return ResponseEntity.ok("Test mail sent to email - ".concat(email));
    }
}
