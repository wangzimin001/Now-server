package com.wangzimin.now.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.wangzimin.now.repository.AuthRepository;

class AuthServiceTest {

    @Test
    void registerStoresPasswordAndRefreshTokenAsHashesAndIssuesJwt() {
        AuthRepository repository = mock(AuthRepository.class);
        when(repository.usernameExists("test_user")).thenReturn(false);
        when(repository.insertUser(anyString(), anyString(), anyString(), anyString())).thenReturn(12L);

        SecretKey key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(), "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        AuthService service = new AuthService(repository, passwordEncoder, jwtEncoder);

        AuthService.AuthResponse response = service.register(
                new AuthService.RegisterRequest("Test_User", "password123", "测试训练者"));

        assertEquals(12L, response.user().id());
        assertTrue(response.user().publicId().startsWith("N"));
        assertEquals("test_user", response.user().username());
        assertEquals("12", jwtDecoder.decode(response.accessToken()).getSubject());
        assertFalse(response.refreshToken().isBlank());

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> publicIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> usernameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> displayNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).insertUser(publicIdCaptor.capture(), usernameCaptor.capture(),
                passwordCaptor.capture(), displayNameCaptor.capture());
        String storedPassword = String.valueOf(passwordCaptor.getValue());
        assertTrue(storedPassword.startsWith("$2"));
        assertTrue(passwordEncoder.matches("password123", storedPassword));
        assertEquals(response.user().publicId(), publicIdCaptor.getValue());
        assertEquals("test_user", usernameCaptor.getValue());
        assertEquals("测试训练者", displayNameCaptor.getValue());

        ArgumentCaptor<String> refreshHashCaptor = ArgumentCaptor.forClass(String.class);
        verify(repository).insertRefreshToken(any(), refreshHashCaptor.capture(), any());
        String storedRefreshHash = String.valueOf(refreshHashCaptor.getValue());
        assertEquals(64, storedRefreshHash.length());
        assertFalse(storedRefreshHash.equals(response.refreshToken()));
    }
}
