-- 在现有业务库执行；只新增表，可重复执行，不补发历史订单。
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
