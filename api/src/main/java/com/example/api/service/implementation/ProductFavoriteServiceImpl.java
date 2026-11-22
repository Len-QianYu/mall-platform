package com.example.api.service.implementation;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.api.dto.ProductFavoriteView;
import com.example.api.entity.ProductFavoriteEntity;
import com.example.api.exception.TradeFailedException;
import com.example.api.mapper.ProductFavoriteMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.service.ProductFavoriteService;
import com.example.api.utils.Pagination;

@Service
public class ProductFavoriteServiceImpl implements ProductFavoriteService {
    private final ProductFavoriteMapper favorites;
    private final ProductMapper products;

    public ProductFavoriteServiceImpl(ProductFavoriteMapper favorites, ProductMapper products) {
        this.favorites = favorites;
        this.products = products;
    }

    @Override
    public List<Integer> fetchIds(Integer userId) { return this.favorites.selectProductIds(userId); }

    @Override
    public int count(Integer userId) { return this.favorites.countItems(userId); }

    @Override
    public List<ProductFavoriteView> fetch(Integer userId, Pagination pagination) {
        return this.favorites.selectItems(userId, pagination.getLimit(), pagination.getOffset());
    }

    @Override
    @Transactional
    public void setFavorite(Integer userId, Integer productId, boolean favorite) {
        if (favorite) {
            // 和商品修改共用行锁，保证订阅时间在变动之前还是之后有确定的顺序；下架商品仍可收藏。
            if (this.products.selectByIdForUpdate(productId) == null) {
                throw new TradeFailedException("商品不存在");
            }
            this.favorites.add(userId, productId);
        } else {
            this.favorites.delete(Wrappers.<ProductFavoriteEntity>lambdaQuery()
                .eq(ProductFavoriteEntity::getUserId, userId)
                .eq(ProductFavoriteEntity::getProductId, productId));
        }
    }
}
