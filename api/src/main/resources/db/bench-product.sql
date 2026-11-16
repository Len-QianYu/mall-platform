/*
 * Mall Platform 压测数据：往 product 追加 10 万件商品（在 schema.sql、data.sql 之后执行）
 *
 * 约定：
 *   1. 主键固定为 1001 ~ 101000，1000 以内留给 data.sql 和管理端新建的商品；执行时先删掉这一段再插，可重复执行；
 *   2. 各列由主键的 CRC32 哈希推出，不用 RAND()：每次生成的数据完全一样，优化前后的压测结果才能对比；
 *   3. 分布：6 个分类各占约 1/6，约 90% 上架，销量长尾（一半不到 10，约 0.1% 上万），创建时间散在近一年；
 *   4. 商品名 = 前缀 + 品类词 + 型号，如「便携蓝牙耳机 K327」，每个品类词约占 1/48，关键字搜索能搜出两千件左右；
 *   5. 只造商品：订单、购物车不引用这批商品，不影响原有的演示数据；主图按品类复用仓库内的演示素材。
 *   6. 图片在 tools/seed-product-images.ps1 执行后导入 RustFS；图片池和许可见 demo/product-images/。
 *
 * 执行（仓库根目录,即使用CMD命令行窗口， cd C:\Code-AI\mall-platform，约十几秒）：mysql -uroot -p mall_platform < api/src/main/resources/db/bench-product.sql
 * 清理：DELETE FROM product WHERE id BETWEEN 1001 AND 101000;
 */

SET NAMES utf8mb4;

USE `mall_platform`;

START TRANSACTION;

DELETE FROM `product` WHERE `id` BETWEEN 1001 AND 101000;

