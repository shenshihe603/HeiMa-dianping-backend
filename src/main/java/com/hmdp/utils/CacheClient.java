package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;


/**
 * @author CSJ
 * @version 1.0
 * @decription 缓存工具封装
 * @createTime 2023/2/7 星期二 20:00
 */

@Slf4j//日志
@Component//标注Spring管理的Bean，使用@Component注解在一个类上，表示将此类标记为Spring容器中的一个Bean。
public class CacheClient {

    //    @Resource主要做依赖注入的，从容器中自动获取bean
    private final StringRedisTemplate stringRedisTemplate;
    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * //方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
     *
     * @param key   完整redis的key
     * @param value 存入redis的值
     * @param time  TTL过期时间
     * @param unit  TTL过期时间的时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }


    /**
     * //    方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
     * //这个逻辑过期时间，是第一次设置
     *
     * @param key   完整redis的key
     * @param value 存入redis的值
     * @param time  TTL过期时间
     * @param unit  TTL过期时间的时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //封装带逻辑过期时间的数据类
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //转化json并存入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * //方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     *
     * @param keyPrefix  redis前缀
     * @param id         查询id
     * @param type       类的类型
     * @param dbFallback 函数式编程
     * @param time       TTL过期时间
     * @param unit       TTL过期时间的时间单位
     * @param <R>        返回值
     * @param <ID>       ID类型
     * @return 返回R
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //添加商铺缓存
        //1.从redis获取商铺缓存
        //构造key
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断字符串是否为非空白
        if (StrUtil.isNotBlank(json)) {
            //存在、直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断命中的是否是空值
        if (json != null) {
            // 返回一个错误信息
            return null;
        }

        //4.不存在，查询数据库
        R r = dbFallback.apply(id);
        //5.数据库也不存在
        if (r == null) {
            //解决缓存穿透问题，缓存一个空集合
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
//            return Result.ok("店铺不存在！");
            return null;
        }
        //6.数据库存在，写入redis
        //写入时添加超时剔除缓存功能
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        this.set(key, r, time, unit);
        return r;
    }


    //    方法4：根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
//封装 利用逻辑过期解决缓存击穿的代码
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //添加商铺缓存
        //1.从redis获取商铺缓存
        //构造key
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断字符串是否命中
        if (StrUtil.isBlank(json)) {
            //未命中，为空白、直接返回空
            return null;
        }
        //3命中。把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //取出数据
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //取出过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //3.1 判断逻辑过期时间
//        if (redisData.getExpireTime() > (LocalDateTime.now())) {

//        }
        if (expireTime.isAfter(LocalDateTime.now())) {
            //3.2未过期。返回信息
            return r;
        }

        //3.2已过期，尝试获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //3.2.1未获取互斥锁，返回商铺信息
//        if(!isLock) return shop;
        //3.2.2获取锁，开启独立线程
        if (isLock) {
            //开启独立线程,实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {

                try {
                    // 查询数据库
                    R newR = dbFallback.apply(id);
                    // 重建缓存
                    this.setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new RuntimeException(e);
                } finally {
                    unLock(lockKey);
                }
            });
        }
        //7.未获取互斥锁，返回商铺信息
//        return Result.ok(shop);
        return r;
    }

    //封装，互斥锁解决缓存击穿
    /*public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1.从redis查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            // 3.存在，直接返回
            return JSONUtil.toBean(shopJson, type);
        }
        // 判断命中的是否是空值
        if (shopJson != null) {
            // 返回一个错误信息
            return null;
        }

        // 4.实现缓存重建
        // 4.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean isLock = tryLock(lockKey);
            // 4.2.判断是否获取成功
            if (!isLock) {
                // 4.3.获取锁失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            // 4.4.获取锁成功，根据id查询数据库
            r = dbFallback.apply(id);
            // 5.不存在，返回错误
            if (r == null) {
                // 将空值写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                // 返回错误信息
                return null;
            }
            // 6.存在，写入redis
            this.set(key, r, time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 7.释放锁
            unlock(lockKey);
        }
        // 8.返回
        return r;
    }*/

    //获取互斥锁
    private boolean tryLock(String key) {
        //设置互斥锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        //注意不能直接返回，会发生自动拆箱导致为空，所以要用工具
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
