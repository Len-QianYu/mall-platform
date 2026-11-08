package com.example.api.service.implementation;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.dto.OrderWithItemsView;
import com.example.api.dto.TradeRequests;
import com.example.api.entity.CartEntity;
import com.example.api.entity.OrderEntity;
import com.example.api.entity.OrderItemEntity;
import com.example.api.entity.ProductEntity;
import com.example.api.enums.OrderStatus;
import com.example.api.exception.TradeFailedException;
import com.example.api.mapper.CartMapper;
import com.example.api.mapper.OrderItemMapper;
import com.example.api.mapper.OrderMapper;
import com.example.api.mapper.ProductMapper;
import com.example.api.mq.OrderPayTimeoutProducer;
import com.example.api.service.OrderService;
import com.example.api.utils.Pagination;
import com.example.api.utils.TradeNo;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final ProductMapper productMapper;

    private final CartMapper cartMapper;

    private final OrderPayTimeoutProducer payTimeoutProducer;

    public OrderServiceImpl(
        OrderMapper orderMapper,
        OrderItemMapper orderItemMapper,
        ProductMapper productMapper,
        CartMapper cartMapper,
        OrderPayTimeoutProducer payTimeoutProducer
    ) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.cartMapper = cartMapper;
        this.payTimeoutProducer = payTimeoutProducer;
    }

    @Override
    public Integer count(String keyword, OrderStatus status) {
        return this.orderMapper.selectCount(this.filter(keyword, status)).intValue();
    }

    @Override
    public List<OrderEntity> fetch(String keyword, OrderStatus status, Pagination pagination) {
        // 总数已由 count 查过，分页插件只负责拼 LIMIT（searchCount = false，不重复计数）
        return this.orderMapper.selectList(
            new Page<>(pagination.getCurrentPage(), pagination.getPageSize(), false),
            this.filter(keyword, status).orderByDesc(OrderEntity::getId)
        );
    }

    @Override
    public Integer countForUser(Integer userId, OrderStatus status) {
        return this.orderMapper.selectCount(this.filterForUser(userId, status)).intValue();
    }

    @Override
    public List<OrderWithItemsView> fetchForUser(Integer userId, OrderStatus status, Pagination pagination) {
        // 按下单时间倒序，正好走 idx_user_created 索引；主键兜底，同一秒下的单顺序也稳定
        List<OrderEntity> orders = this.orderMapper.selectList(
            new Page<>(pagination.getCurrentPage(), pagination.getPageSize(), false),
            this.filterForUser(userId, status)
                .orderByDesc(OrderEntity::getCreatedAt)
                .orderByDesc(OrderEntity::getId)
        );
        if (orders.isEmpty()) {
            return List.of();
        }

        // 这一页的明细一次查出再按订单分组，不用每个订单各查一次（N+1）
        Map<Long, List<OrderItemEntity>> itemsByOrderId = this.orderItemMapper.selectList(
            Wrappers.<OrderItemEntity>lambdaQuery()
                .in(OrderItemEntity::getOrderId, orders.stream().map(OrderEntity::getId).toList())
                .orderByAsc(OrderItemEntity::getId)
        ).stream().collect(Collectors.groupingBy(OrderItemEntity::getOrderId));

        return orders.stream()
            .map(order -> OrderWithItemsView.of(order, itemsByOrderId.getOrDefault(order.getId(), List.of())))
            .toList();
    }

    @Override
    public List<OrderEntity> fetchRecent(Integer limit) {
        return this.orderMapper.selectList(
            new Page<>(1, limit, false),
            Wrappers.<OrderEntity>lambdaQuery().orderByDesc(OrderEntity::getId)
        );
    }

    @Override
    public Optional<OrderEntity> fetchById(Long id) {
        return Optional.ofNullable(this.orderMapper.selectById(id));
    }

    @Override
    public Optional<OrderEntity> fetchByIdForUser(Long id, Integer userId) {
        return Optional.ofNullable(this.orderMapper.selectOne(
            Wrappers.<OrderEntity>lambdaQuery()
                .eq(OrderEntity::getId, id)
                .eq(OrderEntity::getUserId, userId)
        ));
    }

    @Override
    public List<OrderItemEntity> fetchItems(Long orderId) {
        return this.orderItemMapper.selectList(
            Wrappers.<OrderItemEntity>lambdaQuery()
                .eq(OrderItemEntity::getOrderId, orderId)
                .orderByAsc(OrderItemEntity::getId)
        );
    }

    @Override
    public BigDecimal sumSalesAmount() {
        return this.orderMapper.sumSalesAmount();
    }

    @Override
    @Transactional
    public OrderEntity create(Integer userId, TradeRequests.OrderCreate request) {
        // 按商品 ID 合并、排序：同一商品重复提交时数量相加；
        // 扣库存会锁住商品行，所有事务都按商品 ID 从小到大加锁，两笔订单商品相同、顺序相反时也不会互相等锁（死锁）
        Map<Integer, Integer> quantities = new TreeMap<>();
        for (TradeRequests.OrderCreateItem item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        List<OrderItemEntity> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            Integer productId = entry.getKey();
            Integer quantity = entry.getValue();

            if (this.productMapper.deductStock(productId, quantity) == 0) {
                throw this.deductFailure(productId);
            }
            // 扣减成功后本事务持有这行商品的锁，这时读到的售价在提交前不会被改，订单金额和明细单价一致
            ProductEntity product = this.productMapper.selectById(productId);

            OrderItemEntity item = new OrderItemEntity();
            item.setProductId(productId);
            item.setProductName(product.getName());
            item.setProductImage(product.getMainImage());
            item.setUnitPrice(product.getPrice());
            item.setQuantity(quantity);
            items.add(item);

            totalAmount = totalAmount.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }

        OrderEntity order = new OrderEntity();
        order.setOrderNo(TradeNo.order());
        order.setUserId(userId);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setReceiverName(request.receiverName());
        order.setReceiverPhone(request.receiverPhone());
        order.setReceiverAddress(request.receiverAddress());
        order.setRemark(request.remark());
        this.orderMapper.insert(order);

        for (OrderItemEntity item : items) {
            item.setOrderId(order.getId());
            this.orderItemMapper.insert(item);
        }

        if (Boolean.TRUE.equals(request.fromCart())) {
            this.cartMapper.delete(
                Wrappers.<CartEntity>lambdaQuery()
                    .eq(CartEntity::getUserId, userId)
                    .in(CartEntity::getProductId, quantities.keySet())
            );
        }

        // 到期未支付自动取消、回补库存；消息在事务提交后才发出
        this.payTimeoutProducer.sendAfterCommit(order);

        // 重新查一次：created_at 等列由数据库填写
        return this.orderMapper.selectById(order.getId());
    }

    @Override
    public List<Long> fetchExpiredIds(Duration expireAfter, Integer limit) {
        // 用数据库的 NOW() 和 created_at（也由数据库填写）比较，不受应用服务器和数据库时钟不一致影响；走 idx_status
        return this.orderMapper.selectList(
            new Page<>(1, limit, false),
            Wrappers.<OrderEntity>lambdaQuery()
                .select(OrderEntity::getId)
                .eq(OrderEntity::getStatus, OrderStatus.PENDING_PAYMENT)
                .apply("created_at < NOW() - INTERVAL {0} SECOND", expireAfter.toSeconds())
                .orderByAsc(OrderEntity::getId)
        ).stream().map(OrderEntity::getId).toList();
    }

    // 状态流转都用条件更新：WHERE 里带上当前应有的状态，判断和修改在同一条 SQL 里完成，
    // 重复提交、两个管理员同时操作、或取消时买家恰好付了款，都只有一方能改成功，不会出现状态被覆盖

    @Override
    public boolean ship(Long id) {
        return this.orderMapper.update(
            Wrappers.<OrderEntity>lambdaUpdate()
                .set(OrderEntity::getStatus, OrderStatus.SHIPPED)
                .set(OrderEntity::getShippedAt, LocalDateTime.now())
                .eq(OrderEntity::getId, id)
                .eq(OrderEntity::getStatus, OrderStatus.PAID)
        ) > 0;
    }

    @Override
    @Transactional
    public boolean cancel(Long id) {
        boolean cancelled = this.orderMapper.update(
            Wrappers.<OrderEntity>lambdaUpdate()
                .set(OrderEntity::getStatus, OrderStatus.CANCELLED)
                .set(OrderEntity::getCancelledAt, LocalDateTime.now())
                .eq(OrderEntity::getId, id)
                .eq(OrderEntity::getStatus, OrderStatus.PENDING_PAYMENT)
        ) > 0;

        // 下单时扣了库存，取消要还回去；只有把状态改成功的一方回补，重复取消不会多补。
        // 按商品 ID 从小到大更新，和下单扣库存的加锁顺序一致
        if (cancelled) {
            List<OrderItemEntity> items = this.orderItemMapper.selectList(
                Wrappers.<OrderItemEntity>lambdaQuery()
                    .eq(OrderItemEntity::getOrderId, id)
                    .orderByAsc(OrderItemEntity::getProductId)
            );
            for (OrderItemEntity item : items) {
                this.productMapper.restoreStock(item.getProductId(), item.getQuantity());
            }
        }
        return cancelled;
    }

    @Override
    public boolean confirm(Long id) {
        return this.orderMapper.update(
            Wrappers.<OrderEntity>lambdaUpdate()
                .set(OrderEntity::getStatus, OrderStatus.COMPLETED)
                .set(OrderEntity::getCompletedAt, LocalDateTime.now())
                .eq(OrderEntity::getId, id)
                .eq(OrderEntity::getStatus, OrderStatus.SHIPPED)
        ) > 0;
    }

    // 扣库存的条件（在售且库存够）不满足时，再查一次商品，把原因提示到具体商品
    private TradeFailedException deductFailure(Integer productId) {
        ProductEntity product = this.productMapper.selectById(productId);
        if (product == null) {
            return new TradeFailedException("商品不存在");
        }
        return new TradeFailedException(
            "「" + product.getName() + "」" + (product.getStatus() == 1 ? "库存不足" : "已下架")
        );
    }

    // 列表筛选条件：count 和 fetch 共用，保证总数和当前页按同一条件查询
    private LambdaQueryWrapper<OrderEntity> filter(String keyword, OrderStatus status) {
        return Wrappers.<OrderEntity>lambdaQuery()
            .and(StringUtils.hasText(keyword), wrapper -> wrapper
                .like(OrderEntity::getOrderNo, keyword)
                .or().like(OrderEntity::getReceiverName, keyword)
                .or().like(OrderEntity::getReceiverPhone, keyword)
            )
            .eq(status != null, OrderEntity::getStatus, status);
    }

    // 某用户的订单：countForUser 和 fetchForUser 共用
    private LambdaQueryWrapper<OrderEntity> filterForUser(Integer userId, OrderStatus status) {
        return Wrappers.<OrderEntity>lambdaQuery()
            .eq(OrderEntity::getUserId, userId)
            .eq(status != null, OrderEntity::getStatus, status);
    }

}
