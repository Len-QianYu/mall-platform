package com.example.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.example.api.exception.AccountFlowException;
import com.example.api.service.EmailVerificationCodes.Purpose;

@Service
public class VerificationMailSender {
    private static final Logger log = LoggerFactory.getLogger(VerificationMailSender.class);
    private final JavaMailSender sender;
    private final String from;
    private final boolean configured;

    public VerificationMailSender(JavaMailSender sender,
        @Value("${spring.mail.username:}") String username,
        @Value("${spring.mail.password:}") String password,
        @Value("${account.email.from:}") String from) {
        this.sender = sender;
        this.from = from;
        this.configured = !username.isBlank() && !password.isBlank() && !from.isBlank();
    }

    public void requireConfigured() {
        if (!this.configured) throw new AccountFlowException(HttpStatus.SERVICE_UNAVAILABLE,
            "邮件服务尚未配置，请联系管理员");
    }

    public void send(String email, String code, Purpose purpose) {
        this.requireConfigured();
        try {
            var message = this.sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(this.from, "Prompt Pals");
            helper.setTo(email);
            helper.setSubject(purpose == Purpose.BIND ? "Prompt Pals 邮箱绑定验证码" : "Prompt Pals 密码重置验证码");
            helper.setText("你的验证码是：" + code + "\n\n"
                + "验证码 10 分钟内有效，只能使用一次。请在商城页面输入验证码完成"
                + (purpose == Purpose.BIND ? "邮箱绑定。" : "密码重置。")
                + "\n如果不是你本人操作，请忽略这封邮件，并且不要向他人提供验证码。", false);
            this.sender.send(message);
        } catch (Exception error) {
            // 只记录异常类型，邮件正文、验证码和 SMTP 授权码不写入日志。
            log.warn("邮箱验证码发送失败：{}", error.getClass().getSimpleName());
            throw new AccountFlowException(HttpStatus.SERVICE_UNAVAILABLE, "邮件发送失败，请稍后重试");
        }
    }
}
