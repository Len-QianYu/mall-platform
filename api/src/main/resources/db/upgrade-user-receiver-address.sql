-- 在已有的 mall_platform 数据库中执行一次，为个人中心增加默认收货地址。
-- 新建库使用 schema.sql；已有用户的收货地址默认为 NULL。
ALTER TABLE `user`
  ADD COLUMN `receiver_address` VARCHAR(255) NULL COMMENT '默认收货地址' AFTER `phone`;
