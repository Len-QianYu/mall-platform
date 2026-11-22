-- 在当前业务库执行，可重复运行；只新增表、可空字段和索引。
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

SET @seckill_upgrade = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'order_item' AND column_name = 'seckill_activity_id'),
  'SELECT 1',
  'ALTER TABLE order_item ADD COLUMN seckill_activity_id BIGINT UNSIGNED NULL COMMENT ''秒杀活动 ID（普通订单为空）'' AFTER product_id'
);
PREPARE seckill_upgrade_stmt FROM @seckill_upgrade;
EXECUTE seckill_upgrade_stmt;
DEALLOCATE PREPARE seckill_upgrade_stmt;

SET @seckill_upgrade = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'order_item' AND index_name = 'idx_seckill_activity'),
  'SELECT 1',
  'ALTER TABLE order_item ADD KEY idx_seckill_activity (seckill_activity_id)'
);
PREPARE seckill_upgrade_stmt FROM @seckill_upgrade;
EXECUTE seckill_upgrade_stmt;
DEALLOCATE PREPARE seckill_upgrade_stmt;
