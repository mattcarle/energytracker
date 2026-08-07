package com.carle7.energytracker.controller;

import com.carle7.energytracker.dto.ChangePasswordRequest;
import com.carle7.energytracker.dto.ErrorResponse;
import com.carle7.energytracker.dto.LoginRequest;
import com.carle7.energytracker.dto.SetupRequest;
import com.carle7.energytracker.dto.SetupResponse;
import com.carle7.energytracker.dto.SetupStatusResponse;
import com.carle7.energytracker.dto.UserResponse;
import com.carle7.energytracker.security.UserPrincipal;
import com.carle7.energytracker.service.OctopusService;
import com.carle7.energytracker.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final OctopusService octopusService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(UserService userService, OctopusService octopusService,
                           AuthenticationManager authenticationManager,
                           SecurityContextRepository securityContextRepository) {
        this.userService = userService;
        this.octopusService = octopusService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/setup-status")
    public SetupStatusResponse setupStatus() {
        return new SetupStatusResponse(userService.isSetupRequired());
    }

    @PostMapping("/setup")
    public ResponseEntity<?> setup(@RequestBody SetupRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            userService.setupAdmin(request.getPassword(), request.getOctopusAccountNumber(), request.getOctopusAuthToken());
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(e.getMessage()));
        }

        ResponseEntity<UserResponse> loginResponse = authenticateAndRespond("admin", request.getPassword(), httpRequest, httpResponse);

        OctopusService.AccountLoadResult accountLoad = octopusService.loadAccountData(false);
        OctopusService.UsageLoadResult usageLoad = octopusService.loadUsageData(false);

        return ResponseEntity.ok(new SetupResponse(loginResponse.getBody(), accountLoad, usageLoad));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        try {
            return authenticateAndRespond(request.getUsername(), request.getPassword(), httpRequest, httpResponse);
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid username or password"));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return UserResponse.from(principal.getUser());
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                             @RequestBody ChangePasswordRequest request) {
        try {
            userService.changePassword(principal.getUser(), request.getCurrentPassword(), request.getNewPassword());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
        }
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<UserResponse> authenticateAndRespond(String username, String password,
                                                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(UserResponse.from(principal.getUser()));
    }
}
