--获取锁标识redis.call('get',KEYS[1])
--获取线程标识ARGV[1]


if(redis.call('get',KEYS[1]) == ARGV[1]) then
   return redis.call('del',KEYS[1])
end
return 0