package com.stayhub.backend.dto;

import com.stayhub.backend.entity.Role;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserRegisterRequestDTO {
    private String username;
    private String password;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
}
