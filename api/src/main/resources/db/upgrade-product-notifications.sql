-- 在现有业务库执行；只新增表，可重复执行。
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
