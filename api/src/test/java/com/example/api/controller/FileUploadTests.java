package com.example.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.api.exception.FileStorageExceptionHandler;
import com.example.api.security.SecurityConfiguration;
import com.example.api.service.FileStorageService;
import com.example.api.service.FileStorageService.StoredFile;
import com.example.api.service.UserService;

@WebMvcTest({ UploadController.class, FileController.class })
@Import({ SecurityConfiguration.class, FileStorageExceptionHandler.class })
class FileUploadTests {

    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a/p8AAAAASUVORK5CYII=");
    private static final String FILENAME = "18d17b9c-61ab-44ec-8573-3ce20dcaad49.png";
    private static final Instant MODIFIED = Instant.parse("2026-11-01T00:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FileStorageService storage;

    @MockitoBean
    private UserService users;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void avatarUploadRequiresLogin() throws Exception {
        this.mvc.perform(multipart("/upload/avatars").file(png()))
            .andExpect(status().isUnauthorized());
        verify(this.storage, never()).store(any(), any(), any());
        verifyNoInteractions(this.users);
    }

    @Test
    void productImageUploadRequiresAdmin() throws Exception {
        this.mvc.perform(multipart("/upload/images").file(png()).with(jwt()
            .authorities(new SimpleGrantedAuthority("ROLE_user"))))
            .andExpect(status().isForbidden());
        verify(this.storage, never()).store(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "admin", "super_admin" })
    void adminUploadUsesDetectedFormatAndKeepsRelativeUrl(String role) throws Exception {
        when(this.storage.store(eq("images"), any(), eq("png"))).thenReturn("/images/" + FILENAME);

        this.mvc.perform(multipart("/upload/images").file(png()).with(jwt()
            .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.payload.url").value("/images/" + FILENAME));
        verify(this.storage).store(eq("images"), any(), eq("png"));
        verifyNoInteractions(this.users);
    }

    @ParameterizedTest
    @ValueSource(strings = { "user", "admin", "super_admin" })
    void avatarUploadSavesTheUrlForTheJwtAccountWithoutChangingOtherProfileFields(String role) throws Exception {
        when(this.storage.store(eq("avatars"), any(), eq("png"))).thenReturn("/avatars/" + FILENAME);
        when(this.users.updateAvatar(7, "/avatars/" + FILENAME)).thenReturn(1);

        this.mvc.perform(multipart("/upload/avatars").file(png())
            .param("userId", "999").param("name", "未保存的名字")
            .with(jwt().jwt(token -> token.claim("userId", 7L))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(true))
            .andExpect(jsonPath("$.payload.url").value("/avatars/" + FILENAME));
        verify(this.users).updateAvatar(7, "/avatars/" + FILENAME);
        verify(this.users, never()).updateProfile(any(), any());
    }

    @Test
    void missingAccountDoesNotReportAvatarUploadSuccess() throws Exception {
        when(this.storage.store(eq("avatars"), any(), eq("png"))).thenReturn("/avatars/" + FILENAME);
        when(this.users.updateAvatar(7, "/avatars/" + FILENAME)).thenReturn(0);

        this.mvc.perform(multipart("/upload/avatars").file(png())
            .with(jwt().jwt(token -> token.claim("userId", 7L))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.payload.message").value("用户不存在，头像保存失败"))
            .andExpect(jsonPath("$.payload.url").doesNotExist());
    }

    @Test
    void svgDisguisedAsPngIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "fake.png", "image/png",
            "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes());

        this.mvc.perform(multipart("/upload/avatars").file(file).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false));
        verify(this.storage, never()).store(any(), any(), any());
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        this.mvc.perform(multipart("/upload/avatars")
            .file(new MockMultipartFile("file", new byte[0])).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false));
        verify(this.storage, never()).store(any(), any(), any());
    }

    @Test
    void unsupportedDirectoryIsRejected() throws Exception {
        this.mvc.perform(multipart("/upload/private").file(png()).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(false));
        verify(this.storage, never()).store(any(), any(), any());
    }

    @Test
    void anonymousImageDownloadReturnsOriginalBytesAndClosesStream() throws Exception {
        ByteArrayInputStream input = image("images");

        this.mvc.perform(get("/images/" + FILENAME))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(PNG))
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, PNG.length))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=2592000, public"))
            .andExpect(header().string(HttpHeaders.ETAG, "\"image-etag\""))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        verify(input).close();
    }

    @Test
    void conditionalAvatarRequestReturns304AndClosesStream() throws Exception {
        ByteArrayInputStream input = image("avatars");

        this.mvc.perform(get("/avatars/" + FILENAME).header(HttpHeaders.IF_NONE_MATCH, "\"image-etag\""))
            .andExpect(status().isNotModified())
            .andExpect(content().bytes(new byte[0]));
        verify(input).close();
    }

    @Test
    void headRequestReturnsMetadataAndClosesStream() throws Exception {
        ByteArrayInputStream input = image("images");

        this.mvc.perform(head("/images/" + FILENAME))
            .andExpect(status().isOk())
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, PNG.length))
            .andExpect(content().bytes(new byte[0]));
        verify(input).close();
    }

    @Test
    void missingImageReturns404() throws Exception {
        when(this.storage.load("images", FILENAME)).thenReturn(Optional.empty());

        this.mvc.perform(get("/images/" + FILENAME)).andExpect(status().isNotFound());
    }

    @Test
    void unavailableStorageReturns503OnUpload() throws Exception {
        when(this.storage.store(any(), any(), any())).thenThrow(new IOException("storage offline"));

        this.mvc.perform(multipart("/upload/avatars").file(png()).with(jwt()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(false))
            .andExpect(jsonPath("$.payload.message").value("文件服务暂不可用，请稍后重试"));
        verifyNoInteractions(this.users);
    }

    @Test
    void unavailableStorageReturns503OnDownload() throws Exception {
        when(this.storage.load("images", FILENAME)).thenThrow(new IOException("storage offline"));

        this.mvc.perform(get("/images/" + FILENAME))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(false));
    }

    private ByteArrayInputStream image(String directory) throws IOException {
        ByteArrayInputStream input = spy(new ByteArrayInputStream(PNG));
        when(this.storage.load(directory, FILENAME)).thenReturn(Optional.of(
            new StoredFile(input, "image/png", PNG.length, "\"image-etag\"", MODIFIED)));
        return input;
    }

    private static MockMultipartFile png() {
        // 客户端文件名和 Content-Type 故意错误，服务端应按 PNG 魔数识别。
        return new MockMultipartFile("file", "../../misleading.html", "text/html", PNG);
    }

}
