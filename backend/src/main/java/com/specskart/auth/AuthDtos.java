package com.specskart.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
    /** deviceId/deviceName/platform/appVersion are null for the web admin login — device
     *  claiming (one till, one account, one device) only applies when the POS app sends them. */
    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password,
                               String deviceId, String deviceName, String platform, String appVersion) {}
    /** storeId/storeName are null for an unscoped login (every ADMIN) -- the app then shows
     *  the free store-picker instead of auto-locking to one shop. */
    public record LoginResponse(String token, String email, String name, String role,
                                java.util.UUID storeId, String storeName) {}
    public record ForgotPasswordRequest(@Email @NotBlank String email) {}
}
