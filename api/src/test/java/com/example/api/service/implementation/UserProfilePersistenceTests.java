package com.example.api.service.implementation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;

import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.example.api.dto.ProfileRequests;
import com.example.api.entity.UserEntity;
import com.example.api.service.UserService;
import com.example.api.task.OrderPayTimeoutScanTask;
import com.example.api.task.PaymentReconciliationTask;

@SpringBootTest(properties = { "rustfs.endpoint=http://127.0.0.1:1", "seckill.expiry-enabled=false" })
@Transactional
class UserProfilePersistenceTests {

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
    void savesAvatarWithoutChangingSavedProfileFieldsOrCredentials() {
        UserEntity user = new UserEntity();
        user.setUsername("ava_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        user.setPassword("avatar-test-password");
        assertEquals(Integer.valueOf(1), this.users.create(user));

        ProfileRequests.Update profile = new ProfileRequests.Update(
            "头像测试", "M", "avatar@example.com", "13800138000", "浙江省", "杭州市",
            "浙江省杭州市西湖区文三路1号", "/avatars/previous.png"
        );
        assertEquals(Integer.valueOf(1), this.users.updateProfile(user.getId(), profile));
        UserEntity before = this.users.fetchById(user.getId()).orElseThrow();

        assertEquals(Integer.valueOf(1), this.users.updateAvatar(user.getId(), "/avatars/new.png"));

        UserEntity saved = this.users.fetchById(user.getId()).orElseThrow();
        assertEquals("/avatars/new.png", saved.getAvatarUrl());
        assertEquals(before.getName(), saved.getName());
        assertEquals(before.getGender(), saved.getGender());
        assertEquals(before.getEmail(), saved.getEmail());
        assertEquals(before.getPhone(), saved.getPhone());
        assertEquals(before.getProvince(), saved.getProvince());
        assertEquals(before.getCity(), saved.getCity());
        assertEquals(before.getReceiverAddress(), saved.getReceiverAddress());
        assertEquals(before.getUsername(), saved.getUsername());
        assertEquals(before.getRole(), saved.getRole());
        assertEquals(before.getStatus(), saved.getStatus());
        assertEquals(before.getPassword(), saved.getPassword());
    }

    @Test
    void savesAndClearsLocationAndAddressWhileKeepingTheAccountIdentityAndCredentials() {
        UserEntity user = new UserEntity();
        user.setUsername("loc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        user.setPassword("profile-test-password");
        assertEquals(Integer.valueOf(1), this.users.create(user));

        ProfileRequests.Update located = new ProfileRequests.Update(
            "地点测试", "F", "profile@example.com", "13800138000", "浙江省", "杭州市",
            "浙江省杭州市西湖区文三路1号", null
        );
        assertEquals(Integer.valueOf(1), this.users.updateProfile(user.getId(), located));

        UserEntity saved = this.users.fetchById(user.getId()).orElseThrow();
        assertEquals("浙江省", saved.getProvince());
        assertEquals("杭州市", saved.getCity());
        assertEquals("浙江省杭州市西湖区文三路1号", saved.getReceiverAddress());
        assertEquals(user.getUsername(), saved.getUsername());
        assertEquals(user.getRole(), saved.getRole());
        assertEquals(user.getPassword(), saved.getPassword());

        ProfileRequests.Update cleared = new ProfileRequests.Update(
            located.name(), located.gender(), located.email(), located.phone(), null, null, null, located.avatarUrl()
        );
        assertEquals(Integer.valueOf(1), this.users.updateProfile(user.getId(), cleared));

        UserEntity reloaded = this.users.fetchById(user.getId()).orElseThrow();
        assertNull(reloaded.getProvince());
        assertNull(reloaded.getCity());
        assertNull(reloaded.getReceiverAddress());
        assertEquals(located.name(), reloaded.getName());
        assertEquals(user.getUsername(), reloaded.getUsername());
        assertEquals(user.getRole(), reloaded.getRole());
        assertEquals(user.getPassword(), reloaded.getPassword());
    }
}
