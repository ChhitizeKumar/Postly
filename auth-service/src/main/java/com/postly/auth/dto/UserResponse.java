package com.postly.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String  id;
    private String  email;
    private String  username;
    private String  role;
    private boolean emailVerified;
}