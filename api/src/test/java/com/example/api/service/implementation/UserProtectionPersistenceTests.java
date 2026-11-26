package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.example.api.entity.UserEntity;
import com.example.api.service.UserService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;

@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "seckill.expiry-enabled=false" })
@Transactional
class UserProtectionPersistenceTests {

    @Autowired
    private UserService users;

    @MockitoBean
    private Producer rocketMQProducer;

    @MockitoBean
    private PushConsumer orderPayTimeoutConsumer;

    @MockitoBean
    private OrderPayTimeoutScanTask orderPayTimeoutScanTask;

    @MockitoBean
    private PaymentReconciliationTask paymentReconciliationTask;

    @Test
    void directServiceCallsCannotDisableOrDemoteASuperAdmin() {
        UserEntity user = createAccount("super_admin");

        assertEquals(Integer.valueOf(0), this.users.updateStatus(user.getId(), 0));
        user.setName("不应写入");
        for (String role : new String[] { "admin", "user" }) {
            user.setRole(role);
            assertEquals(Integer.valueOf(0), this.users.update(user));
        }

        UserEntity saved = this.users.fetchById(user.getId()).orElseThrow();
        assertEquals("super_admin", saved.getRole());
        assertEquals(Integer.valueOf(1), saved.getStatus());
        assertEquals("保护测试", saved.getName());
    }

    @Test
    void superAdminProfileRemainsEditableWithoutChangingCredentialsOrDisablingTheAccount() {
        UserEntity user = createAccount("super_admin");
        String username = user.getUsername();
        String password = user.getPassword();
        user.setName("修改后的名字");
        user.setUsername("不应改名");
        user.setPassword("不应改密码");
        user.setStatus(0);

        assertEquals(Integer.valueOf(1), this.users.update(user));
        UserEntity saved = this.users.fetchById(user.getId()).orElseThrow();
        assertEquals("修改后的名字", saved.getName());
        assertEquals(username, saved.getUsername());
        assertEquals(password, saved.getPassword());
        assertEquals("super_admin", saved.getRole());
        assertEquals(Integer.valueOf(1), saved.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = { "user", "admin" })
    void ordinaryAccountsCanBeDisabledAndPromotionToSuperAdminEnablesThem(String role) {
        UserEntity user = createAccount(role);
        assertEquals(Integer.valueOf(1), this.users.updateStatus(user.getId(), 0));
        assertEquals(Integer.valueOf(0), this.users.fetchById(user.getId()).orElseThrow().getStatus());

        user.setRole("super_admin");
        assertEquals(Integer.valueOf(1), this.users.update(user));
        UserEntity saved = this.users.fetchById(user.getId()).orElseThrow();
        assertEquals("super_admin", saved.getRole());
        assertEquals(Integer.valueOf(1), saved.getStatus());
        assertEquals(Integer.valueOf(0), this.users.updateStatus(user.getId(), 0));
    }

    @Test
    void editingAnOrdinaryAccountsRoleDoesNotChangeItsDisabledStatus() {
        UserEntity user = createAccount("user");
        assertEquals(Integer.valueOf(1), this.users.updateStatus(user.getId(), 0));
        user.setRole("admin");

        assertEquals(Integer.valueOf(1), this.users.update(user));
        UserEntity saved = this.users.fetchById(user.getId()).orElseThrow();
        assertEquals("admin", saved.getRole());
        assertEquals(Integer.valueOf(0), saved.getStatus());
    }

    private UserEntity createAccount(String role) {
        UserEntity user = new UserEntity();
        user.setUsername("guard_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        user.setName("保护测试");
        user.setPassword("protection-test-password");
        user.setRole(role);
        assertEquals(Integer.valueOf(1), this.users.create(user));
        return user;
    }
}
