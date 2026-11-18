package com.example.api.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

import com.example.api.dto.ProfileRequests;
import com.example.api.entity.UserEntity;
import com.example.api.security.SecurityConfiguration;
import com.example.api.service.UserService;

@WebMvcTest(UserController.class)
@Import(SecurityConfiguration.class)
class UserProfileTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private UserService users;

    @MockitoBean
    private PasswordEncoder passwords;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @ParameterizedTest
    @ValueSource(strings = { "user", "admin", "super_admin" })
    void returnsTheSavedLocationAndAddressForTheAuthenticatedAccount(String role) throws Exception {
        authenticate(role);
        UserEntity user = new UserEntity();
        user.setId(7);
        user.setUsername("profile-user");
        user.setRole(role);
        user.setProvince("北京市");
        user.setCity("海淀区");
        user.setReceiverAddress("北京市海淀区中关村大街1号");
        user.setPassword("stored-hash");
        when(this.users.fetchById(7)).thenReturn(Optional.of(user));

        this.mvc.perform(get("/user/profile")
            .header(HttpHeaders.AUTHORIZATION, "Bearer profile-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.user.province").value("北京市"))
            .andExpect(jsonPath("$.payload.user.city").value("海淀区"))
            .andExpect(jsonPath("$.payload.user.receiverAddress").value("北京市海淀区中关村大街1号"))
            .andExpect(jsonPath("$.payload.user.password").doesNotExist());
        verify(this.users).fetchById(7);
    }

    @ParameterizedTest
    @ValueSource(strings = { "user", "admin", "super_admin" })
    void savesTheLocationAndAddressForTheJwtAccountWhileIgnoringClientIdentityFields(String role) throws Exception {
        authenticate(role);
        when(this.users.updateProfile(eq(7), any(ProfileRequests.Update.class))).thenReturn(1);

        this.mvc.perform(post("/user/update")
            .header(HttpHeaders.AUTHORIZATION, "Bearer profile-token")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"id":999,"role":"super_admin","province":"浙江省","city":"杭州市",
                 "receiverAddress":"浙江省杭州市西湖区文三路1号"}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true));

        ArgumentCaptor<ProfileRequests.Update> saved = ArgumentCaptor.forClass(ProfileRequests.Update.class);
        verify(this.users).updateProfile(eq(7), saved.capture());
        assertEquals("浙江省", saved.getValue().province());
        assertEquals("杭州市", saved.getValue().city());
        assertEquals("浙江省杭州市西湖区文三路1号", saved.getValue().receiverAddress());
    }

    @Test
    void allowsClearingTheOptionalLocationAndAddress() throws Exception {
        authenticate("user");
        when(this.users.updateProfile(eq(7), any(ProfileRequests.Update.class))).thenReturn(1);

        this.mvc.perform(post("/user/update")
            .header(HttpHeaders.AUTHORIZATION, "Bearer profile-token")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"province":null,"city":null,"receiverAddress":null}
                """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true));

        ArgumentCaptor<ProfileRequests.Update> saved = ArgumentCaptor.forClass(ProfileRequests.Update.class);
        verify(this.users).updateProfile(eq(7), saved.capture());
        assertNull(saved.getValue().province());
        assertNull(saved.getValue().city());
        assertNull(saved.getValue().receiverAddress());
    }

    @Test
    void anonymousRequestsCannotReadOrChangeProfileLocations() throws Exception {
        this.mvc.perform(get("/user/profile")).andExpect(status().isUnauthorized());
        this.mvc.perform(post("/user/update").contentType(MediaType.APPLICATION_JSON)
            .content("{\"province\":\"浙江省\",\"city\":\"杭州市\"}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(this.users);
    }

    private void authenticate(String role) {
        Jwt jwt = Jwt.withTokenValue("profile-token").header("alg", "RS256").subject("profile-user")
            .claim("role", role).claim("userId", 7L).build();
        when(this.jwtDecoder.decode("profile-token")).thenReturn(jwt);
    }
}
