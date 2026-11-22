package com.example.api.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.api.dto.SeckillActivityView;
import com.example.api.dto.SeckillRequests;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.entity.ProductEntity;
import com.example.api.entity.SeckillActivityEntity;
import com.example.api.exception.TradeFailedException;
import com.example.api.mapper.ProductMapper;
import com.example.api.mapper.SeckillActivityMapper;
import com.example.api.mapper.SeckillPurchaseMapper;
import com.example.api.utils.Pagination;

@Service
public class SeckillService {
    private final SeckillActivityMapper activities;
    private final SeckillPurchaseMapper purchases;
    private final ProductMapper products;
    private final ProductService productService;
    private final SeckillStockGate gate;

    public SeckillService(SeckillActivityMapper activities, SeckillPurchaseMapper purchases,
        ProductMapper products, ProductService productService, SeckillStockGate gate) {
        this.activities = activities;
        this.purchases = purchases;
        this.products = products;
        this.productService = productService;
        this.gate = gate;
    }

    public int count(boolean admin) { return this.activities.countViews(admin, null); }

    public int count(String phase) { return this.activities.countViews(false, phase); }

    public List<SeckillActivityView> fetch(boolean admin, Pagination pagination) {
        return this.activities.selectViews(admin, null, (pagination.getCurrentPage() - 1) * pagination.getPageSize(),
            pagination.getPageSize());
    }

    public List<SeckillActivityView> fetch(String phase, Pagination pagination) {
        return this.activities.selectViews(false, phase, pagination.getOffset(), pagination.getLimit());
    }

    public Optional<SeckillActivityView> fetchById(Long id) {
        return Optional.ofNullable(this.activities.selectView(id));
    }

    @Transactional
    public SeckillActivityEntity create(SeckillRequests.Create request) {
        if (!request.startsAt().isBefore(request.endsAt())
            || !request.endsAt().isAfter(this.activities.databaseNow())) {
            throw new TradeFailedException("结束时间必须晚于开始时间和当前时间");
        }
        if (request.limitPerUser() > request.totalStock()) {
            throw new TradeFailedException("每人限购数量不能超过活动总配额");
        }
        // 先从普通库存划拨配额，这行锁也保护创建时的原价与上下架状态。
        if (!this.productService.deductStock(request.productId(), request.totalStock())) {
            throw new TradeFailedException("商品已下架或普通可用库存不足，无法划拨秒杀配额");
        }
        ProductEntity product = this.products.selectById(request.productId());
        if (request.seckillPrice().compareTo(product.getPrice()) >= 0) {
            throw new TradeFailedException("秒杀价必须低于商品当前售价");
        }
        SeckillActivityEntity activity = new SeckillActivityEntity();
        activity.setProductId(request.productId());
        activity.setTitle(request.title().trim());
        activity.setOriginalPrice(product.getPrice());
        activity.setSeckillPrice(request.seckillPrice());
        activity.setTotalStock(request.totalStock());
        activity.setStock(request.totalStock());
        activity.setReturnedStock(0);
        activity.setLimitPerUser(request.limitPerUser());
        activity.setStartsAt(request.startsAt());
        activity.setEndsAt(request.endsAt());
        activity.setStatus(1);
        this.activities.insert(activity);
        return activity;
    }

    public record ReservedProduct(ProductEntity product, BigDecimal price, Long activityId) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public ReservedProduct reserve(Long activityId, Integer userId, String requestId,
        Integer productId, Integer quantity) {
        SeckillActivityView activity = this.activities.selectView(activityId);
        if (activity == null || !activity.getProductId().equals(productId)) {
            throw new TradeFailedException("秒杀活动或商品不正确");
        }
        if (quantity <= 0 || quantity > activity.getLimitPerUser()) {
            throw new TradeFailedException("超过本场活动的限购数量");
        }
        String phase = activity.getPhase();
        if (!"LIVE".equals(phase)) {
            throw new TradeFailedException(switch (phase) {
                case "UPCOMING" -> "秒杀活动尚未开始";
                case "SOLD_OUT" -> "秒杀商品已抢完";
                case "ENDED" -> "秒杀活动已结束";
                default -> "秒杀活动已关闭或商品已下架";
            });
        }
        Integer purchased = this.purchases.quantity(activityId, userId);
        this.gate.reserve(activityId, userId, requestId, quantity, activity.getStock(),
            purchased == null ? 0 : purchased, activity.getLimitPerUser());
        // Redis 不能跟随数据库回滚，显式补偿；进程崩溃时由短 TTL 使预扣失效。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) gate.release(activityId, userId, requestId);
            }
        });
        if (this.activities.deductStock(activityId, quantity) != 1) {
            throw new TradeFailedException("秒杀配额不足或活动已结束，请刷新后重试");
        }
        ProductEntity product = this.products.selectByIdForUpdate(productId);
        if (product == null || product.getStatus() != 1) {
            throw new TradeFailedException("秒杀商品已下架");
        }
        this.purchases.ensure(activityId, userId);
        if (this.purchases.increase(activityId, userId, quantity, activity.getLimitPerUser()) != 1) {
            throw new TradeFailedException("已达到本场活动的限购数量");
        }
        return new ReservedProduct(product, activity.getSeckillPrice(), activityId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void restore(OrderEntity order, OrderItemEntity item) {
        Long id = item.getSeckillActivityId();
        SeckillActivityEntity activity = this.activities.selectForUpdate(id);
        if (activity == null || this.purchases.decrease(id, order.getUserId(), item.getQuantity()) != 1) {
            throw new TradeFailedException("秒杀配额记录不一致，暂时无法回补");
        }
        if (activity.getStatus() == 1 && activity.getEndsAt().isAfter(this.activities.databaseNow())) {
            if (this.activities.restoreStock(id, item.getQuantity()) != 1) {
                throw new TradeFailedException("秒杀配额回补失败");
            }
        } else {
            if (activity.getStatus() == 1) this.closeLocked(activity, 2);
            if (this.activities.returnStock(id, item.getQuantity()) != 1
                || !this.productService.adjustStock(item.getProductId(), item.getQuantity())) {
                throw new TradeFailedException("秒杀库存归还失败");
            }
        }
        // 回补后重建预扣计数，可同时覆盖缓存过期、重启和旧预扣令牌丢失的情况。
        afterCommit(() -> this.gate.invalidate(id));
    }

    @Transactional
    public boolean close(Long id) {
        SeckillActivityEntity activity = this.activities.selectForUpdate(id);
        if (activity == null || activity.getStatus() != 1) return false;
        this.closeLocked(activity, activity.getEndsAt().isAfter(this.activities.databaseNow()) ? 0 : 2);
        return true;
    }

    @Transactional
    public void closeExpired(Long id) {
        SeckillActivityEntity activity = this.activities.selectForUpdate(id);
        if (activity != null && activity.getStatus() == 1
            && !activity.getEndsAt().isAfter(this.activities.databaseNow())) this.closeLocked(activity, 2);
    }

    public List<Long> fetchExpiredIds(int limit) { return this.activities.selectExpiredIds(limit); }

    private void closeLocked(SeckillActivityEntity activity, int status) {
        if (this.activities.close(activity.getId(), status) != 1) return;
        if (activity.getStock() > 0 && !this.productService.adjustStock(activity.getProductId(), activity.getStock())) {
            throw new TradeFailedException("归还秒杀剩余库存失败");
        }
        afterCommit(() -> this.gate.invalidate(activity.getId()));
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
