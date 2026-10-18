/*
 * Mall Platform 初始化数据（在 schema.sql 建表之后执行）
 * 账号密码均为 123456
 */

-- USE `mall_platform`;

-- INSERT INTO `user` (`name`, `username`, `password`, `email`, `role`, `registered_at`) VALUES
-- ('管理员', 'admin', '$2b$10$NHsjh/C.WrHRmpR/bM4ZHuf1YRz57zXqWT0y8xXTPIRxbq4UZ23G6', 'admin@mall.local', 'ADMIN', NOW()),
-- ('用户', 'demo', '$2b$10$NHsjh/C.WrHRmpR/bM4ZHuf1YRz57zXqWT0y8xXTPIRxbq4UZ23G6', 'user@mall.local', 'USER', NOW());
