-- 在已有业务库中执行。只增加下单幂等字段和唯一索引，旧订单保持 NULL。
-- 可重复执行；新建库直接使用 schema.sql。
SET @order_idempotency_upgrade = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'request_id'),
  'SELECT 1',
  'ALTER TABLE orders ADD COLUMN request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''下单幂等请求号（旧订单为空）'' AFTER user_id'
);
PREPARE order_idempotency_stmt FROM @order_idempotency_upgrade;
EXECUTE order_idempotency_stmt;
DEALLOCATE PREPARE order_idempotency_stmt;

SET @order_idempotency_upgrade = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'request_hash'),
  'SELECT 1',
  'ALTER TABLE orders ADD COLUMN request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT ''下单请求内容 SHA-256'' AFTER request_id'
);
PREPARE order_idempotency_stmt FROM @order_idempotency_upgrade;
EXECUTE order_idempotency_stmt;
DEALLOCATE PREPARE order_idempotency_stmt;

SET @order_idempotency_upgrade = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'orders' AND index_name = 'uk_user_request'),
  'SELECT 1',
  'ALTER TABLE orders ADD UNIQUE KEY uk_user_request (user_id, request_id)'
);
PREPARE order_idempotency_stmt FROM @order_idempotency_upgrade;
EXECUTE order_idempotency_stmt;
DEALLOCATE PREPARE order_idempotency_stmt;
