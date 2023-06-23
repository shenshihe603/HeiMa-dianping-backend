--1.参数列表
--1.1优惠券id
local voucherId=ARGV[1]
--1.2用户ID
local userId =ARGV[2]
--1.3订单id（使用stream实现消息队列时增加的）
local orderId =ARGV[3]
--2.数据key
--2.1库存key
local stockKey="seckill:stock:"..voucherId
--2.2订单key
local orderKey="seckill:order:"..voucherId

--3.脚本业务
--3.1判断库存充足
if(tonumber(redis.call('get',stockKey))<=0) then
    --库存不足，返回1
    return 1
end
--3.2判断用户订单
if(redis.call('sismember',orderKey, userId) == 1) then
    --存在，不能重复下单
    return 2
end
--3.3扣减库存
redis.call('incrby',stockKey,-1)
--3.4下单
redis.call('sadd',orderKey, userId)
--存入消息队列
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)
return 0

















