package com.synapse.auth;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
        @NotBlank(message = "Name is required") @Size(max = 120) String name,
        @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") String email,
        @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String password) {
    }

    public record LoginRequest(
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "Password is required") String password) {
    }

    public record UserView(Long id, String name, String email, Instant createdAt) {
        static UserView of(User user) {
            return new UserView(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt());
        }
    }

    public record UpdateProfileRequest(
        @NotBlank(message = "Name is required") @Size(max = 120, message = "Name is too long") String name) {
    }

    public record ChangePasswordRequest(
        @NotBlank(message = "Enter your current password") String currentPassword,
        @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters") String newPassword) {
    }

    public record DeleteAccountRequest(@NotBlank(message = "Enter your password to confirm") String password) {
    }

    public record AuthResponse(String token, UserView user) {
    }
}
