package com.example.api.service;

import java.util.List;

import com.example.api.dto.ProductFavoriteView;
import com.example.api.utils.Pagination;

public interface ProductFavoriteService {
    List<Integer> fetchIds(Integer userId);
    int count(Integer userId);
    List<ProductFavoriteView> fetch(Integer userId, Pagination pagination);
    void setFavorite(Integer userId, Integer productId, boolean favorite);
}
