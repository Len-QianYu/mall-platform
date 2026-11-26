package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.api.dto.ProfileRequests;
import com.example.api.entity.UserEntity;
import com.example.api.service.EmailVerificationCodes;
import com.example.api.service.EmailVerificationCodes.Purpose;
import com.example.api.service.UserService;
import com.example.api.service.VerificationMailSender;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;
import com.example.api.utils.JwtUtils;

import tools.jackson.databind.json.JsonMapper;

// 真正使用 MySQL / Redis / JWT，不发送外部邮件，用户写入回滚，Redis 只清理随机测试命名空间。
@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false", "seckill.expiry-enabled=false" })
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "EMAIL_AUTH_INTEGRATION_TESTS", matches = "true")
class EmailAccountIntegrationTests {
    private static final String PREFIX = "email-auth-test:" + UUID.randomUUID();
    private static final String PASSWORD = "original-password";
    @Autowired private MockMvc mvc;
    @Autowired private UserService users;
    @Autowired private JwtUtils tokens;
    @Autowired private StringRedisTemplate redis;
    @Autowired private EmailVerificationCodes codes;
    @Autowired private JsonMapper json;
    @MockitoBean private VerificationMailSender mail;
    @MockitoBean private Producer producer;
    @MockitoBean private PushConsumer consumer;
    @MockitoBean private OrderPayTimeoutScanTask timeoutScan;
    @MockitoBean private PaymentReconciliationTask reconciliation;
    @MockitoBean private RedissonClient redisson;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) { registry.add("account.email.redis-prefix", () -> PREFIX); }

    @AfterEach
    void cleanup() {
        var keys = this.redis.keys(PREFIX + ":*");
        if (keys != null && !keys.isEmpty()) {
            assertTrue(keys.stream().allMatch(key -> key.startsWith(PREFIX + ":")));
            this.redis.delete(keys);
        }
    }

    @Test
    void bindingEnablesEmailLoginAndResetRevokesPreviousLogins() throws Exception {
        String email = "flow-" + UUID.randomUUID() + "@example.invalid";
        UserEntity user = account(email);
        String originalToken = this.tokens.generateToken(user);
        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", email, "password", PASSWORD, "role", "user"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(false));

        this.mvc.perform(post("/account/email/code").header(HttpHeaders.AUTHORIZATION, "Bearer " + originalToken)
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", email, "password", PASSWORD))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payload.code").doesNotExist());
        ArgumentCaptor<String> bindingCode = ArgumentCaptor.forClass(String.class);
        verify(this.mail).send(eq(email), bindingCode.capture(), eq(Purpose.BIND));
        String linked = this.mvc.perform(post("/account/email/bind").header(HttpHeaders.AUTHORIZATION, "Bearer " + originalToken)
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", email, "password", PASSWORD, "code", bindingCode.getValue()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payload.user.emailVerified").value(true))
            .andExpect(jsonPath("$.payload.user.password").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        String linkedToken = token(linked);
        this.mvc.perform(get("/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + originalToken)).andExpect(status().isUnauthorized());
        this.mvc.perform(get("/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + linkedToken)).andExpect(status().isOk());
        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", email.toUpperCase(), "password", PASSWORD, "role", "user"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true));

        clearCooldown(email);
        this.mvc.perform(post("/account/password-reset/code").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", email))))
            .andExpect(status().isOk());
        ArgumentCaptor<String> resetCode = ArgumentCaptor.forClass(String.class);
        verify(this.mail).send(eq(email), resetCode.capture(), eq(Purpose.RESET));
        this.mvc.perform(post("/account/password-reset").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", email, "code", resetCode.getValue(), "newPassword", "updated-password"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payload.token").doesNotExist());
        this.mvc.perform(get("/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + linkedToken)).andExpect(status().isUnauthorized());
        this.mvc.perform(post("/account/password-reset").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", email, "code", resetCode.getValue(), "newPassword", "another-password"))))
            .andExpect(status().isBadRequest());
        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", email, "password", PASSWORD, "role", "user"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(false));
        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", email, "password", "updated-password", "role", "user"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true));
    }

    @Test
    void verifiedEmailsAreUniqueAndCannotBeOverwrittenThroughProfileEditing() {
        String email = "unique-" + UUID.randomUUID() + "@example.invalid";
        UserEntity first = account(email);
        UserEntity second = account(email);
        assertEquals(1, this.users.bindEmail(first.getId(), email, 0L));
        assertThrows(DuplicateKeyException.class, () -> this.users.bindEmail(second.getId(), email, 0L));
        assertEquals(first.getId(), this.users.fetchByEmail(email.toUpperCase()).orElseThrow().getId());
        assertEquals(0, this.users.updateProfile(first.getId(), new ProfileRequests.Update(
            "changed", null, "attacker@example.invalid", null, null, null, null, null)));
        assertEquals(email, this.users.fetchById(first.getId()).orElseThrow().getEmail());
    }

    @Test
    void codesAreIsolatedExpireAndAllowOnlyOneConcurrentConsumer() throws Exception {
        String email = "codes-" + UUID.randomUUID() + "@example.invalid";
        String code = this.codes.issue(Purpose.BIND, email, 7, 0L);
        assertFalse(this.codes.consume(Purpose.RESET, email, 7, 0L, code));
        assertFalse(this.codes.consume(Purpose.BIND, email, 8, 0L, code));
        assertTrue(this.codes.consume(Purpose.BIND, email, 7, 0L, code));
        assertFalse(this.codes.consume(Purpose.BIND, email, 7, 0L, code));
        code = this.codes.issue(Purpose.BIND, email, 7, 0L);
        String wrong = code.equals("111111") ? "222222" : "111111";
        for (int attempt = 0; attempt < 5; attempt++) assertFalse(this.codes.consume(Purpose.BIND, email, 7, 0L, wrong));
        assertFalse(this.codes.consume(Purpose.BIND, email, 7, 0L, code));
        String expired = this.codes.issue(Purpose.BIND, email, 7, 0L);
        for (String key : this.redis.keys(PREFIX + ":code:BIND:7:*")) this.redis.expire(key, Duration.ofMillis(1));
        Thread.sleep(30);
        assertFalse(this.codes.consume(Purpose.BIND, email, 7, 0L, expired));
        String concurrent = this.codes.issue(Purpose.RESET, email, 7, 0L);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(List.of(
                () -> this.codes.consume(Purpose.RESET, email, 7, 0L, concurrent),
                () -> this.codes.consume(Purpose.RESET, email, 7, 0L, concurrent)));
            int consumed = 0;
            for (var result : results) if (Boolean.TRUE.equals(result.get())) consumed++;
            assertEquals(1, consumed);
        }
    }

    @Test
    void resetCodeDeliveryHasARealCooldown() throws Exception {
        String email = "rate-" + UUID.randomUUID() + "@example.invalid";
        UserEntity user = account(email);
        this.users.bindEmail(user.getId(), email, 0L);
        this.mvc.perform(post("/account/password-reset/code").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", email))))
            .andExpect(status().isOk());
        this.mvc.perform(post("/account/password-reset/code").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", email))))
            .andExpect(status().isTooManyRequests());
    }

    private UserEntity account(String email) {
        UserEntity user = new UserEntity();
        user.setUsername("em_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        user.setPassword(PASSWORD); user.setEmail(email);
        this.users.create(user);
        return user;
    }

    private String body(Object value) { return this.json.writeValueAsString(value); }

    private String token(String response) {
        Map<?, ?> result = this.json.readValue(response, Map.class);
        return ((Map<?, ?>) result.get("payload")).get("token").toString();
    }

    private void clearCooldown(String email) throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8)));
        this.redis.delete(PREFIX + ":cooldown:" + digest);
    }
}
