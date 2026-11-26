package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.mail.javamail.JavaMailSenderImpl;

// 只校验 SMTP TLS 连接与认证，不发送任何邮件，不输出发信账号和授权码。
@EnabledIfEnvironmentVariable(named = "SMTP_CONNECTION_TESTS", matches = "true")
class SmtpConnectionTests {
    @Test
    void authenticatesToTheConfiguredSmtpServerWithoutSendingMail() throws Exception {
        Properties settings = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(".env"), StandardCharsets.UTF_8)) { settings.load(reader); }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.getProperty("MAIL_HOST", "smtp.qq.com"));
        sender.setPort(Integer.parseInt(settings.getProperty("MAIL_PORT", "465")));
        sender.setUsername(settings.getProperty("MAIL_USERNAME", ""));
        sender.setPassword(settings.getProperty("MAIL_PASSWORD", ""));
        Properties transport = sender.getJavaMailProperties();
        transport.setProperty("mail.smtp.auth", "true");
        transport.setProperty("mail.smtp.ssl.enable", settings.getProperty("MAIL_SSL_ENABLED", "true"));
        transport.setProperty("mail.smtp.starttls.enable", settings.getProperty("MAIL_STARTTLS_ENABLED", "false"));
        transport.setProperty("mail.smtp.connectiontimeout", "5000");
        transport.setProperty("mail.smtp.timeout", "5000");
        transport.setProperty("mail.smtp.writetimeout", "5000");
        transport.setProperty("mail.debug", "false");
        try { sender.testConnection(); }
        catch (Exception error) { fail("SMTP connection/authentication failed: " + error.getClass().getSimpleName()); }
    }
}
