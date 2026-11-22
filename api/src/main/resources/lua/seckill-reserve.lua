-- Redis 是短期预扣筛选；库存和每人额度最终由数据库条件更新确认。
-- 所有字段放在一个 key 内，兼容 Redis Cluster。
local key = KEYS[1]
local quantity = tonumber(ARGV[1])
local user = 'u:' .. ARGV[2]
local reservation = 'r:' .. ARGV[2] .. ':' .. ARGV[3]
if not quantity or quantity < 1 then return -3 end
if redis.call('EXISTS', key) == 0 then
    redis.call('HSET', key, 'stock', ARGV[4])
    redis.call('PEXPIRE', key, ARGV[7])
end
local previous = redis.call('HGET', key, reservation)
if previous then
    if tonumber(previous) == quantity then return 1 end
    return -3
end
local used = tonumber(redis.call('HGET', key, user))
if not used then
    used = tonumber(ARGV[5])
    redis.call('HSET', key, user, used)
end
if used + quantity > tonumber(ARGV[6]) then return -2 end
if tonumber(redis.call('HGET', key, 'stock')) < quantity then return -1 end
redis.call('HINCRBY', key, 'stock', -quantity)
redis.call('HINCRBY', key, user, quantity)
redis.call('HSET', key, reservation, quantity)
return 1
