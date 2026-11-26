package com.example.api.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.example.api.exception.AccountFlowException;

@Service
public class EmailVerificationCodes {
    public enum Purpose { BIND, RESET }

    public static final int VALIDITY_SECONDS = 600;
    public static final int RESEND_SECONDS = 60;

    // 邮箱重发冷却与每小时邮箱/IP 配额在同一条 Redis 操作中检查。
    private static final DefaultRedisScript<Long> RESERVE = new DefaultRedisScript<>("""
        local cooldown = redis.call('TTL', KEYS[1])
        if cooldown > 0 then return -cooldown end
        if tonumber(redis.call('GET', KEYS[2]) or '0') >= 5 then
            return -math.max(1, redis.call('TTL', KEYS[2]))
        end
        if tonumber(redis.call('GET', KEYS[3]) or '0') >= 20 then
            return -math.max(1, redis.call('TTL', KEYS[3]))
        end
        redis.call('SET', KEYS[1], '1', 'EX', 60)
        if redis.call('INCR', KEYS[2]) == 1 then redis.call('EXPIRE', KEYS[2], 3600) end
        if redis.call('INCR', KEYS[3]) == 1 then redis.call('EXPIRE', KEYS[3], 3600) end
        return 1
        """, Long.class);

    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>("""
        redis.call('HSET', KEYS[1], 'hash', ARGV[1], 'owner', ARGV[2], 'version', ARGV[3], 'attempts', 0)
        redis.call('EXPIRE', KEYS[1], 600)
        return 1
        """, Long.class);

    // 成功后立即删除，最多允许五次错误尝试；绑定与重置、账号与版本分别隔离。
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
        local value = redis.call('HGET', KEYS[1], 'hash')
        if not value then return 0 end
        if redis.call('HGET', KEYS[1], 'owner') ~= ARGV[2]
            or redis.call('HGET', KEYS[1], 'version') ~= ARGV[3] then
            redis.call('DEL', KEYS[1])
            return 0
        end
        if value == ARGV[1] then
            redis.call('DEL', KEYS[1])
            return 1
        end
        if redis.call('HINCRBY', KEYS[1], 'attempts', 1) >= 5 then redis.call('DEL', KEYS[1]) end
        return 0
        """, Long.class);

    private static final DefaultRedisScript<Long> DISCARD = new DefaultRedisScript<>("""
        if redis.call('HGET', KEYS[1], 'hash') == ARGV[1] then return redis.call('DEL', KEYS[1]) end
        return 0
        """, Long.class);

    private final StringRedisTemplate redis;
    private final String prefix;
    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationCodes(StringRedisTemplate redis,
        @Value("${account.email.redis-prefix:mall:account-email}") String prefix,
        @Value("${account.email.code-secret:}") String configuredSecret) {
        this.redis = redis;
        this.prefix = prefix;
        if (configuredSecret.isBlank()) {
            this.secret = new byte[32];
            this.random.nextBytes(this.secret);
        } else {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
            if (this.secret.length < 32) throw new IllegalArgumentException("EMAIL_CODE_SECRET 至少需要 32 个字节");
        }
    }

    public void reserveDelivery(String email, String requesterIp) {
        String recipient = digest(email);
        Long result = this.execute(RESERVE, List.of(
            this.prefix + ":cooldown:" + recipient,
            this.prefix + ":email-hour:" + recipient,
            this.prefix + ":ip-hour:" + digest(requesterIp == null ? "unknown" : requesterIp)
        ));
        if (result == null) throw unavailable();
        if (result < 0) throw new AccountFlowException(HttpStatus.TOO_MANY_REQUESTS,
            "验证码发送过于频繁，请稍后再试", Math.toIntExact(-result));
    }

    public String issue(Purpose purpose, String email, Integer owner, long version) {
        String code = String.format(Locale.ROOT, "%06d", this.random.nextInt(1_000_000));
        Long result = this.execute(ISSUE, List.of(this.codeKey(purpose, email, owner)),
            this.codeHash(purpose, email, owner, version, code), owner.toString(), Long.toString(version));
        if (result == null || result != 1L) throw unavailable();
        return code;
    }

    public boolean consume(Purpose purpose, String email, Integer owner, long version, String code) {
        if (code == null || !code.matches("[0-9]{6}")) return false;
        Long result = this.execute(CONSUME, List.of(this.codeKey(purpose, email, owner)),
            this.codeHash(purpose, email, owner, version, code), owner.toString(), Long.toString(version));
        if (result == null) throw unavailable();
        return result == 1L;
    }

    public void discard(Purpose purpose, String email, Integer owner, long version, String code) {
        this.execute(DISCARD, List.of(this.codeKey(purpose, email, owner)),
            this.codeHash(purpose, email, owner, version, code));
    }

    private Long execute(DefaultRedisScript<Long> script, List<String> keys, String... arguments) {
        try {
            return this.redis.execute(script, keys, (Object[]) arguments);
        } catch (DataAccessException error) {
            throw unavailable();
        }
    }

    private String codeKey(Purpose purpose, String email, Integer owner) {
        return this.prefix + ":code:" + purpose + ":" + owner + ":" + digest(email);
    }

    private String codeHash(Purpose purpose, String email, Integer owner, long version, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(this.secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                (purpose + "|" + email + "|" + owner + "|" + version + "|" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("无法创建邮箱验证码", error);
        }
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }

    private static AccountFlowException unavailable() {
        return new AccountFlowException(HttpStatus.SERVICE_UNAVAILABLE, "验证码服务暂不可用，请稍后重试");
    }
}
