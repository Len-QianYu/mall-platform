package com.example.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SeckillActivityView {
    private Long id;
    private Integer productId;
    private String title;
    private String productName;
    private String productSubtitle;
    private String productImage;
    private Integer productStatus;
    private BigDecimal originalPrice;
    private BigDecimal seckillPrice;
    private Integer totalStock;
    private Integer stock;
    private Integer returnedStock;
    private Integer limitPerUser;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Long startsAtEpochMs;
    private Long endsAtEpochMs;
    private Long serverTime;
    private Integer status;

    public String getPhase() {
        if (this.status == 2 || this.serverTime >= this.endsAtEpochMs) return "ENDED";
        if (this.status != 1 || this.productStatus != 1) return "CLOSED";
        if (this.serverTime < this.startsAtEpochMs) return "UPCOMING";
        if (this.stock <= 0) return "SOLD_OUT";
        return "LIVE";
    }
}
