-- 在已有业务库执行，可重复运行。保留旧邮箱资料，验证后才启用邮箱登录。
-- 唯一索引只约束已验证的邮箱，旧的未验证邮箱可以重复。
SET @email_auth_upgrade = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'email_verified'),
  'SELECT 1',
  'ALTER TABLE `user` ADD COLUMN email_verified TINYINT NOT NULL DEFAULT 0 COMMENT ''邮箱是否已验证'' AFTER email'
);
PREPARE email_auth_stmt FROM @email_auth_upgrade;
EXECUTE email_auth_stmt;
DEALLOCATE PREPARE email_auth_stmt;

SET @email_auth_upgrade = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'auth_version'),
  'SELECT 1',
  'ALTER TABLE `user` ADD COLUMN auth_version BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''登录凭据版本'' AFTER email_verified'
);
PREPARE email_auth_stmt FROM @email_auth_upgrade;
EXECUTE email_auth_stmt;
DEALLOCATE PREPARE email_auth_stmt;

SET @email_auth_upgrade = IF(
  EXISTS(SELECT 1 FROM information_schema.columns
         WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'login_email'),
  'SELECT 1',
  'ALTER TABLE `user` ADD COLUMN login_email VARCHAR(128) GENERATED ALWAYS AS (CASE WHEN email_verified = 1 THEN LOWER(TRIM(email)) ELSE NULL END) STORED AFTER auth_version'
);
PREPARE email_auth_stmt FROM @email_auth_upgrade;
EXECUTE email_auth_stmt;
DEALLOCATE PREPARE email_auth_stmt;

SET @email_auth_upgrade = IF(
  EXISTS(SELECT 1 FROM information_schema.statistics
         WHERE table_schema = DATABASE() AND table_name = 'user' AND index_name = 'uk_user_login_email'),
  'SELECT 1',
  'ALTER TABLE `user` ADD UNIQUE KEY uk_user_login_email (login_email)'
);
PREPARE email_auth_stmt FROM @email_auth_upgrade;
EXECUTE email_auth_stmt;
DEALLOCATE PREPARE email_auth_stmt;
