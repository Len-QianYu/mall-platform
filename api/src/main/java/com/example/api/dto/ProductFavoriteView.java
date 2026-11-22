package com.example.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductFavoriteView(
    Integer productId, String productName, String productImage, String category,
    BigDecimal price, Integer stock, Integer productStatus, LocalDateTime favoritedAt
) { }
