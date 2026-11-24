-- 在现有业务库执行；只新增表，可重复执行，不补发历史订单的发货通知。
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
