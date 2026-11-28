import java.io.BufferedWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.apache.rocketmq.client.apis.producer.Producer;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.api.ApiApplication;
import com.example.api.configuration.RocketMQConfiguration;
import com.example.api.entity.UserEntity;
import com.example.api.utils.JwtUtils;

/** Local HTTP benchmark: production JWT, order, Redis and MySQL code, isolated MQ transport. */
public class SeckillBenchmarkServer {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan("com.example.api.mapper")
    @ComponentScan(basePackages = "com.example.api", excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = { ApiApplication.class, RocketMQConfiguration.class }))
    static class BenchmarkConfiguration {
        // No consumer connects to development queues. Both variants have the same MQ stub;
        // message construction and the production after-commit callback still execute.
        @Bean
        Producer benchmarkProducer() {
            return (Producer) Proxy.newProxyInstance(Producer.class.getClassLoader(), new Class<?>[] { Producer.class },
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "sendAsync" -> CompletableFuture.completedFuture(null);
                    case "close" -> null;
                    case "toString" -> "IsolatedBenchmarkProducer";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        }
    }

    public static void main(String[] args) throws Exception {
        var application = new SpringApplication(BenchmarkConfiguration.class);
        var context = application.run(args);
        var environment = context.getEnvironment();
        Path csv = Path.of(environment.getRequiredProperty("benchmark.csv"));
        Files.createDirectories(csv.toAbsolutePath().getParent());
        JwtUtils tokens = context.getBean(JwtUtils.class);
        int accountCount = environment.getProperty("benchmark.accounts", Integer.class, 100000);
        if (accountCount < 1) throw new IllegalArgumentException("benchmark.accounts must be positive");
        try (BufferedWriter output = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            output.write("userId,username,token\n");
            context.getBean(JdbcTemplate.class).query(
                "SELECT id, username, role, auth_version FROM user WHERE role = 'user' ORDER BY id LIMIT " + accountCount, rows -> {
                    UserEntity user = new UserEntity();
                    user.setId(rows.getInt("id"));
                    user.setUsername(rows.getString("username"));
                    user.setRole(rows.getString("role"));
                    user.setAuthVersion(rows.getLong("auth_version"));
                    try {
                        output.write(user.getId() + "," + user.getUsername() + "," + tokens.generateToken(user) + "\n");
                    } catch (java.io.IOException error) {
                        throw new java.io.UncheckedIOException(error);
                    }
                });
        }
        Files.writeString(csv.resolveSibling("ready.json"), "{\"pid\":" + ProcessHandle.current().pid()
            + ",\"mqTransport\":\"stubbed\",\"jwt\":\"production-RS256\",\"transactionSynchronization\":"
            + context.getBean(org.springframework.transaction.support.AbstractPlatformTransactionManager.class)
                .getTransactionSynchronization() + "}", StandardCharsets.UTF_8);
        System.out.println("BENCHMARK_READY: signed account fixtures generated.");
    }
}
