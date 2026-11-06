/*
 * Mall Platform 压测数据：往 product 追加 10 万件商品（在 schema.sql、data.sql 之后执行）
 *
 * 约定：
 *   1. 主键固定为 1001 ~ 101000，1000 以内留给 data.sql 和管理端新建的商品；执行时先删掉这一段再插，可重复执行；
 *   2. 各列由主键的 CRC32 哈希推出，不用 RAND()：每次生成的数据完全一样，优化前后的压测结果才能对比；
 *   3. 分布：6 个分类各占约 1/6，约 90% 上架，销量长尾（一半不到 10，约 0.1% 上万），创建时间散在近一年；
 *   4. 商品名 = 前缀 + 品类词 + 型号，如「便携蓝牙耳机 K327」，每个品类词约占 1/48，关键字搜索能搜出两千件左右；
 *   5. 只造商品：订单、购物车不引用这批商品，不影响原有的演示数据。
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
  NULL,
  IF(MOD(`h2`, 10) = 0, 0, 1),
  -- 2026-11-01 之前一年内的任意时刻
  TIMESTAMP('2026-11-01 00:00:00') - INTERVAL MOD(`h3` DIV 2500, 525600) MINUTE
FROM `picked`;

COMMIT;

-- 一次插入大量数据后，索引统计信息不会立刻更新，先重新统计，EXPLAIN 看到的执行计划才准确
ANALYZE TABLE `product`;

SELECT
  COUNT(*)                      AS `商品总数`,
  SUM(`status` = 1)             AS `上架`,
  COUNT(DISTINCT `category`)    AS `分类数`,
  MAX(`sales`)                  AS `最高销量`
FROM `product`;
