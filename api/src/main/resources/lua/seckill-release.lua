local key = KEYS[1]
local user = 'u:' .. ARGV[1]
local reservation = 'r:' .. ARGV[1] .. ':' .. ARGV[2]
local quantity = tonumber(redis.call('HGET', key, reservation))
if not quantity then return 0 end
redis.call('HDEL', key, reservation)
redis.call('HINCRBY', key, 'stock', quantity)
redis.call('HINCRBY', key, user, -quantity)
return 1
