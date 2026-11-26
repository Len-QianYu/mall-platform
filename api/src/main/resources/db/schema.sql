/*
 * Prompt Pals 业务库建表
 * 库：mall_platform —— 12 张表：用户、商品、交易、秒杀，以及收藏、通知事件和通知记录
 *
 * 约定：
 *   1. 不建物理外键：关联关系由应用层维护（便于逻辑删除与后续），高频关联列均建索引；
 *   2. 金额统一 DECIMAL(10,2)，单位：元；
 *   3. 枚举列用 VARCHAR 存英文编码（与 Java 枚举对齐），取值范围写在列注释里；
 *   4. 订单明细冗余商品名/主图/单价，作为下单时快照，避免商品后续修改影响历史订单；
 *   5. 主键预期量级：user / product 用 INT UNSIGNED，订单链路（orders / order_item / payment）用 BIGINT UNSIGNED。
 *
 */

CREATE DATABASE `mall_platform`;

USE `mall_platform`;

/* ---------- 1. 用户 ---------- */
CREATE TABLE `user` (
  `id`            INT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `name`          VARCHAR(32)   NULL                    COMMENT '名字',
  `username`      VARCHAR(16)   NOT NULL                COMMENT '用户名',
  `password`      VARCHAR(512)  NOT NULL                COMMENT '密码（BCrypt 哈希）',
  `gender`        CHAR(1)       NULL                    COMMENT '性别【F:女;M:男】',
  `email`         VARCHAR(128)  NULL                    COMMENT '邮箱',
  `email_verified` TINYINT      NOT NULL DEFAULT 0      COMMENT '邮箱是否已验证',
  `auth_version`  BIGINT UNSIGNED NOT NULL DEFAULT 0    COMMENT '登录凭据版本',
  `login_email`   VARCHAR(128) GENERATED ALWAYS AS
                    (CASE WHEN email_verified = 1 THEN LOWER(TRIM(email)) ELSE NULL END) STORED,
  `phone`         VARCHAR(20)   NULL                    COMMENT '手机号',
  `province`      VARCHAR(50)   NULL                    COMMENT '省份',
  `city`          VARCHAR(50)   NULL                    COMMENT '城市',
  `receiver_address` VARCHAR(255) NULL                  COMMENT '默认收货地址',
  `avatar_url`    VARCHAR(512)  NULL                    COMMENT '头像访问',
  `role`          VARCHAR(16)   NOT NULL DEFAULT 'user' COMMENT '角色【user:普通用户;admin:管理员;super_admin:super管理员】',
  `status`        TINYINT       NOT NULL DEFAULT 1      COMMENT '状态【1:启用;0:禁用】',
  `registered_at` DATETIME      NOT NULL                COMMENT '注册于',
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_user_login_email` (`login_email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '用户表';

/* ---------- 2. 商品 ---------- */
CREATE TABLE `product` (
  `id`          INT UNSIGNED   NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `name`        VARCHAR(128)   NOT NULL                COMMENT '商品名',
  `subtitle`    VARCHAR(255)   NULL                    COMMENT '副标题（卖点）',
  `description` TEXT           NULL                    COMMENT '商品详情',
  `category`    VARCHAR(32)    NOT NULL                COMMENT '分类',
  `price`       DECIMAL(10, 2) NOT NULL                COMMENT '售价（元）',
  `stock`       INT UNSIGNED   NOT NULL DEFAULT 0      COMMENT '库存',
  `sales`       INT UNSIGNED   NOT NULL DEFAULT 0      COMMENT '销量',
  `main_image`  VARCHAR(512)   NULL                    COMMENT '主图 URL（RustFS 对象存储）',
  `status`      TINYINT        NOT NULL DEFAULT 1      COMMENT '状态【1:上架;0:下架】',
  `created_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_status` (`category`, `status`),
  -- 前台按销量排序：WHERE status = 1 ORDER BY sales DESC, id DESC 倒序读这个索引，读够一页就停，不用全表扫描再排序
  -- （二级索引末尾自带主键 id，排序里的 id 兜底也能用上；status 回表判断，九成商品在售，多读几条就够）。
  -- 不建 (status, sales)：status 只有 0/1，优化器会拿它走 ref，关键字计数、按价格/最新排序都先读 9 万条索引项再逐条回表，实测比全表扫描慢 2~4 倍
  KEY `idx_sales` (`sales`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '商品表';

/* ---------- 3. 订单主表 ---------- */
CREATE TABLE `orders` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `order_no`         VARCHAR(32)     NOT NULL                COMMENT '订单号（对外展示）',
  `user_id`          INT UNSIGNED    NOT NULL                COMMENT '用户 ID（逻辑外键 user.id）',
  `request_id`       VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '下单幂等请求号（旧订单为空）',
  `request_hash`     CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '下单请求内容 SHA-256',
  `total_amount`     DECIMAL(10, 2)  NOT NULL                COMMENT '订单总额（元）',
  `status`           VARCHAR(16)     NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '状态【PENDING_PAYMENT:待支付;PAID:已支付;SHIPPED:已发货;COMPLETED:已完成;CANCELLED:已取消;REFUNDED:已退款】',
  `receiver_name`    VARCHAR(32)     NOT NULL                COMMENT '收货人（下单时快照）',
  `receiver_phone`   VARCHAR(20)     NOT NULL                COMMENT '收货电话（下单时快照）',
  `receiver_address` VARCHAR(255)    NOT NULL                COMMENT '收货地址（下单时快照）',
  `remark`           VARCHAR(255)    NULL                    COMMENT '买家备注',
  `paid_at`          DATETIME        NULL                    COMMENT '支付时间',
  `shipped_at`       DATETIME        NULL                    COMMENT '发货时间',
  `completed_at`     DATETIME        NULL                    COMMENT '完成时间',
  `cancelled_at`     DATETIME        NULL                    COMMENT '取消时间',
  `created_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_user_request` (`user_id`, `request_id`),
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '订单主表';

/* ---------- 4. 订单明细 ---------- */
CREATE TABLE `order_item` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `order_id`      BIGINT UNSIGNED NOT NULL                COMMENT '订单 ID（逻辑外键 orders.id）',
  `product_id`    INT UNSIGNED    NOT NULL                COMMENT '商品 ID（逻辑外键 product.id）',
  `seckill_activity_id` BIGINT UNSIGNED NULL              COMMENT '秒杀活动 ID（普通订单为空）',
  `product_name`  VARCHAR(128)    NOT NULL                COMMENT '商品名（下单时快照）',
  `product_image` VARCHAR(512)    NULL                    COMMENT '主图（下单时快照）',
  `unit_price`    DECIMAL(10, 2)  NOT NULL                COMMENT '成交单价（下单时快照）',
  `quantity`      INT UNSIGNED    NOT NULL                COMMENT '购买数量',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_seckill_activity` (`seckill_activity_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '订单明细表';

/* ---------- 5. 购物车 ---------- */
CREATE TABLE `cart` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `user_id`    INT UNSIGNED    NOT NULL                COMMENT '用户 ID（逻辑外键 user.id）',
  `product_id` INT UNSIGNED    NOT NULL                COMMENT '商品 ID（逻辑外键 product.id）',
  `quantity`   INT UNSIGNED    NOT NULL DEFAULT 1      COMMENT '数量',
  `checked`    TINYINT         NOT NULL DEFAULT 1      COMMENT '是否勾选【1:勾选;0:未勾选】（结算用）',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '购物车表';

/* ---------- 6. 支付流水 ---------- */
CREATE TABLE `payment` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `payment_no` VARCHAR(32)     NOT NULL                COMMENT '支付流水号（对外展示）',
  `trade_no`   VARCHAR(64)     NULL                    COMMENT '第三方交易号（模拟支付为空）',
  `order_id`   BIGINT UNSIGNED NOT NULL                COMMENT '订单 ID（逻辑外键 orders.id，一单可多条流水：失败重试）',
  `user_id`    INT UNSIGNED    NOT NULL                COMMENT '用户 ID（逻辑外键 user.id）',
  `amount`     DECIMAL(10, 2)  NOT NULL                COMMENT '支付金额（元）',
  `method`     VARCHAR(16)     NOT NULL                COMMENT '支付方式【ALIPAY:支付宝;WECHAT:微信;MOCK:模拟支付】',
  `status`     VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '状态【PENDING:待支付;SUCCESS:成功;FAILED:失败;REFUNDED:已退款】',
  `paid_at`    DATETIME        NULL                    COMMENT '支付成功时间',
  `created_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_payment_no` (`payment_no`),
  UNIQUE KEY `uk_trade_no` (`trade_no`),
  KEY `idx_method_status_id` (`method`, `status`, `id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '支付流水表';

/* ---------- 7 / 8. 秒杀活动与用户限购额度 ---------- */
CREATE TABLE IF NOT EXISTS seckill_activity (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '活动 ID',
  product_id INT UNSIGNED NOT NULL COMMENT '商品 ID',
  title VARCHAR(128) NOT NULL COMMENT '活动标题',
  original_price DECIMAL(10,2) NOT NULL COMMENT '划拨时原价',
  seckill_price DECIMAL(10,2) NOT NULL COMMENT '秒杀成交价',
  total_stock INT UNSIGNED NOT NULL COMMENT '从普通库存划拨的总配额',
  stock INT UNSIGNED NOT NULL COMMENT '剩余秒杀配额',
  returned_stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已归还普通库存的配额',
  limit_per_user INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '每人同时占用或购买的上限',
  starts_at DATETIME NOT NULL COMMENT '开始时间',
  ends_at DATETIME NOT NULL COMMENT '结束时间',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1:开放;0:手动关闭;2:已结束',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_product_id (product_id),
  KEY idx_status_ends (status, ends_at),
  KEY idx_starts_at (starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '秒杀活动与独立库存配额';

CREATE TABLE IF NOT EXISTS seckill_purchase (
  activity_id BIGINT UNSIGNED NOT NULL COMMENT '秒杀活动 ID',
  user_id INT UNSIGNED NOT NULL COMMENT '用户 ID',
  quantity INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '待支付及有效购买数量，取消和退款释放',
  PRIMARY KEY (activity_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '秒杀用户持久化限购额度';

/* ---------- 9 / 10 / 11. 商品收藏、通知发件箱与通知记录 ---------- */
CREATE TABLE IF NOT EXISTS product_favorite (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id INT UNSIGNED NOT NULL,
  product_id INT UNSIGNED NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id),
  UNIQUE KEY uk_favorite_user_product (user_id, product_id),
  KEY idx_favorite_product_created (product_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '商品收藏及通知订阅';

CREATE TABLE IF NOT EXISTS product_notification_event (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  product_id INT UNSIGNED NOT NULL,
  type VARCHAR(16) NOT NULL COMMENT 'LISTED / PRICE_DROP / PRICE_RISE',
  product_name VARCHAR(128) NOT NULL,
  product_image VARCHAR(512) NULL,
  old_price DECIMAL(10,2) NOT NULL,
  new_price DECIMAL(10,2) NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  published_at DATETIME(6) NULL COMMENT 'RocketMQ 确认发送时间，失败时继续重试',
  consumed_at DATETIME(6) NULL COMMENT '通知落库时间，重复消息不重复处理',
  PRIMARY KEY (id),
  KEY idx_event_pending (published_at, consumed_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '与商品变动一起提交的消息发件箱';

CREATE TABLE IF NOT EXISTS product_notification (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id INT UNSIGNED NOT NULL,
  event_id BIGINT UNSIGNED NOT NULL,
  product_id INT UNSIGNED NOT NULL,
  type VARCHAR(16) NOT NULL,
  product_name VARCHAR(128) NOT NULL,
  product_image VARCHAR(512) NULL,
  old_price DECIMAL(10,2) NOT NULL,
  new_price DECIMAL(10,2) NOT NULL,
  read_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_notification_event_user (event_id, user_id),
  KEY idx_notification_user_id (user_id, id),
  KEY idx_notification_user_unread (user_id, read_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '可离线查看的商品通知';

/* ---------- 12. 管理员新订单通知 ---------- */
CREATE TABLE IF NOT EXISTS order_notification (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id INT UNSIGNED NOT NULL COMMENT '接收通知的管理员 ID',
  order_id BIGINT UNSIGNED NOT NULL,
  order_no VARCHAR(32) NOT NULL,
  buyer_id INT UNSIGNED NOT NULL,
  buyer_username VARCHAR(16) NOT NULL,
  buyer_name VARCHAR(32) NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  read_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_notification_order_user (order_id, user_id),
  KEY idx_order_notification_user_id (user_id, id),
  KEY idx_order_notification_user_unread (user_id, read_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '与订单一起提交的管理员新订单通知';

/* ---------- 13. 买家发货通知 ---------- */
CREATE TABLE IF NOT EXISTS order_shipment_notification (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id INT UNSIGNED NOT NULL COMMENT '订单买家 ID',
  order_id BIGINT UNSIGNED NOT NULL,
  order_no VARCHAR(32) NOT NULL,
  total_amount DECIMAL(10,2) NOT NULL,
  shipped_at DATETIME(6) NOT NULL,
  read_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_shipment_order_user (order_id, user_id),
  KEY idx_order_shipment_user_created (user_id, created_at, id),
  KEY idx_order_shipment_user_unread (user_id, read_at, created_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '与发货状态一起提交的买家通知';
