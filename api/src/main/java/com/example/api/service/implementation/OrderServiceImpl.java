package com.example.api.service.implementation;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.api.dto.DashboardTrendPoint;
import com.example.api.dto.OrderStatusCount;
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
import com.example.api.service.OrderNotificationService;
import com.example.api.service.PaymentService;
import com.example.api.service.ProductService;
import com.example.api.service.SeckillService;
import com.example.api.utils.Pagination;
import com.example.api.utils.TradeNo;

import tools.jackson.databind.json.JsonMapper;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private static final JsonMapper REQUEST_MAPPER = JsonMapper.builder().build();

    private final OrderMapper orderMapper;

    private final OrderItemMapper orderItemMapper;

    private final ProductMapper productMapper;

    private final ProductService productService;

    private final SeckillService seckillService;

    private final CartMapper cartMapper;

    private final OrderPayTimeoutProducer payTimeoutProducer;

    private final PaymentService paymentService;

    private final OrderNotificationService notifications;

    private final RedissonClient redisson;

    private final TransactionTemplate createTransaction;

    private final Duration createLockWait;

    public OrderServiceImpl(
        OrderMapper orderMapper,
        OrderItemMapper orderItemMapper,
        ProductMapper productMapper,
        ProductService productService,
        SeckillService seckillService,
        CartMapper cartMapper,
        OrderPayTimeoutProducer payTimeoutProducer,
        PaymentService paymentService,
        OrderNotificationService notifications,
        RedissonClient redisson,
        PlatformTransactionManager transactionManager,
        @Value("${order.create-lock-wait:3s}") Duration createLockWait
    ) {
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
        this.productService = productService;
        this.seckillService = seckillService;
        this.cartMapper = cartMapper;
        this.payTimeoutProducer = payTimeoutProducer;
        this.paymentService = paymentService;
        this.notifications = notifications;
        this.redisson = redisson;
        this.createTransaction = new TransactionTemplate(transactionManager);
        // 获取锁后才开启独立事务：锁内查询使用新快照，提交/回滚结束后才能释放锁。
        this.createTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.createLockWait = createLockWait;
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
    public List<DashboardTrendPoint> fetchTrend(LocalDate startDate, LocalDate endDate) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atStartOfDay();
        Map<LocalDate, Long> orders = this.orderMapper.fetchDailyOrders(start, end).stream()
            .collect(Collectors.toMap(DashboardTrendPoint::date, DashboardTrendPoint::orderCount));
        Map<LocalDate, BigDecimal> sales = this.orderMapper.fetchDailySales(start, end).stream()
            .collect(Collectors.toMap(DashboardTrendPoint::date, DashboardTrendPoint::salesAmount));

        return startDate.datesUntil(endDate)
            .map(date -> new DashboardTrendPoint(
                date, orders.getOrDefault(date, 0L), sales.getOrDefault(date, BigDecimal.ZERO)
            ))
            .toList();
    }

    @Override
    public List<OrderStatusCount> fetchStatusCounts() {
        Map<OrderStatus, Long> counts = this.orderMapper.fetchStatusCounts().stream()
            .collect(Collectors.toMap(OrderStatusCount::status, OrderStatusCount::count));
        return Arrays.stream(OrderStatus.values())
            .map(status -> new OrderStatusCount(status, counts.getOrDefault(status, 0L)))
            .toList();
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public OrderEntity create(Integer userId, TradeRequests.OrderCreate request) {
        // 按商品 ID 合并、排序：同一商品重复提交时数量相加；
        // 扣库存会锁住商品行，所有事务都按商品 ID 从小到大加锁，两笔订单商品相同、顺序相反时也不会互相等锁（死锁）
        Map<Integer, Integer> quantities = new TreeMap<>();
        for (TradeRequests.OrderCreateItem item : request.items()) {
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }

        if (request.seckillActivityId() != null
            && (quantities.size() != 1 || Boolean.TRUE.equals(request.fromCart()))) {
            throw new TradeFailedException("秒杀商品请单独结算，不能与购物车商品混合下单");
        }

        String requestHash = this.requestHash(request, quantities);
        OrderEntity existing = this.orderMapper.selectByRequest(userId, request.requestId());
        if (existing != null) return this.reuseOrder(existing, requestHash);

        RLock lock = this.acquireCreateLock(userId, request.requestId());
        try {
            return this.createTransaction.execute(transaction -> {
                // 等锁期间另一个请求可能已完成下单，必须在锁内再查一次。
                OrderEntity committed = this.orderMapper.selectByRequest(userId, request.requestId());
                if (committed != null) return this.reuseOrder(committed, requestHash);
                return this.createOnce(userId, request, quantities, requestHash);
            });
        } catch (DuplicateKeyException conflict) {
            // 锁失效等极端情况下，唯一索引兜底；此时下单事务已经回滚，库存不会多扣。
            OrderEntity committed = this.orderMapper.selectByRequest(userId, request.requestId());
            if (committed == null) throw conflict;
            return this.reuseOrder(committed, requestHash);
        } finally {
            this.releaseCreateLock(lock);
        }
    }

    private RLock acquireCreateLock(Integer userId, String requestId) {
        try {
            RLock lock = this.redisson.getLock("mall:order:create:" + userId + ":" + requestId);
            // 不指定固定租期，使用 Redisson watchdog 续期，避免事务执行期间锁提前过期。
            if (!lock.tryLock(this.createLockWait.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new TradeFailedException("该订单正在提交，请稍后重试");
            }
            return lock;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new TradeFailedException("下单请求已中断，请稍后重试");
        } catch (RedisException unavailable) {
            log.warn("获取下单锁失败: {}", unavailable.getClass().getSimpleName());
            throw new TradeFailedException("下单服务暂时不可用，请稍后重试");
        }
    }

    private void releaseCreateLock(RLock lock) {
        try {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        } catch (RedisException | IllegalMonitorStateException unavailable) {
            // 事务已经结束，解锁失败不能把已成功的订单报成失败；锁会自动过期。
            log.warn("释放下单锁失败，等待自动过期: {}", unavailable.getClass().getSimpleName());
        }
    }

    private OrderEntity reuseOrder(OrderEntity order, String requestHash) {
        if (!Objects.equals(order.getRequestHash(), requestHash)) {
            throw new TradeFailedException("该下单请求号已用于其他订单内容，请重新发起结算");
        }
        return order;
    }

    private String requestHash(TradeRequests.OrderCreate request, Map<Integer, Integer> quantities) {
        OrderContent content = new OrderContent(
            quantities.entrySet().stream().map(entry ->
                new TradeRequests.OrderCreateItem(entry.getKey(), entry.getValue())).toList(),
            Boolean.TRUE.equals(request.fromCart()), request.receiverName(), request.receiverPhone(),
            request.receiverAddress(), request.remark() == null ? "" : request.remark());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // 普通订单保持原哈希格式，已存在的请求号在升级后仍能重试。
            if (request.seckillActivityId() != null) {
                digest.update(("seckill:" + request.seckillActivityId() + ":").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest(REQUEST_MAPPER.writeValueAsBytes(content)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    private record OrderContent(List<TradeRequests.OrderCreateItem> items, boolean fromCart,
        String receiverName, String receiverPhone, String receiverAddress, String remark) {}

    private OrderEntity createOnce(Integer userId, TradeRequests.OrderCreate request,
        Map<Integer, Integer> quantities, String requestHash) {

        List<OrderItemEntity> items = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (Map.Entry<Integer, Integer> entry : quantities.entrySet()) {
            Integer productId = entry.getKey();
            Integer quantity = entry.getValue();

            ProductEntity product;
            BigDecimal unitPrice;
            if (request.seckillActivityId() == null) {
                if (!this.productService.deductStock(productId, quantity)) throw this.deductFailure(productId);
                // 本事务持有商品行锁，普通订单价格与明细快照一致。
                product = this.productMapper.selectById(productId);
                unitPrice = product.getPrice();
            } else {
                SeckillService.ReservedProduct reserved = this.seckillService.reserve(request.seckillActivityId(),
                    userId, request.requestId(), productId, quantity);
                product = reserved.product();
                unitPrice = reserved.price();
            }

            OrderItemEntity item = new OrderItemEntity();
            item.setProductId(productId);
            item.setSeckillActivityId(request.seckillActivityId());
            item.setProductName(product.getName());
            item.setProductImage(product.getMainImage());
            item.setUnitPrice(unitPrice);
            item.setQuantity(quantity);
            items.add(item);

            totalAmount = totalAmount.add(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        }

        OrderEntity order = new OrderEntity();
        order.setOrderNo(TradeNo.order());
        order.setUserId(userId);
        order.setRequestId(request.requestId());
        order.setRequestHash(requestHash);
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

        this.notifications.recordCreatedOrder(order.getId());

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
        OrderEntity order = this.orderMapper.selectByIdForUpdate(id);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) return false;
        // 收款可能成功但回调还没到；先查询/关闭支付宝交易，再取消和回补库存。
        if (!this.paymentService.closePending(order)) return false;

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
                if (item.getSeckillActivityId() == null) {
                    this.productService.restoreStock(item.getProductId(), item.getQuantity());
                } else {
                    this.seckillService.restore(order, item);
                }
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
