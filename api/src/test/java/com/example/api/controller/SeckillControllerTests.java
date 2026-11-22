package com.example.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.api.controller.admin.AdminProductController;
import com.example.api.controller.admin.AdminSeckillController;
import com.example.api.dto.SeckillActivityView;
import com.example.api.entity.SeckillActivityEntity;
import com.example.api.exception.TradeExceptionHandler;
import com.example.api.security.SecurityConfiguration;
import com.example.api.service.ProductService;
import com.example.api.service.SeckillService;

@WebMvcTest({ SeckillController.class, AdminSeckillController.class, AdminProductController.class })
@Import({ SecurityConfiguration.class, TradeExceptionHandler.class })
class SeckillControllerTests {
    @Autowired private MockMvc mvc;
    @MockitoBean private SeckillService seckill;
    @MockitoBean private ProductService products;
    @MockitoBean private JwtDecoder decoder;

    @Test
    void visitorsCanBrowseCampaignsAndTheirServerTimeWithoutAuthentication() throws Exception {
        when(this.seckill.fetch(isNull(String.class), any())).thenReturn(List.of());
        this.mvc.perform(get("/seckill")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true));
        SeckillActivityView activity = new SeckillActivityView();
        activity.setId(3L);
        activity.setStatus(1);
        activity.setProductStatus(1);
        activity.setStock(3);
        activity.setServerTime(1000L);
        activity.setStartsAtEpochMs(0L);
        activity.setEndsAtEpochMs(2000L);
        when(this.seckill.fetchById(3L)).thenReturn(Optional.of(activity));
        this.mvc.perform(get("/seckill/3")).andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.activity.phase").value("LIVE"))
            .andExpect(jsonPath("$.payload.activity.serverTime").value(1000));
    }

    @Test
    void ordinaryUsersCannotCreateOrCloseCampaigns() throws Exception {
        this.authenticate("user");
        this.mvc.perform(post("/admin/seckill/create").header(HttpHeaders.AUTHORIZATION, "Bearer seckill-token")
            .contentType(MediaType.APPLICATION_JSON).content(this.body())).andExpect(status().isForbidden());
        this.mvc.perform(post("/admin/seckill/close").header(HttpHeaders.AUTHORIZATION, "Bearer seckill-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"id\":3}")).andExpect(status().isForbidden());
        verifyNoInteractions(this.seckill);
    }

    @Test
    void administratorsCanConfigureCampaignsAndInvalidQuotaIsRejected() throws Exception {
        this.authenticate("admin");
        SeckillActivityEntity created = new SeckillActivityEntity();
        created.setId(3L);
        when(this.seckill.create(any())).thenReturn(created);
        this.mvc.perform(post("/admin/seckill/create").header(HttpHeaders.AUTHORIZATION, "Bearer seckill-token")
            .contentType(MediaType.APPLICATION_JSON).content(this.body())).andExpect(status().isOk())
            .andExpect(jsonPath("$.payload.activity.id").value(3));
        this.mvc.perform(post("/admin/seckill/create").header(HttpHeaders.AUTHORIZATION, "Bearer seckill-token")
            .contentType(MediaType.APPLICATION_JSON).content(this.body().replace("\"totalStock\":5", "\"totalStock\":0")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(false));
    }

    @Test
    void productInformationCanBeSavedWithoutSendingAnInventoryValue() throws Exception {
        this.authenticate("admin");
        when(this.products.update(any())).thenReturn(1);
        this.mvc.perform(post("/admin/product/update").header(HttpHeaders.AUTHORIZATION, "Bearer seckill-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"id\":11,\"name\":\"edited\",\"category\":\"测试\",\"price\":100,\"status\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(true));
        verify(this.products).update(argThat(product -> product.getStock() == null && product.getId() == 11));
    }

    @Test
    void stockAdjustmentsRejectZeroAndRequireAnAdministrator() throws Exception {
        this.authenticate("admin");
        this.mvc.perform(post("/admin/product/stock").header(HttpHeaders.AUTHORIZATION, "Bearer seckill-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"id\":11,\"delta\":0}"))
            .andExpect(status().isBadRequest());
        this.authenticate("user");
        this.mvc.perform(post("/admin/product/stock").header(HttpHeaders.AUTHORIZATION, "Bearer seckill-token")
            .contentType(MediaType.APPLICATION_JSON).content("{\"id\":11,\"delta\":5}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(this.products);
    }

    private void authenticate(String role) {
        when(this.decoder.decode("seckill-token")).thenReturn(Jwt.withTokenValue("seckill-token")
            .header("alg", "RS256").subject("seckill-test").claim("role", role).claim("userId", 7L).build());
    }

    private String body() {
        return """
            {"productId":11,"title":"限时秒杀","seckillPrice":30,"totalStock":5,"limitPerUser":1,
             "startsAt":"2099-01-01T12:00:00","endsAt":"2099-01-01T13:00:00"}
            """;
    }
}
