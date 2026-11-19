-- 在已有业务库中执行；只增加支付宝对账字段和索引，不改动现有流水。
-- 可重复执行。新建库直接使用 schema.sql。
SET @payment_upgrade = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'payment' AND column_name = 'trade_no'),
  'SELECT 1',
  'ALTER TABLE payment ADD COLUMN trade_no VARCHAR(64) NULL COMMENT ''第三方交易号（模拟支付为空）'' AFTER payment_no'
);
PREPARE payment_upgrade_stmt FROM @payment_upgrade;
EXECUTE payment_upgrade_stmt;
DEALLOCATE PREPARE payment_upgrade_stmt;

SET @payment_upgrade = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'payment' AND index_name = 'uk_trade_no'),
  'SELECT 1',
  'ALTER TABLE payment ADD UNIQUE KEY uk_trade_no (trade_no)'
);
PREPARE payment_upgrade_stmt FROM @payment_upgrade;
EXECUTE payment_upgrade_stmt;
DEALLOCATE PREPARE payment_upgrade_stmt;

SET @payment_upgrade = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'payment' AND index_name = 'idx_method_status_id'),
  'SELECT 1',
  'ALTER TABLE payment ADD KEY idx_method_status_id (method, status, id)'
);
PREPARE payment_upgrade_stmt FROM @payment_upgrade;
EXECUTE payment_upgrade_stmt;
DEALLOCATE PREPARE payment_upgrade_stmt;