INSERT INTO `product` (`id`, `name`, `subtitle`, `description`, `category`, `price`, `stock`, `sales`, `main_image`, `status`, `created_at`)
WITH
  -- 0 ~ 9 自连接 5 次得到 10 万个序号，不用递归 CTE（默认最多递归 1000 层）
  `digit` AS (
    SELECT 0 AS `n` UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9
  ),
  `image_pool` AS (
-- BEGIN DEMO IMAGE POOL
    SELECT '蓝牙耳机' AS `image_item`, '/images/demo-headphones-1-9c2b7b1afa98.webp' AS `image0`, '/images/demo-headphones-2-375cb37674b0.webp' AS `image1`, '/images/demo-headphones-3-8814b0a484e3.webp' AS `image2`
    UNION ALL
    SELECT '机械键盘', '/images/demo-keyboard-1-87844ab6086e.webp', '/images/demo-keyboard-2-8c0ffc1e692a.webp', '/images/demo-keyboard-3-39512b3911c9.webp'
    UNION ALL
    SELECT '智能手表', '/images/demo-smartwatch-1-18bdf718b48e.webp', '/images/demo-smartwatch-2-5a5caa876873.webp', '/images/demo-smartwatch-3-8dd7d128ad93.webp'
    UNION ALL
    SELECT '快充充电器', '/images/demo-charger-1-f61153397046.webp', '/images/demo-charger-2-f61153397046.webp', '/images/demo-charger-3-f61153397046.webp'
    UNION ALL
    SELECT '移动电源', '/images/demo-powerbank-1-c9e6ae10400b.webp', '/images/demo-powerbank-2-e7ccd7cfe1c3.webp', '/images/demo-powerbank-3-e61a25ec79fb.webp'
    UNION ALL
    SELECT '无线鼠标', '/images/demo-mouse-1-9855336a9d7d.webp', '/images/demo-mouse-2-0ba2f4a9aba0.webp', '/images/demo-mouse-3-53795e4cc5b4.webp'
    UNION ALL
    SELECT '平板电脑', '/images/demo-tablet-1-cf161a33721d.webp', '/images/demo-tablet-2-118505188b94.webp', '/images/demo-tablet-3-4342d71831c2.webp'
    UNION ALL
    SELECT '数据线', '/images/demo-cable-1-4628da6066ea.webp', '/images/demo-cable-2-b19d77bf4baa.webp', '/images/demo-cable-3-9b10bba457f7.webp'
    UNION ALL
    SELECT '纯棉 T 恤', '/images/demo-tshirt-1-2813635fe1b0.webp', '/images/demo-tshirt-2-0195f212a149.webp', '/images/demo-tshirt-3-0195f212a149.webp'
    UNION ALL
    SELECT '连帽卫衣', '/images/demo-hoodie-1-6bb2b5f7391f.webp', '/images/demo-hoodie-2-559055fc8ec7.webp', '/images/demo-hoodie-3-d1d24ab846ad.webp'
    UNION ALL
    SELECT '羽绒服', '/images/demo-jacket-1-18370e5104f5.webp', '/images/demo-jacket-2-5e7ad765fba9.webp', '/images/demo-jacket-3-6d484434ab6f.webp'
    UNION ALL
    SELECT '运动鞋', '/images/demo-shoes-1-429eda4514c7.webp', '/images/demo-shoes-2-581dc55a6867.webp', '/images/demo-shoes-3-667149cf8490.webp'
    UNION ALL
    SELECT '牛仔裤', '/images/demo-jeans-1-3eabaa9b3db0.webp', '/images/demo-jeans-2-9f6de29749bb.webp', '/images/demo-jeans-3-4444374e8126.webp'
    UNION ALL
    SELECT '衬衫', '/images/demo-shirt-1-e4209cacdafe.webp', '/images/demo-shirt-2-1555020d2976.webp', '/images/demo-shirt-3-f5e5bd5ad24e.webp'
    UNION ALL
    SELECT '双肩包', '/images/demo-backpack-1-097800970b1f.webp', '/images/demo-backpack-2-ebfc0ee213b1.webp', '/images/demo-backpack-3-7a7d67afdbd2.webp'
    UNION ALL
    SELECT '棒球帽', '/images/demo-cap-1-b3125a012094.webp', '/images/demo-cap-2-77da2bdb2407.webp', '/images/demo-cap-3-2950b1a692d3.webp'
    UNION ALL
    SELECT '保温杯', '/images/demo-flask-1-1e929192c134.webp', '/images/demo-flask-2-e26fa3fc0b01.webp', '/images/demo-flask-3-fca6dee5a505.webp'
    UNION ALL
    SELECT '护颈枕', '/images/demo-neck-pillow-1-8eb2e90118a3.webp', '/images/demo-neck-pillow-2-c889f29182a9.webp', '/images/demo-neck-pillow-3-e50b0c16fc3b.webp'
    UNION ALL
    SELECT '加湿器', '/images/demo-humidifier-1-a502ddd3c356.webp', '/images/demo-humidifier-2-2bfac4a1084c.webp', '/images/demo-humidifier-3-2bfac4a1084c.webp'
    UNION ALL
    SELECT '收纳箱', '/images/demo-storage-box-1-51615989f943.webp', '/images/demo-storage-box-2-1a615b27b35b.webp', '/images/demo-storage-box-3-9454ded4c6cd.webp'
    UNION ALL
    SELECT '护眼台灯', '/images/demo-desk-lamp-1-9f3b28efba21.webp', '/images/demo-desk-lamp-2-8cf8b1e85cae.webp', '/images/demo-desk-lamp-3-be7db04ee517.webp'
    UNION ALL
    SELECT '床品四件套', '/images/demo-bedding-1-883cfc6f6157.webp', '/images/demo-bedding-2-94f69fd7f94d.webp', '/images/demo-bedding-3-2d12796937ae.webp'
    UNION ALL
    SELECT '抱枕', '/images/demo-cushion-1-72a79da303a9.webp', '/images/demo-cushion-2-430e63cadd82.webp', '/images/demo-cushion-3-774f7540504c.webp'
    UNION ALL
    SELECT '香薰蜡烛', '/images/demo-candle-1-c1da01b854a8.webp', '/images/demo-candle-2-cbe662deb061.webp', '/images/demo-candle-3-e0daa198b58e.webp'
    UNION ALL
    SELECT '咖啡豆', '/images/demo-coffee-1-a03f11f5afb2.webp', '/images/demo-coffee-2-940dbeea0d7f.webp', '/images/demo-coffee-3-67e59e5a66bf.webp'
    UNION ALL
    SELECT '每日坚果', '/images/demo-nuts-1-829a35793a5d.webp', '/images/demo-nuts-2-20dfc8e4e51a.webp', '/images/demo-nuts-3-3b2cf1d20ca2.webp'
    UNION ALL
    SELECT '绿茶', '/images/demo-tea-1-4b2183c83a08.webp', '/images/demo-tea-2-8b0c7b1c0cbf.webp', '/images/demo-tea-3-382bbcdb40b0.webp'
    UNION ALL
    SELECT '牛肉干', '/images/demo-jerky-1-c2b0e3827586.webp', '/images/demo-jerky-2-f804428a91be.webp', '/images/demo-jerky-3-784c28e46f98.webp'
    UNION ALL
    SELECT '燕麦片', '/images/demo-oats-1-81c9efba0fc4.webp', '/images/demo-oats-2-c5102d639c1b.webp', '/images/demo-oats-3-b25cb0de10ed.webp'
    UNION ALL
    SELECT '蜂蜜', '/images/demo-honey-1-92034e99984c.webp', '/images/demo-honey-2-096999b1a2e7.webp', '/images/demo-honey-3-566d9bb57760.webp'
    UNION ALL
    SELECT '曲奇饼干', '/images/demo-cookies-1-7f73366a45a7.webp', '/images/demo-cookies-2-e38dd32f39d2.webp', '/images/demo-cookies-3-14dc2310edae.webp'
    UNION ALL
    SELECT '果干', '/images/demo-dried-fruit-1-2dcb004d97de.webp', '/images/demo-dried-fruit-2-520bda03de4b.webp', '/images/demo-dried-fruit-3-0dd296e780c9.webp'
    UNION ALL
    SELECT '洁面乳', '/images/demo-cleanser-1-2dc5a95600c7.webp', '/images/demo-cleanser-2-2dc5a95600c7.webp', '/images/demo-cleanser-3-2dc5a95600c7.webp'
    UNION ALL
    SELECT '保湿面霜', '/images/demo-face-cream-1-82182d3ec639.webp', '/images/demo-face-cream-2-51dd0fbde8a4.webp', '/images/demo-face-cream-3-b16b2b8a6314.webp'
    UNION ALL
    SELECT '精华液', '/images/demo-serum-1-46f93b2e9242.webp', '/images/demo-serum-2-46f93b2e9242.webp', '/images/demo-serum-3-46f93b2e9242.webp'
    UNION ALL
    SELECT '防晒霜', '/images/demo-sunscreen-1-bcb2538a0a55.webp', '/images/demo-sunscreen-2-777ca8649a0d.webp', '/images/demo-sunscreen-3-777ca8649a0d.webp'
    UNION ALL
    SELECT '口红', '/images/demo-lipstick-1-ca3b5e6f2d4e.webp', '/images/demo-lipstick-2-bec3e74f0921.webp', '/images/demo-lipstick-3-084e05f9d77f.webp'
    UNION ALL
    SELECT '面膜', '/images/demo-face-mask-1-3c726588e9fd.webp', '/images/demo-face-mask-2-b52af23f14bc.webp', '/images/demo-face-mask-3-233a5e3fdc9b.webp'
    UNION ALL
    SELECT '爽肤水', '/images/demo-toner-1-62e9a991f4b3.webp', '/images/demo-toner-2-62e9a991f4b3.webp', '/images/demo-toner-3-3920e3d0a9eb.webp'
    UNION ALL
    SELECT '护手霜', '/images/demo-hand-cream-1-0414877d50f0.webp', '/images/demo-hand-cream-2-0414877d50f0.webp', '/images/demo-hand-cream-3-0414877d50f0.webp'
    UNION ALL
    SELECT 'Java 编程指南', '/images/demo-java-book-1-686d9de109d5.webp', '/images/demo-java-book-2-3e42cce11012.webp', '/images/demo-java-book-3-14570d532c85.webp'
    UNION ALL
    SELECT 'Python 数据分析', '/images/demo-python-book-1-ba4da44337e0.webp', '/images/demo-python-book-2-5628b112e134.webp', '/images/demo-python-book-3-cca95511862f.webp'
    UNION ALL
    SELECT '机器学习实战', '/images/demo-ml-book-1-eb7ddb57cc4a.webp', '/images/demo-ml-book-2-a5f5c9e0947f.webp', '/images/demo-ml-book-3-fb95abab52cd.webp'
    UNION ALL
    SELECT '数据库系统原理', '/images/demo-database-book-1-eed7d45ba6b5.webp', '/images/demo-database-book-2-21d7f659bf9a.webp', '/images/demo-database-book-3-2602595cb53c.webp'
    UNION ALL
    SELECT '悬疑小说集', '/images/demo-novel-1-3a5ff9e7f893.webp', '/images/demo-novel-2-4f4d0378d290.webp', '/images/demo-novel-3-743f7249e45e.webp'
    UNION ALL
    SELECT '中国通史', '/images/demo-history-book-1-45d5ebbb95e5.webp', '/images/demo-history-book-2-a09fe2e7fb6f.webp', '/images/demo-history-book-3-98254b42d726.webp'
    UNION ALL
    SELECT '英语词汇手册', '/images/demo-dictionary-1-4b272ad3c95c.webp', '/images/demo-dictionary-2-a2bd7514401e.webp', '/images/demo-dictionary-3-696c6ed5a030.webp'
    UNION ALL
    SELECT '儿童绘本', '/images/demo-picture-book-1-cb2f123007f5.webp', '/images/demo-picture-book-2-fdc7e181f908.webp', '/images/demo-picture-book-3-db04fe50d070.webp'
-- END DEMO IMAGE POOL
  ),
  -- 三个互不相关的哈希值，分别用来推出不同的列，避免列与列之间互相关联
  `hashed` AS (
    SELECT
      `id`,
      CRC32(`id`)               AS `h1`,
      CRC32(CONCAT(`id`, '#'))  AS `h2`,
      CRC32(CONCAT(`id`, '@'))  AS `h3`
    FROM (
      SELECT 1001 + d1.`n` + d2.`n` * 10 + d3.`n` * 100 + d4.`n` * 1000 + d5.`n` * 10000 AS `id`
      FROM `digit` d1, `digit` d2, `digit` d3, `digit` d4, `digit` d5
    ) AS `seq`
  ),
  -- h1 决定分类、品类词、前缀、型号；h2 决定上架状态、销量、价格；h3 决定库存、卖点、创建时间
  `picked` AS (
    SELECT
      `id`, `h2`, `h3`,
      MOD(`h1`, 6) AS `cat`,
      ELT(MOD(`h1`, 6) * 8 + MOD(`h1` DIV 6, 8) + 1,
        '蓝牙耳机', '机械键盘', '智能手表', '快充充电器', '移动电源', '无线鼠标', '平板电脑', '数据线',
        '纯棉 T 恤', '连帽卫衣', '羽绒服', '运动鞋', '牛仔裤', '衬衫', '双肩包', '棒球帽',
        '保温杯', '护颈枕', '加湿器', '收纳箱', '护眼台灯', '床品四件套', '抱枕', '香薰蜡烛',
        '咖啡豆', '每日坚果', '绿茶', '牛肉干', '燕麦片', '蜂蜜', '曲奇饼干', '果干',
        '洁面乳', '保湿面霜', '精华液', '防晒霜', '口红', '面膜', '爽肤水', '护手霜',
        'Java 编程指南', 'Python 数据分析', '机器学习实战', '数据库系统原理', '悬疑小说集', '中国通史', '英语词汇手册', '儿童绘本'
      ) AS `item`,
      ELT(MOD(`h1` DIV 48, 10) + 1, '新款', '经典', '轻奢', '简约', '加厚', '便携', '升级版', '国潮', '旗舰', '入门') AS `prefix`,
      CONCAT(SUBSTRING('ABCDEFGHJKLMNPQRSTUVWXYZ', MOD(`h1` DIV 480, 24) + 1, 1), 100 + MOD(`h1` DIV 11520, 900)) AS `model`
    FROM `hashed`
  )
