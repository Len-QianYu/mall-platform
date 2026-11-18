-- 在已有的 mall_platform 数据库中执行一次，为个人资料增加地点字段。
-- 新建库使用 schema.sql；已有用户的地点默认为 NULL。
ALTER TABLE `user`
  ADD COLUMN `province` VARCHAR(50) NULL COMMENT '省份' AFTER `phone`,
  ADD COLUMN `city` VARCHAR(50) NULL COMMENT '城市' AFTER `province`;
