package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.example.api.service.FileStorageService.StoredFile;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

class FileStorageServiceImplTests {

    private static final String BUCKET = "test-uploads";
    private static final byte[] IMAGE = { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3 };

    private S3Client client;
    private FileStorageServiceImpl storage;

    @BeforeEach
    void setUp() {
        // 执行 SDK 的 Consumer 重载，捕获最终的 S3 请求，不发网络请求。
        this.client = mock(S3Client.class, CALLS_REAL_METHODS);
        this.storage = new FileStorageServiceImpl(this.client, BUCKET);
        doReturn(HeadBucketResponse.builder().build()).when(this.client).headBucket(any(HeadBucketRequest.class));
        doReturn(CreateBucketResponse.builder().build()).when(this.client).createBucket(any(CreateBucketRequest.class));
        doReturn(PutObjectResponse.builder().build()).when(this.client)
            .putObject(any(PutObjectRequest.class), any(RequestBody.class));
        doReturn(DeleteObjectResponse.builder().build()).when(this.client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void storesOriginalBytesWithSafeKeyAndDetectedContentType() throws Exception {
        ByteArrayInputStream input = spy(new ByteArrayInputStream(IMAGE));
        MockMultipartFile file = new MockMultipartFile("file", "../../fake.html", "text/html", IMAGE) {
            @Override
            public InputStream getInputStream() {
                return input;
            }
        };
        doAnswer(invocation -> {
            RequestBody body = invocation.getArgument(1);
            assertEquals(IMAGE.length, body.optionalContentLength().orElseThrow());
            assertArrayEquals(IMAGE, body.contentStreamProvider().newStream().readAllBytes());
            return PutObjectResponse.builder().build();
        }).when(this.client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        String url = this.storage.store("avatars", file, "jpg");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(this.client).putObject(request.capture(), any(RequestBody.class));
        assertTrue(url.matches("/avatars/[0-9a-f-]{36}\\.jpg"));
        assertEquals(url.substring(1), request.getValue().key());
        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals("image/jpeg", request.getValue().contentType());
        verify(input).close();
    }

    @Test
    void replaysLargeMultipartStreamsForSigningAndRetries() throws Exception {
        byte[] image = new byte[2 * 1024 * 1024];
        InputStream input = spy(new FilterInputStream(new ByteArrayInputStream(image)) {
            @Override
            public boolean markSupported() {
                return false;
            }

            @Override
            public void mark(int readLimit) {
                // Servlet multipart streams may be backed by files and cannot be rewound.
            }

            @Override
            public void reset() throws IOException {
                throw new IOException("Multipart stream cannot be reset");
            }
        });
        MockMultipartFile file = new MockMultipartFile("file", "large.png", "image/png", image) {
            @Override
            public InputStream getInputStream() {
                return input;
            }
        };
        doAnswer(invocation -> {
            RequestBody body = invocation.getArgument(1);
            assertEquals(image.length, body.optionalContentLength().orElseThrow());
            try (InputStream first = body.contentStreamProvider().newStream()) {
                assertArrayEquals(image, first.readAllBytes());
            }
            try (InputStream retry = body.contentStreamProvider().newStream()) {
                assertArrayEquals(image, retry.readAllBytes());
            }
            return PutObjectResponse.builder().build();
        }).when(this.client).putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertTrue(this.storage.store("images", file, "png").startsWith("/images/"));
        verify(input).close();
    }

    @Test
    void createsMissingBucketOnceBeforeUploads() throws Exception {
        doThrow(s3Error(404)).when(this.client).headBucket(any(HeadBucketRequest.class));

        this.storage.store("images", file(), "jpg");
        this.storage.store("avatars", file(), "jpg");

        verify(this.client, times(1)).headBucket(any(HeadBucketRequest.class));
        ArgumentCaptor<CreateBucketRequest> request = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(this.client, times(1)).createBucket(request.capture());
        assertEquals(BUCKET, request.getValue().bucket());
        verify(this.client, times(2)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void keepsExistingBucketAndDoesNotCreateAnother() throws Exception {
        this.storage.store("images", file(), "jpg");
        this.storage.store("images", file(), "jpg");

        verify(this.client, times(1)).headBucket(any(HeadBucketRequest.class));
        verify(this.client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void retriesBucketInitializationAfterConnectionFailure() throws Exception {
        doThrow(SdkClientException.create("offline"))
            .doReturn(HeadBucketResponse.builder().build())
            .when(this.client).headBucket(any(HeadBucketRequest.class));

        assertThrows(IOException.class, () -> this.storage.store("avatars", file(), "jpg"));
        assertTrue(this.storage.store("avatars", file(), "jpg").startsWith("/avatars/"));
        verify(this.client, times(2)).headBucket(any(HeadBucketRequest.class));
        verify(this.client, times(1)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void rechecksBucketAccessAfterConcurrentCreation() throws Exception {
        doThrow(s3Error(404)).doReturn(HeadBucketResponse.builder().build())
            .when(this.client).headBucket(any(HeadBucketRequest.class));
        doThrow(s3Error(409)).when(this.client).createBucket(any(CreateBucketRequest.class));

        this.storage.store("images", file(), "jpg");

        verify(this.client, times(2)).headBucket(any(HeadBucketRequest.class));
        verify(this.client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void deniedBucketAccessDoesNotAttemptCreationOrUpload() {
        doThrow(s3Error(403)).when(this.client).headBucket(any(HeadBucketRequest.class));

        assertThrows(IOException.class, () -> this.storage.store("images", file(), "jpg"));
        verify(this.client, never()).createBucket(any(CreateBucketRequest.class));
        verify(this.client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void invalidKeysAndFormatsDoNotReachStorage() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> this.storage.store("../private", file(), "jpg"));
        assertThrows(IllegalArgumentException.class, () -> this.storage.store("images", file(), "svg"));
        assertTrue(this.storage.load("images", "../secret.jpg").isEmpty());
        assertTrue(this.storage.load("private", "a.jpg").isEmpty());
        assertTrue(this.storage.load("avatars", "a.html").isEmpty());
        verifyNoInteractions(this.client);
    }

    @Test
    void loadsBytesAndMetadataAndClosesResponse() throws Exception {
        ByteArrayInputStream input = spy(new ByteArrayInputStream(IMAGE));
        Instant modified = Instant.parse("2026-11-01T00:00:00Z");
        GetObjectResponse metadata = GetObjectResponse.builder().contentType("image/jpeg")
            .contentLength((long) IMAGE.length).eTag("\"etag\"").lastModified(modified).build();
        doReturn(new ResponseInputStream<>(metadata, input)).when(this.client)
            .getObject(any(GetObjectRequest.class));

        try (StoredFile file = this.storage.load("images", "test.jpg").orElseThrow()) {
            assertArrayEquals(IMAGE, file.content().readAllBytes());
            assertEquals("image/jpeg", file.contentType());
            assertEquals(IMAGE.length, file.contentLength());
            assertEquals("\"etag\"", file.eTag());
            assertEquals(modified, file.lastModified());
        }
        ArgumentCaptor<GetObjectRequest> request = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(this.client).getObject(request.capture());
        assertEquals("images/test.jpg", request.getValue().key());
        assertEquals(BUCKET, request.getValue().bucket());
        verify(input).close();
    }

    @Test
    void missingObjectReturnsEmpty() throws Exception {
        doThrow(s3Error(404)).when(this.client).getObject(any(GetObjectRequest.class));

        assertFalse(this.storage.load("images", "missing.jpg").isPresent());
    }

    @Test
    void failedDownloadKeepsStorageErrorInsteadOfReportingMissingObject() {
        doThrow(SdkClientException.create("offline")).when(this.client).getObject(any(GetObjectRequest.class));

        assertThrows(IOException.class, () -> this.storage.load("images", "test.jpg"));
    }

    @Test
    void deletesTheObjectKeyFromItsRelativeImageUrlWithoutCreatingABucket() throws Exception {
        this.storage.delete("/images/previous.webp");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(this.client).deleteObject(request.capture());
        assertEquals(BUCKET, request.getValue().bucket());
        assertEquals("images/previous.webp", request.getValue().key());
        verify(this.client, never()).headBucket(any(HeadBucketRequest.class));
        verify(this.client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "", "images/a.png", "/private/a.png", "/images/../secret.png",
        "/images/a.png?version=1", "/images/a.png#fragment", "/images/a.svg", "//images/a.png",
        "https://example.com/images/a.png", "/images/%2e%2e.png" })
    void unmanagedOrInvalidDeletionUrlsDoNotReachRustfs(String url) throws Exception {
        this.storage.delete(url);

        verifyNoInteractions(this.client);
    }

    @Test
    void deletingAnAlreadyMissingObjectIsSuccessful() throws Exception {
        doThrow(s3Error(404)).when(this.client).deleteObject(any(DeleteObjectRequest.class));

        this.storage.delete("/images/missing.png");

        verify(this.client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void failedDeletionKeepsTheStorageError() {
        doThrow(SdkClientException.create("offline")).when(this.client).deleteObject(any(DeleteObjectRequest.class));

        assertThrows(IOException.class, () -> this.storage.delete("/images/previous.png"));
    }

    @Test
    void deniedDeletionKeepsTheStorageError() {
        doThrow(s3Error(403)).when(this.client).deleteObject(any(DeleteObjectRequest.class));

        assertThrows(IOException.class, () -> this.storage.delete("/images/previous.png"));
    }

    private static MockMultipartFile file() {
        return new MockMultipartFile("file", "image.jpg", "image/jpeg", IMAGE);
    }

    private static S3Exception s3Error(int status) {
        S3Exception.Builder builder = S3Exception.builder();
        builder.statusCode(status);
        builder.message("S3 request failed");
        return (S3Exception) builder.build();
    }

}