SELECT
  `id`,
  CONCAT(`prefix`, `item`, ' ', `model`),
  CONCAT(`item`, '，', ELT(MOD(`h3`, 5) + 1, '限时特惠', '品质之选', '热销爆款', '新品上市', '官方正品')),
  CONCAT(`prefix`, `item`, ' ', `model`, '。\n', ELT(`cat` + 1,
    '采用新一代芯片方案，连接稳定、功耗更低，支持主流手机与电脑。\n整机通过多项可靠性测试，享一年质保，七天无理由退换。',
    '精选优质面料，做工细致，版型经过多轮打版调整，上身舒适不紧绷。\n多色多码可选，支持七天无理由退换，洗涤请参考吊牌说明。',
    '选用环保材质，无异味，细节处理圆润不伤手，适合日常居家使用。\n简约设计百搭各种家装风格，享一年质保。',
    '原料产地直采，严格品控，独立包装更卫生，开袋即食方便携带。\n请存放于阴凉干燥处，开封后尽快食用。',
    '温和配方，不含酒精与香精，经皮肤测试，适合大多数肤质日常使用。\n使用前建议先在耳后做局部测试。',
    '正版图书，内容经多次修订，讲解循序渐进，配有大量示例与习题。\n适合入门学习与案头查阅，下单后当天发货。'
  )),
  ELT(`cat` + 1, '数码', '服饰', '家居', '食品', '美妆', '图书'),
  -- 价格 9.90 ~ 2009.80
  ROUND(9.9 + MOD(`h2` DIV 100000, 20000) / 10, 1),
  -- 约 1/500 售罄
  MOD(`h3` DIV 5, 500),
  -- 帕累托分布：一半商品销量不到 10，约 1% 过千，约 0.1% 上万，最高约 10 万
  FLOOR(10 / (1 - MOD(`h2` DIV 10, 10000) / 10000)) - 10,
  CASE MOD(CRC32(CONCAT(`id`, '#image')), 3)
    WHEN 0 THEN `image0`
    WHEN 1 THEN `image1`
    ELSE `image2`
  END,
  IF(MOD(`h2`, 10) = 0, 0, 1),
  -- 2026-11-01 之前一年内的任意时刻
  TIMESTAMP('2026-11-01 00:00:00') - INTERVAL MOD(`h3` DIV 2500, 525600) MINUTE
FROM `picked` LEFT JOIN `image_pool` ON `picked`.`item` = `image_pool`.`image_item`;

COMMIT;

-- 一次插入大量数据后，索引统计信息不会立刻更新，先重新统计，EXPLAIN 看到的执行计划才准确
ANALYZE TABLE `product`;

SELECT
  COUNT(*)                      AS `商品总数`,
  SUM(`status` = 1)             AS `上架`,
  COUNT(DISTINCT `category`)    AS `分类数`,
  MAX(`sales`)                  AS `最高销量`
FROM `product`;
