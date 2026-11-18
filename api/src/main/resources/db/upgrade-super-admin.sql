-- 在现有业务库执行，将用户名为“千羽”的已有账号设为 S管理员。
-- super_admin 适用于现有 VARCHAR(16) 角色列，使用管理员入口登录。
UPDATE `user`
SET `role` = 'super_admin'
WHERE `username` = '千羽' AND `role` <> 'super_admin';
