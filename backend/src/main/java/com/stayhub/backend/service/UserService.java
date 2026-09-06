package com.stayhub.backend.service;

import com.stayhub.backend.dto.UserDTO;
import com.stayhub.backend.dto.UserRegisterRequestDTO;
import com.stayhub.backend.entity.User;
import com.stayhub.backend.exception.ResourceNotFoundException;
import com.stayhub.backend.mapper.DtoMapper;
import com.stayhub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.beans.factory.annotation.Value;
import com.stayhub.backend.entity.Role;

import java.util.Map;
import java.util.List;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Value("${keycloak.auth-server-url:http://localhost:9000}")
    private String keycloakServerUrl;

    @Value("${keycloak.realm:stayhub}")
    private String realm;

    @Value("${keycloak.admin.username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin.password:admin}")
    private String adminPassword;

    @Transactional(readOnly = true)
    public UserDTO getUserById(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + id));
        return DtoMapper.toDto(user);
    }

    @Transactional
    public UserDTO syncUser(UserDTO userDTO) {
        User user = userRepository.findById(userDTO.getId())
                .map(existingUser -> {
                    existingUser.setEmail(userDTO.getEmail());
                    existingUser.setFirstName(userDTO.getFirstName());
                    existingUser.setLastName(userDTO.getLastName());
                    existingUser.setRole(userDTO.getRole());
                    return userRepository.save(existingUser);
                })
                .orElseGet(() -> userRepository.save(DtoMapper.toEntity(userDTO)));
        return DtoMapper.toDto(user);
    }

    private static final java.util.regex.Pattern USERNAME_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9._-]{3,30}$");
    private static final java.util.regex.Pattern EMAIL_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final java.util.regex.Pattern NAME_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z\\u00C0-\\u024F\\s'-]{1,50}$");

    private void validateUserRegisterRequest(UserRegisterRequestDTO dto) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "I dati di registrazione sono obbligatori.");
        }
        if (dto.getUsername() == null || dto.getUsername().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il nome utente è obbligatorio.");
        }
        String username = dto.getUsername().trim();
        if (username.length() < 3 || username.length() > 30 || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'username deve contenere tra 3 e 30 caratteri alfanumerici (sono ammessi solo '.', '-', '_', senza spazi).");
        }

        if (dto.getEmail() == null || dto.getEmail().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'indirizzo email è obbligatorio.");
        }
        String email = dto.getEmail().trim();
        if (email.length() > 100 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'indirizzo email specificato non è valido.");
        }

        if (dto.getFirstName() == null || dto.getFirstName().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il nome è obbligatorio.");
        }
        String firstName = dto.getFirstName().trim();
        if (firstName.length() > 50 || !NAME_PATTERN.matcher(firstName).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il nome può contenere solo lettere, spazi, apostrofi e trattini (senza parentesi o simboli).");
        }

        if (dto.getLastName() == null || dto.getLastName().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il cognome è obbligatorio.");
        }
        String lastName = dto.getLastName().trim();
        if (lastName.length() > 50 || !NAME_PATTERN.matcher(lastName).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il cognome contiene caratteri non consentiti: usa solo lettere, spazi o trattini (senza parentesi o simboli).");
        }

        if (dto.getPassword() == null || dto.getPassword().trim().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La password deve contenere almeno 6 caratteri (non solo spazi).");
        }
        if (dto.getPassword().length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La password non può superare i 100 caratteri.");
        }
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public UserDTO registerUser(UserRegisterRequestDTO dto) {
        validateUserRegisterRequest(dto);

        dto.setUsername(dto.getUsername().trim());
        dto.setEmail(dto.getEmail().trim());
        dto.setFirstName(dto.getFirstName().trim());
        dto.setLastName(dto.getLastName().trim());
        dto.setPassword(dto.getPassword().trim());

        String adminToken = getAdminToken();
        RestTemplate restTemplate = new RestTemplate();
        String usersUrl = keycloakServerUrl + "/admin/realms/" + realm + "/users";

        // 1. Verifica preventiva di unicità per username ed email
        checkUsernameAndEmailAvailability(dto.getUsername(), dto.getEmail(), adminToken, restTemplate, usersUrl);

        // 2. Prepare user representation
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        Map<String, Object> credentials = new HashMap<>();
        credentials.put("type", "password");
        credentials.put("value", dto.getPassword());
        credentials.put("temporary", false);

        Map<String, Object> userRepresentation = new HashMap<>();
        userRepresentation.put("username", dto.getUsername());
        userRepresentation.put("enabled", true);
        userRepresentation.put("email", dto.getEmail());
        userRepresentation.put("firstName", dto.getFirstName());
        userRepresentation.put("lastName", dto.getLastName());
        userRepresentation.put("credentials", List.of(credentials));

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(userRepresentation, headers);
        
        ResponseEntity<String> response;
        try {
            response = restTemplate.postForEntity(usersUrl, request, String.class);
        } catch (HttpStatusCodeException ex) {
            String rawBody = ex.getResponseBodyAsString();
            String body = rawBody != null ? rawBody.toLowerCase() : "";
            if (ex.getStatusCode() == HttpStatus.CONFLICT || body.contains("user exists")) {
                if (body.contains("same username") || body.contains("username")) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Questo nome utente è già in uso. Scegline un altro.");
                } else if (body.contains("same email") || body.contains("email")) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Questo indirizzo email è già registrato. Accedi con il tuo account.");
                } else {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Nome utente o indirizzo email già registrati.");
                }
            }
            if (body.contains("error-person-name-invalid-character")) {
                if (body.contains("lastname")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il cognome contiene caratteri non consentiti: usa solo lettere, spazi o trattini (senza parentesi o simboli).");
                } else if (body.contains("firstname")) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il nome contiene caratteri non consentiti: usa solo lettere, spazi o trattini (senza parentesi o simboli).");
                } else {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il nome o cognome contiene caratteri non consentiti (senza parentesi o simboli).");
                }
            }
            if (body.contains("password")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La password non soddisfa i requisiti minimi di sicurezza.");
            }
            if (body.contains("error-invalid-email") || (body.contains("email") && body.contains("invalid"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'indirizzo email specificato non è valido.");
            }
            if (body.contains("username") && body.contains("invalid")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Il nome utente contiene caratteri non consentiti.");
            }

            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"errorMessage\"\\s*:\\s*\"([^\"]+)\"").matcher(rawBody != null ? rawBody : "");
            if (m.find()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore di registrazione: " + m.group(1));
            }
            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("\"error_description\"\\s*:\\s*\"([^\"]+)\"").matcher(rawBody != null ? rawBody : "");
            if (m2.find()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, m2.group(1));
            }

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Errore durante la registrazione: " + ex.getStatusText());
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Errore durante la registrazione: " + e.getMessage());
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossibile creare l'utente su Keycloak.");
        }

        // 2. Extract Keycloak User ID from Location header
        List<String> locations = response.getHeaders().get("Location");
        if (locations == null || locations.isEmpty()) {
            throw new RuntimeException("Keycloak user creation did not return a Location header");
        }
        String location = locations.get(0);
        String keycloakUserId = location.substring(location.lastIndexOf("/") + 1);

        try {
            // 3. Assign role(s) in Keycloak (CUSTOMER always, HOST if requested, never ADMIN via public registration)
            Role assignedRole = (dto.getRole() == Role.HOST) ? Role.HOST : Role.CUSTOMER;
            assignRealmRoleToUser(keycloakUserId, Role.CUSTOMER.name(), adminToken);
            if (assignedRole == Role.HOST) {
                assignRealmRoleToUser(keycloakUserId, Role.HOST.name(), adminToken);
            }

            // 4. Create local user in MariaDB
            UserDTO userDto = UserDTO.builder()
                    .id(keycloakUserId)
                    .email(dto.getEmail())
                    .firstName(dto.getFirstName())
                    .lastName(dto.getLastName())
                    .role(assignedRole)
                    .build();
            
            return syncUser(userDto);
        } catch (Exception e) {
            // Rollback Keycloak user if role assignment or DB sync fails
            try {
                restTemplate.exchange(usersUrl + "/" + keycloakUserId, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            } catch (Exception ignored) {}
            throw new RuntimeException("Errore durante la registrazione: " + e.getMessage(), e);
        }
    }

    @Transactional
    public UserDTO becomeHost(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (user.getRole() != Role.ADMIN) {
            user.setRole(Role.HOST);
            user = userRepository.save(user);
        }

        String adminToken = getAdminToken();
        assignRealmRoleToUser(userId, Role.HOST.name(), adminToken);

        return DtoMapper.toDto(user);
    }

    @SuppressWarnings("unchecked")
    private void assignRealmRoleToUser(String keycloakUserId, String roleName, String adminToken) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        String roleUrl = keycloakServerUrl + "/admin/realms/" + realm + "/roles/" + roleName;
        Map<String, Object> roleMap = null;
        try {
            ResponseEntity<Map> roleResponse = restTemplate.exchange(roleUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            if (roleResponse.getStatusCode().is2xxSuccessful()) {
                roleMap = roleResponse.getBody();
            }
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            // Auto-provision role if it doesn't exist in the running Keycloak realm
            Map<String, Object> createRolePayload = new HashMap<>();
            createRolePayload.put("name", roleName);
            createRolePayload.put("description", roleName + " Role");
            HttpEntity<Map<String, Object>> createRoleRequest = new HttpEntity<>(createRolePayload, headers);
            String rolesUrl = keycloakServerUrl + "/admin/realms/" + realm + "/roles";
            restTemplate.postForEntity(rolesUrl, createRoleRequest, String.class);

            ResponseEntity<Map> roleResponse = restTemplate.exchange(roleUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            roleMap = roleResponse.getBody();
        }

        if (roleMap == null || !roleMap.containsKey("id")) {
            throw new RuntimeException("Failed to fetch role info for " + roleName);
        }
        String roleId = (String) roleMap.get("id");

        String roleMappingUrl = keycloakServerUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/role-mappings/realm";
        Map<String, Object> roleMappingPayload = new HashMap<>();
        roleMappingPayload.put("id", roleId);
        roleMappingPayload.put("name", roleName);

        HttpEntity<List<Map<String, Object>>> roleMappingRequest = new HttpEntity<>(List.of(roleMappingPayload), headers);
        ResponseEntity<String> roleMappingResponse = restTemplate.postForEntity(roleMappingUrl, roleMappingRequest, String.class);

        if (!roleMappingResponse.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to map role " + roleName + " to Keycloak user");
        }
    }

    @SuppressWarnings("unchecked")
    private String getAdminToken() {
        RestTemplate restTemplate = new RestTemplate();
        String url = keycloakServerUrl + "/realms/master/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", "admin-cli");
        map.add("username", adminUsername);
        map.add("password", adminPassword);
        map.add("grant_type", "password");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return (String) response.getBody().get("access_token");
        }
        throw new RuntimeException("Failed to obtain Keycloak admin token");
    }

    @SuppressWarnings("rawtypes")
    private void checkUsernameAndEmailAvailability(String username, String email, String adminToken, RestTemplate restTemplate, String usersUrl) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        boolean usernameTaken = false;
        boolean emailTaken = false;

        try {
            ResponseEntity<List> userCheck = restTemplate.exchange(
                    usersUrl + "?username=" + username + "&exact=true",
                    HttpMethod.GET,
                    entity,
                    List.class
            );
            if (userCheck.getBody() != null && !userCheck.getBody().isEmpty()) {
                usernameTaken = true;
            }
        } catch (Exception ignored) {}

        try {
            ResponseEntity<List> emailCheck = restTemplate.exchange(
                    usersUrl + "?email=" + email + "&exact=true",
                    HttpMethod.GET,
                    entity,
                    List.class
            );
            if (emailCheck.getBody() != null && !emailCheck.getBody().isEmpty()) {
                emailTaken = true;
            }
        } catch (Exception ignored) {}

        if (usernameTaken && emailTaken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sia il nome utente che l'indirizzo email sono già registrati.");
        } else if (usernameTaken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Questo nome utente è già in uso. Scegline un altro.");
        } else if (emailTaken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Questo indirizzo email è già registrato. Accedi con il tuo account.");
        }
    }
}
