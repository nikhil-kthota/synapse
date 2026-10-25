package com.synapse.auth;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.synapse.auth.AuthDtos.AuthResponse;
import com.synapse.auth.AuthDtos.ChangePasswordRequest;
import com.synapse.auth.AuthDtos.DeleteAccountRequest;
import com.synapse.auth.AuthDtos.LoginRequest;
import com.synapse.auth.AuthDtos.RegisterRequest;
import com.synapse.auth.AuthDtos.UpdateProfileRequest;
import com.synapse.auth.AuthDtos.UserView;
import com.synapse.common.ApiException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = users.save(new User(request.name().trim(), email, passwordEncoder.encode(request.password())));
        return new AuthResponse(tokens.issue(user), UserView.of(user));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
            .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password"));
        return new AuthResponse(tokens.issue(user), UserView.of(user));
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        return UserView.of(current(jwt));
    }

    @PatchMapping("/me")
    @Transactional
    public UserView updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
        User user = current(jwt);
        user.rename(request.name().trim());
        return UserView.of(users.save(user));
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        User user = current(jwt);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Your current password is incorrect");
        }
        user.changePasswordHash(passwordEncoder.encode(request.newPassword()));
        users.save(user);
    }

    /** Permanently deletes the account; projects, graphs and documents go with it (ON DELETE CASCADE). */
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteAccount(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteAccountRequest request) {
        User user = current(jwt);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.badRequest("Incorrect password. Your account was not deleted.");
        }
        users.delete(user);
    }

    private User current(Jwt jwt) {
        return users.findById(Long.valueOf(jwt.getSubject()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session expired"));
    }
}
