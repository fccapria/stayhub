package com.stayhub.backend.controller;

import com.stayhub.backend.dto.UserDTO;
import com.stayhub.backend.entity.Role;
import com.stayhub.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/sync")
    @SuppressWarnings("unchecked")
    public ResponseEntity<UserDTO> syncUser(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        String firstName = jwt.getClaimAsString("given_name");
        String lastName = jwt.getClaimAsString("family_name");

        Role role = Role.CUSTOMER;
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && !realmAccess.isEmpty()) {
            Collection<String> roles = (Collection<String>) realmAccess.get("roles");
            if (roles != null && roles.contains("ADMIN")) {
                role = Role.ADMIN;
            }
        }

        UserDTO userDTO = UserDTO.builder()
                .id(userId)
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .role(role)
                .build();

        UserDTO syncedUser = userService.syncUser(userDTO);
        return ResponseEntity.ok(syncedUser);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserDTO> getUserById(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping("/register")
    public ResponseEntity<UserDTO> registerUser(@RequestBody com.stayhub.backend.dto.UserRegisterRequestDTO request) {
        return ResponseEntity.ok(userService.registerUser(request));
    }
}
