package com.dfs.security;

import com.dfs.model.User;
import com.dfs.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtService     jwtService;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email    = oAuth2User.getAttribute("email");
        String name     = oAuth2User.getAttribute("name");

        log.info("Google OAuth2 login: email={}", email);

        User user = userRepository.findByEmail(email).orElseGet(() -> {
            String username = sanitizeUsername(name, email);
            return userRepository.save(
                User.builder()
                    .username(username)
                    .email(email)
                    .passwordHash("OAUTH2_" + UUID.randomUUID())
                    .role(User.Role.ROLE_USER)
                    .build()
            );
        });

        String token = jwtService.generateToken(user);

        // Use the first origin if multiple are configured
        String baseUrl = frontendUrl.split(",")[0].trim();

        String redirectUrl = baseUrl + "/oauth2/callback"
                + "?token="    + token
                + "&username=" + user.getUsername()
                + "&email="    + user.getEmail()
                + "&role="     + user.getRole().name();

        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private String sanitizeUsername(String name, String email) {
        String base = (name != null && !name.isBlank())
            ? name.toLowerCase().replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_")
            : email.split("@")[0];
        if (base.length() > 40) base = base.substring(0, 40);
        String candidate = base;
        int suffix = 1;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + "_" + suffix++;
        }
        return candidate;
    }
}
