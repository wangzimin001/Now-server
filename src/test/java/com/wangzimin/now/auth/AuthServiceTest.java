package com.wangzimin.now.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.KeyHolder;
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
    @SuppressWarnings("unchecked")
    void registerStoresPasswordAndRefreshTokenAsHashesAndIssuesJwt() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        JdbcClient.StatementSpec userStatement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.StatementSpec refreshStatement = mock(JdbcClient.StatementSpec.class);
        when(jdbcClient.sql(anyString())).thenReturn(userStatement, refreshStatement);
        when(userStatement.param(anyString(), any())).thenReturn(userStatement);
        when(refreshStatement.param(anyString(), any())).thenReturn(refreshStatement);
        when(refreshStatement.update()).thenReturn(1);
        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(0);
            keyHolder.getKeyList().add(Map.of("id", 12L));
            return 1;
        }).when(userStatement).update(any(KeyHolder.class), eq("id"));

        SecretKey key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(), "HmacSHA256");
        JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        AuthService service = new AuthService(
                new AuthRepository(jdbcClient), passwordEncoder, jwtEncoder);

        AuthService.AuthResponse response = service.register(
                new AuthService.RegisterRequest("Test_User", "password123", "测试训练者"));

        assertEquals(12L, response.user().id());
        assertEquals("test_user", response.user().username());
        assertEquals("12", jwtDecoder.decode(response.accessToken()).getSubject());
        assertFalse(response.refreshToken().isBlank());

        ArgumentCaptor<Object> passwordCaptor = ArgumentCaptor.forClass(Object.class);
        verify(userStatement).param(eq("passwordHash"), passwordCaptor.capture());
        String storedPassword = String.valueOf(passwordCaptor.getValue());
        assertTrue(storedPassword.startsWith("$2"));
        assertTrue(passwordEncoder.matches("password123", storedPassword));

        ArgumentCaptor<Object> refreshHashCaptor = ArgumentCaptor.forClass(Object.class);
        verify(refreshStatement).param(eq("tokenHash"), refreshHashCaptor.capture());
        String storedRefreshHash = String.valueOf(refreshHashCaptor.getValue());
        assertEquals(64, storedRefreshHash.length());
        assertFalse(storedRefreshHash.equals(response.refreshToken()));
    }
}
