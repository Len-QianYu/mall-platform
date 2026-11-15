package com.example.api.configuration;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

// RustFS 的 S3 客户端：整个应用共用一个，关闭应用时释放 HTTP 客户端。
@Configuration
public class UploadConfiguration {

    // 允许的上传用途，同时是对象键和访问路径的前缀：用户头像、商品图片
    public static final Set<String> DIRECTORIES = Set.of("avatars", "images");

    @Bean(destroyMethod = "close")
    public S3Client rustfsClient(
        @Value("${rustfs.endpoint}") URI endpoint,
        @Value("${rustfs.access-key}") String accessKey,
        @Value("${rustfs.secret-key}") String secretKey,
        @Value("${rustfs.region}") String region
    ) {
        return S3Client.builder()
            .endpointOverride(endpoint)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .region(Region.of(region))
            .forcePathStyle(true)
            // 使用已知长度的普通 PUT，避免 S3 兼容服务对 aws-chunked / 可选校验和的实现差异。
            .serviceConfiguration(S3Configuration.builder().chunkedEncodingEnabled(false).build())
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .httpClientBuilder(UrlConnectionHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(3))
                .socketTimeout(Duration.ofSeconds(10)))
            .overrideConfiguration(configuration -> configuration
                .apiCallAttemptTimeout(Duration.ofSeconds(10))
                .apiCallTimeout(Duration.ofSeconds(30)))
            .build();
    }

}
