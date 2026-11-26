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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api.dto.ProfileRequests;
import com.example.api.entity.UserEntity;
import com.example.api.mapper.UserMapper;
import com.example.api.service.EmailVerificationCodes;
import com.example.api.service.EmailVerificationCodes.Purpose;
import com.example.api.service.UserService;
import com.example.api.service.VerificationMailSender;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;
import com.example.api.utils.JwtUtils;

import tools.jackson.databind.json.JsonMapper;

// 真正使用 MySQL / Redis / JWT，不发送外部邮件；普通用例回滚，并发用例清理临时账号。
@SpringBootTest(properties = {
    "rustfs.endpoint=http://127.0.0.1:1", "alipay.enabled=false", "seckill.expiry-enabled=false",
    "debug=false", "logging.level.root=WARN"
})
@AutoConfigureMockMvc
@Transactional
@EnabledIfEnvironmentVariable(named = "EMAIL_AUTH_INTEGRATION_TESTS", matches = "true")
class EmailAccountIntegrationTests {
    private static final String PREFIX = "email-auth-test:" + UUID.randomUUID();
    private static final String PASSWORD = "original-password";
    @Autowired private MockMvc mvc;
    @Autowired private UserService users;
    @Autowired private UserMapper userMapper;
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
        this.mvc.perform(post("/account/email/bind").header(HttpHeaders.AUTHORIZATION, "Bearer " + linkedToken)
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", email, "password", PASSWORD, "code", bindingCode.getValue()))))
            .andExpect(status().isBadRequest());
        assertEquals(1L, this.users.fetchById(user.getId()).orElseThrow().getAuthVersion().longValue());
        this.mvc.perform(get("/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + originalToken)).andExpect(status().isUnauthorized());
        this.mvc.perform(get("/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + linkedToken)).andExpect(status().isOk());
        String emailLogin = this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", email.toUpperCase(), "password", PASSWORD, "role", "user"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true))
            .andReturn().getResponse().getContentAsString();
        String usernameLogin = this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("username", user.getUsername(), "password", PASSWORD, "role", "user"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true))
            .andReturn().getResponse().getContentAsString();

        clearCooldown(email);
        this.mvc.perform(post("/account/password-reset/code").contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", email))))
            .andExpect(status().isOk());
        ArgumentCaptor<String> resetCode = ArgumentCaptor.forClass(String.class);
        verify(this.mail).send(eq(email), resetCode.capture(), eq(Purpose.RESET));
        this.mvc.perform(post("/account/password-reset").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", email, "code", resetCode.getValue(), "newPassword", "updated-password"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payload.token").doesNotExist());
        for (String oldToken : List.of(originalToken, linkedToken, token(emailLogin), token(usernameLogin))) {
            this.mvc.perform(get("/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
        }
        this.mvc.perform(post("/account/password-reset").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", email, "code", resetCode.getValue(), "newPassword", "another-password"))))
            .andExpect(status().isBadRequest());
        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", email, "password", PASSWORD, "role", "user"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(false));
        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("username", user.getUsername(), "password", PASSWORD, "role", "user"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(false));
        String newLogin = this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", email, "password", "updated-password", "role", "user"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true))
            .andReturn().getResponse().getContentAsString();
        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("username", user.getUsername(), "password", "updated-password", "role", "user"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true));
        this.mvc.perform(get("/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(newLogin)))
            .andExpect(status().isOk());
        assertEquals(2L, this.users.fetchById(user.getId()).orElseThrow().getAuthVersion().longValue());
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
    void bindingRejectsAnOccupiedEmailIgnoringCaseAndWhitespace() throws Exception {
        String email = "occupied-" + UUID.randomUUID() + "@example.invalid";
        UserEntity owner = account(email);
        UserEntity other = account(email.toUpperCase());
        this.users.bindEmail(owner.getId(), email, 0L);
        String otherToken = this.tokens.generateToken(other);
        String code = this.codes.issue(Purpose.BIND, email, other.getId(), 0L);
        String alias = "  " + email.toUpperCase() + "  ";
        this.mvc.perform(post("/account/email/code").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", alias, "password", PASSWORD))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(false));
        this.mvc.perform(post("/account/email/bind").header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", alias, "password", PASSWORD, "code", code))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.status").value(false));
        assertEquals(owner.getId(), this.users.fetchByEmail(email).orElseThrow().getId());
        UserEntity unchanged = this.users.fetchById(other.getId()).orElseThrow();
        assertFalse(unchanged.getEmailVerified());
        assertEquals(0L, unchanged.getAuthVersion().longValue());
    }

    @Test
    void rebindingRevokesOldEmailRecoveryAndPreviousTokens() throws Exception {
        String oldEmail = "before-" + UUID.randomUUID() + "@example.invalid";
        String newEmail = "after-" + UUID.randomUUID() + "@example.invalid";
        UserEntity user = account(oldEmail);
        this.users.bindEmail(user.getId(), oldEmail, 0L);
        String oldToken = this.tokens.generateToken(this.users.fetchById(user.getId()).orElseThrow());
        String oldResetCode = this.codes.issue(Purpose.RESET, oldEmail, user.getId(), 1L);
        String newBindingCode = this.codes.issue(Purpose.BIND, newEmail, user.getId(), 1L);

        String bound = this.mvc.perform(post("/account/email/bind").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken)
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of(
                "email", " " + newEmail.toUpperCase() + " ", "password", PASSWORD, "code", newBindingCode))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.payload.user.email").value(newEmail))
            .andReturn().getResponse().getContentAsString();
        assertTrue(this.users.fetchByEmail(oldEmail).isEmpty());
        assertEquals(user.getId(), this.users.fetchByEmail(newEmail).orElseThrow().getId());
        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", oldEmail, "password", PASSWORD, "role", "user"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(false));
        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email", newEmail, "password", PASSWORD, "role", "user"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true));
        for (String email : List.of(oldEmail, newEmail)) {
            this.mvc.perform(post("/account/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "code", oldResetCode, "newPassword", "unexpected-password"))))
                .andExpect(status().isBadRequest());
        }
        this.mvc.perform(get("/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
            .andExpect(status().isUnauthorized());
        this.mvc.perform(get("/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(bound)))
            .andExpect(status().isOk());
        assertEquals(2L, this.users.fetchById(user.getId()).orElseThrow().getAuthVersion().longValue());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentAccountsCanBindTheSameEmailOnlyOnce() throws Exception {
        String email = "race-bind-" + UUID.randomUUID() + "@example.invalid";
        List<UserEntity> accounts = new ArrayList<>();
        try {
            UserEntity first = account(email);
            accounts.add(first);
            UserEntity second = account(email.toUpperCase());
            accounts.add(second);
            String firstToken = this.tokens.generateToken(first);
            String secondToken = this.tokens.generateToken(second);
            String firstCode = this.codes.issue(Purpose.BIND, email, first.getId(), 0L);
            String secondCode = this.codes.issue(Purpose.BIND, email, second.getId(), 0L);
            var results = concurrently(
                () -> this.mvc.perform(post("/account/email/bind").header(HttpHeaders.AUTHORIZATION, "Bearer " + firstToken)
                    .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", email, "password", PASSWORD, "code", firstCode)))).andReturn(),
                () -> this.mvc.perform(post("/account/email/bind").header(HttpHeaders.AUTHORIZATION, "Bearer " + secondToken)
                    .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("email", " " + email.toUpperCase() + " ", "password", PASSWORD, "code", secondCode)))).andReturn());
            assertEquals(List.of(200, 409), results.stream().map(result -> result.getResponse().getStatus()).sorted().toList());
            assertEquals(1L, this.userMapper.selectCount(Wrappers.<UserEntity>query().eq("login_email", email)).longValue());
            int winner = results.get(0).getResponse().getStatus() == 200 ? 0 : 1;
            UserEntity bound = this.users.fetchById(accounts.get(winner).getId()).orElseThrow();
            UserEntity rejected = this.users.fetchById(accounts.get(1 - winner).getId()).orElseThrow();
            assertTrue(bound.getEmailVerified());
            assertEquals(email, bound.getEmail());
            assertEquals(1L, bound.getAuthVersion().longValue());
            assertFalse(rejected.getEmailVerified());
            assertEquals(0L, rejected.getAuthVersion().longValue());
            String oldWinnerToken = winner == 0 ? firstToken : secondToken;
            this.mvc.perform(get("/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldWinnerToken))
                .andExpect(status().isUnauthorized());
            this.mvc.perform(get("/user/profile").header(HttpHeaders.AUTHORIZATION,
                "Bearer " + token(results.get(winner).getResponse().getContentAsString())))
                .andExpect(status().isOk());
        } finally {
            for (UserEntity user : accounts) this.userMapper.deleteById(user.getId());
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentResetRequestsChangeThePasswordOnlyOnce() throws Exception {
        String email = "race-reset-" + UUID.randomUUID() + "@example.invalid";
        UserEntity user = account(email);
        try {
            this.users.bindEmail(user.getId(), email, 0L);
            String oldToken = this.tokens.generateToken(this.users.fetchById(user.getId()).orElseThrow());
            String code = this.codes.issue(Purpose.RESET, email, user.getId(), 1L);
            List<String> passwords = List.of("first-reset-password", "second-reset-password");
            var results = concurrently(
                () -> this.mvc.perform(post("/account/password-reset").contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("email", email, "code", code, "newPassword", passwords.get(0))))).andReturn(),
                () -> this.mvc.perform(post("/account/password-reset").contentType(MediaType.APPLICATION_JSON)
                    .content(body(Map.of("email", email, "code", code, "newPassword", passwords.get(1))))).andReturn());
            assertEquals(List.of(200, 400), results.stream().map(result -> result.getResponse().getStatus()).sorted().toList());
            assertEquals(2L, this.users.fetchById(user.getId()).orElseThrow().getAuthVersion().longValue());
            int winner = results.get(0).getResponse().getStatus() == 200 ? 0 : 1;
            String login = this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("username", user.getUsername(), "password", passwords.get(winner), "role", "user"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true))
                .andReturn().getResponse().getContentAsString();
            this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "password", passwords.get(1 - winner), "role", "user"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(false));
            this.mvc.perform(post("/account/password-reset").contentType(MediaType.APPLICATION_JSON)
                .content(body(Map.of("email", email, "code", code, "newPassword", "replayed-password"))))
                .andExpect(status().isBadRequest());
            this.mvc.perform(get("/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
                .andExpect(status().isUnauthorized());
            this.mvc.perform(get("/user/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(login)))
                .andExpect(status().isOk());
        } finally {
            this.userMapper.deleteById(user.getId());
        }
    }

    @Test
    void codesAreIsolatedExpireAndAllowOnlyOneConcurrentConsumer() throws Exception {
        String email = "codes-" + UUID.randomUUID() + "@example.invalid";
        String code = this.codes.issue(Purpose.BIND, email, 7, 0L);
        assertFalse(this.codes.consume(Purpose.RESET, email, 7, 0L, code));
        assertFalse(this.codes.consume(Purpose.BIND, email, 8, 0L, code));
        assertTrue(this.codes.consume(Purpose.BIND, email, 7, 0L, code));
        assertFalse(this.codes.consume(Purpose.BIND, email, 7, 0L, code));
        String stale = this.codes.issue(Purpose.RESET, email, 7, 0L);
        assertFalse(this.codes.consume(Purpose.RESET, email, 7, 1L, stale));
        assertFalse(this.codes.consume(Purpose.RESET, email, 7, 0L, stale));
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

    private List<MvcResult> concurrently(Callable<MvcResult> first, Callable<MvcResult> second) throws Exception {
        var start = new CyclicBarrier(2);
        List<Callable<MvcResult>> requests = List.of(
            () -> { start.await(10, TimeUnit.SECONDS); return first.call(); },
            () -> { start.await(10, TimeUnit.SECONDS); return second.call(); });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var results = executor.invokeAll(requests, 30, TimeUnit.SECONDS);
            return List.of(results.get(0).get(), results.get(1).get());
        }
    }

    private void clearCooldown(String email) throws Exception {
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8)));
        this.redis.delete(PREFIX + ":cooldown:" + digest);
    }
}
