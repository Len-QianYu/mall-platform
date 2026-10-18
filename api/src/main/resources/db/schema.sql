/*
 * Mall Platform 业务库建表
 * 库：mall_platform —— 6 张表：user / product / orders / order_item / cart / payment
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
  `phone`         VARCHAR(20)   NULL                    COMMENT '手机号',
  `avatar_url`    VARCHAR(512)  NULL                    COMMENT '头像访问',
  `role`          VARCHAR(16)   NOT NULL DEFAULT 'USER' COMMENT '角色【USER:普通用户;ADMIN:管理员】',
  `status`        TINYINT       NOT NULL DEFAULT 1      COMMENT '状态【1:启用;0:禁用】',
  `registered_at` DATETIME      NOT NULL                COMMENT '注册于',
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
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
  KEY `idx_category_status` (`category`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '商品表';

/* ---------- 3. 订单主表 ---------- */
CREATE TABLE `orders` (
  `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `order_no`         VARCHAR(32)     NOT NULL                COMMENT '订单号（对外展示）',
  `user_id`          INT UNSIGNED    NOT NULL                COMMENT '用户 ID（逻辑外键 user.id）',
  `total_amount`     DECIMAL(10, 2)  NOT NULL                COMMENT '订单总额（元）',
  `status`           VARCHAR(16)     NOT NULL DEFAULT 'PENDING_PAYMENT' COMMENT '状态【PENDING_PAYMENT:待支付;PAID:已支付;SHIPPED:已发货;COMPLETED:已完成;CANCELLED:已取消】',
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
  KEY `idx_user_created` (`user_id`, `created_at`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '订单主表';

/* ---------- 4. 订单明细 ---------- */
CREATE TABLE `order_item` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'ID',
  `order_id`      BIGINT UNSIGNED NOT NULL                COMMENT '订单 ID（逻辑外键 orders.id）',
  `product_id`    INT UNSIGNED    NOT NULL                COMMENT '商品 ID（逻辑外键 product.id）',
  `product_name`  VARCHAR(128)    NOT NULL                COMMENT '商品名（下单时快照）',
  `product_image` VARCHAR(512)    NULL                    COMMENT '主图（下单时快照）',
  `unit_price`    DECIMAL(10, 2)  NOT NULL                COMMENT '成交单价（下单时快照）',
  `quantity`      INT UNSIGNED    NOT NULL                COMMENT '购买数量',
  `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
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
  KEY `idx_order_id` (`order_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT '支付流水表';
