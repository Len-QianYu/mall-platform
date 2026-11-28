-- 新订单通知按角色和状态查管理员，避免在用户数据增长后逐订单扫描整张用户表。
-- 在目标业务库执行；可重复运行。
SET @user_role_index_exists = (
  SELECT COUNT(*) FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'user' AND index_name = 'idx_user_role_status'
);
SET @user_role_index_sql = IF(@user_role_index_exists > 0,
  'SELECT ''idx_user_role_status already exists'' AS result',
  'ALTER TABLE `user` ADD INDEX `idx_user_role_status` (`role`, `status`)');
PREPARE user_role_index_statement FROM @user_role_index_sql;
EXECUTE user_role_index_statement;
DEALLOCATE PREPARE user_role_index_statement;
