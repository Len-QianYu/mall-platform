package com.example.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.api.controller.admin.AdminUserController;
import com.example.api.entity.UserEntity;
import com.example.api.security.SecurityConfiguration;
import com.example.api.service.UserService;
import com.example.api.utils.JwtUtils;
import com.example.api.utils.Pagination;

@WebMvcTest({ SecurityController.class, UserController.class, AdminUserController.class })
@Import(SecurityConfiguration.class)
class UserRoleTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService users;

    @MockitoBean
    private PasswordEncoder passwords;

    @MockitoBean
    private JwtUtils tokens;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @ParameterizedTest
    @CsvSource({ "user,user", "admin,admin", "admin,super_admin" })
    void loginKeepsTheStoredRoleAndHidesThePassword(String requestedRole, String actualRole) throws Exception {
        UserEntity user = loginAccount(actualRole);
        when(this.tokens.generateToken(user)).thenReturn("signed-token");

        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(requestedRole)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.payload.user.role").value(actualRole))
            .andExpect(jsonPath("$.payload.user.password").doesNotExist())
            .andExpect(jsonPath("$.payload.token").value("signed-token"));
        verify(this.tokens).generateToken(user);
    }

    @ParameterizedTest
    @CsvSource({ "admin,user", "user,admin", "user,super_admin" })
    void rejectsTheWrongLoginMode(String requestedRole, String actualRole) throws Exception {
        loginAccount(actualRole);

        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(loginBody(requestedRole)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false));
        verifyNoInteractions(this.tokens);
    }

    @Test
    void disabledSuperAdminCannotLogIn() throws Exception {
        loginAccount("super_admin").setStatus(0);

        this.mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
            .content(loginBody("admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false));
        verifyNoInteractions(this.tokens);
    }

    @ParameterizedTest
    @ValueSource(strings = { "admin", "super_admin" })
    void bothAdminRolesCanListAndFilterSuperAdminsUsingTheJwtRoleClaim(String role) throws Exception {
        authenticate(role, 1);
        when(this.users.count(null, "super_admin", null)).thenReturn(1);
        when(this.users.fetch(any(), any(), any(), any(Pagination.class)))
            .thenReturn(List.of(account("super_admin")));

        this.mvc.perform(get("/admin/user").param("role", "super_admin")
            .header(HttpHeaders.AUTHORIZATION, "Bearer role-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.users[0].role").value("super_admin"));
        verify(this.users).count(null, "super_admin", null);
    }

    @Test
    void ordinaryUserCannotAccessUserManagement() throws Exception {
        authenticate("user", 1);

        this.mvc.perform(get("/admin/user").header(HttpHeaders.AUTHORIZATION, "Bearer role-token"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(this.users);
    }

    @Test
    void adminCanAssignTheNewRoleToAnotherUser() throws Exception {
        authenticate("admin", 1);
        when(this.users.update(any(UserEntity.class))).thenReturn(1);

        this.mvc.perform(post("/admin/user/update").header(HttpHeaders.AUTHORIZATION, "Bearer role-token")
            .contentType(MediaType.APPLICATION_JSON).content(editBody("super_admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true));
        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(this.users).update(saved.capture());
        assertEquals("super_admin", saved.getValue().getRole());
    }

    @Test
    void promotedAdminCanSaveTheirProfileWithTheirCurrentStoredRole() throws Exception {
        authenticate("admin", 10);
        when(this.users.fetchById(10)).thenReturn(Optional.of(account("super_admin")));
        when(this.users.update(any(UserEntity.class))).thenReturn(1);

        this.mvc.perform(post("/admin/user/update").header(HttpHeaders.AUTHORIZATION, "Bearer role-token")
            .contentType(MediaType.APPLICATION_JSON).content(editBody("super_admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true));
        verify(this.users).update(any(UserEntity.class));
    }

    @Test
    void superAdminCannotChangeTheirOwnRole() throws Exception {
        authenticate("super_admin", 10);
        when(this.users.fetchById(10)).thenReturn(Optional.of(account("super_admin")));

        this.mvc.perform(post("/admin/user/update").header(HttpHeaders.AUTHORIZATION, "Bearer role-token")
            .contentType(MediaType.APPLICATION_JSON).content(editBody("admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.payload.message").value("不能修改自己的角色"));
        verify(this.users, never()).update(any(UserEntity.class));
    }

    @Test
    void registrationCannotAssignTheSuperAdminRole() throws Exception {
        when(this.users.fetchByUsername("新用户")).thenReturn(Optional.empty());

        this.mvc.perform(post("/user/create").contentType(MediaType.APPLICATION_JSON).content("""
            {"username":"新用户","password":"123456","role":"super_admin"}
            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true));
        ArgumentCaptor<UserEntity> created = ArgumentCaptor.forClass(UserEntity.class);
        verify(this.users).create(created.capture());
        assertEquals("user", created.getValue().getRole());
    }

    private UserEntity loginAccount(String role) {
        UserEntity user = account(role);
        user.setPassword("stored-hash");
        when(this.users.fetchByUsername("千羽")).thenReturn(Optional.of(user));
        when(this.passwords.matches("123456", "stored-hash")).thenReturn(true);
        return user;
    }

    private UserEntity account(String role) {
        UserEntity user = new UserEntity();
        user.setId(10);
        user.setUsername("千羽");
        user.setRole(role);
        return user;
    }

    private void authenticate(String role, int id) {
        Jwt jwt = Jwt.withTokenValue("role-token").header("alg", "RS256").subject("test-admin")
            .claim("role", role).claim("userId", (long) id).build();
        when(this.jwtDecoder.decode("role-token")).thenReturn(jwt);
    }

    private String loginBody(String role) {
        return """
            {"username":"千羽","password":"123456","role":"%s"}
            """.formatted(role);
    }

    private String editBody(String role) {
        return """
            {"id":10,"name":"千羽","role":"%s"}
            """.formatted(role);
    }
}
