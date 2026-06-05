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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;
import java.util.List;
import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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

    @Transactional
    @SuppressWarnings("unchecked")
    public UserDTO registerUser(UserRegisterRequestDTO dto) {
        String adminToken = getAdminToken();
        RestTemplate restTemplate = new RestTemplate();
        String usersUrl = "http://localhost:9000/admin/realms/stayhub/users";

        // 1. Prepare user representation
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
        } catch (Exception e) {
            throw new RuntimeException("Error calling Keycloak to create user: " + e.getMessage());
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to create user in Keycloak, status: " + response.getStatusCode());
        }

        // 2. Extract Keycloak User ID from Location header
        List<String> locations = response.getHeaders().get("Location");
        if (locations == null || locations.isEmpty()) {
            throw new RuntimeException("Keycloak user creation did not return a Location header");
        }
        String location = locations.get(0);
        String keycloakUserId = location.substring(location.lastIndexOf("/") + 1);

        // 3. Assign role to user in Keycloak
        String roleName = dto.getRole().name();
        
        // 3a. Get role mapping info (need role ID)
        String roleUrl = "http://localhost:9000/admin/realms/stayhub/roles/" + roleName;
        ResponseEntity<Map> roleResponse = restTemplate.exchange(roleUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        if (!roleResponse.getStatusCode().is2xxSuccessful() || roleResponse.getBody() == null) {
            throw new RuntimeException("Failed to fetch role info for " + roleName);
        }
        Map<String, Object> roleMap = roleResponse.getBody();
        String roleId = (String) roleMap.get("id");

        // 3b. Post role mapping
        String roleMappingUrl = "http://localhost:9000/admin/realms/stayhub/users/" + keycloakUserId + "/role-mappings/realm";
        Map<String, Object> roleMappingPayload = new HashMap<>();
        roleMappingPayload.put("id", roleId);
        roleMappingPayload.put("name", roleName);
        
        HttpEntity<List<Map<String, Object>>> roleMappingRequest = new HttpEntity<>(List.of(roleMappingPayload), headers);
        ResponseEntity<String> roleMappingResponse = restTemplate.postForEntity(roleMappingUrl, roleMappingRequest, String.class);
        
        if (!roleMappingResponse.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Failed to map role to Keycloak user");
        }

        // 4. Create local user in MariaDB
        UserDTO userDto = UserDTO.builder()
                .id(keycloakUserId)
                .email(dto.getEmail())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .role(dto.getRole())
                .build();
        
        return syncUser(userDto);
    }

    @SuppressWarnings("unchecked")
    private String getAdminToken() {
        RestTemplate restTemplate = new RestTemplate();
        String url = "http://localhost:9000/realms/master/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", "admin-cli");
        map.add("username", "admin");
        map.add("password", "admin");
        map.add("grant_type", "password");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
        
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return (String) response.getBody().get("access_token");
        }
        throw new RuntimeException("Failed to obtain Keycloak admin token");
    }
}
