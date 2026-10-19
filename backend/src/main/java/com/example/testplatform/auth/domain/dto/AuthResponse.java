
package com.example.testplatform.auth.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String token;
    private String username;
    private Long userId;
    private Integer status;
    private String message;
    private String departmentId;

    public AuthResponse(String token, String username, Long userId, Integer status, String message) {
        this.token = token;
        this.username = username;
        this.userId = userId;
        this.status = status;
        this.message = message;
    }

}