package com.hmdp.service.impl;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.Serializable;
import java.sql.Time;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

/*    //线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);*/

    /**
     * 根据id修改店铺时，先修改数据库，再删除缓存
     *
     * @param shop
     * @return
     */
    @Override
    public Result updateShop(Shop shop) {

        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        //1.先修改数据库
        updateById(shop);
        //2.再删除缓存
        //构造key
        String key = CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);
        return Result.ok();
    }

    @Override
    public Result queryById(Long id) {
        //封装缓存穿透的代码
//        Shop shop = queryWithPassThrough(id);
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

        //封装，互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);
//         Shop shop = cacheClient
//                 .queryWithMutex(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //封装、逻辑过期解决缓存击穿
        //测试前先添加一份数据
//        try {
//            savaShop2Redis(1L,5L);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//        return Result.ok(null);
        //然后在测试queryWithLogicalExpire
//        Shop shop = queryWithLogicalExpire(id);
//        封装工具后，调用工具
         Shop shop = cacheClient
                 .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        //7.返回
        return Result.ok(shop);
    }
    //封装 利用逻辑过期解决缓存击穿的代码
    /*public Shop queryWithLogicalExpire(Long id) {
        //添加商铺缓存
        //1.从redis获取商铺缓存
        //构造key
        String key = CACHE_SHOP_KEY + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断字符串是否命中
        if (StrUtil.isBlank(json)) {
            //未命中，为空白、直接返回空
            return null;
        }
        //3命中。把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        //取出数据
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        //取出过期时间
        LocalDateTime expireTime = redisData.getExpireTime();
        //3.1 判断逻辑过期时间
//        if (redisData.getExpireTime() > (LocalDateTime.now())) {

//        }
        if (expireTime.isAfter(LocalDateTime.now())) {
            //3.2未过期。返回信息
            return shop;
        }

        //3.2已过期，尝试获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //3.2.1未获取互斥锁，返回商铺信息
//        if(!isLock) return shop;
        //3.2.2获取锁，开启独立线程
        if (isLock) {
            //开启独立线程,实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{

                try {
                    //缓存重建
                    this.savaShop2Redis(id,5L);//20秒测试一下
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
        return shop;
    }
*/

    //封装，互斥锁解决缓存击穿
    /*public Shop queryWithMutex(Long id) {
        //添加商铺缓存
        //1.从redis获取商铺缓存
        //构造key
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断字符串是否为非空白
        if (StrUtil.isNotBlank(shopJson)) {
            //存在、直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return Result.ok(shop);
            return shop;
        }
        //判断命中的是否为空值
        if (shopJson != null) {
            //返回错误信息
            return null;
        }
        //todo 未命中。尝试获取互斥锁,实现缓存重建
        //3.1获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean islock = tryLock(lockKey);
            //3.2判断是否获取成功
            if (!islock) {
                //3.3失败，想休眠然后重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //3.4获取成功，查数据库
            shop = getById(id);
            //模拟重建延时
            Thread.sleep(200);
            //5.数据库也不存在
            if (shop == null) {
                //解决缓存穿透问题，缓存一个空集合
                stringRedisTemplate.opsForValue().set(key, Collections.emptyList().toString(), RedisConstants.CACHE_NULL_TTL);
    //            return Result.ok("店铺不存在！");
                return null;
            }
            //6.数据库存在，写入redis
            //写入时添加超时剔除缓存功能
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
            stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //todo 释放互斥锁
            unLock(lockKey);
        }


        //7.返回
//        return Result.ok(shop);
        return shop;
    }*/
    //封装存储店铺信息和逻辑过期时间

    //封装缓存穿透的代码,并把返回Result改返回shop
    /*public Shop queryWithPassThrough(Long id) {
        //添加商铺缓存
        //1.从redis获取商铺缓存
        //构造key
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断字符串是否为非空白
        if (StrUtil.isNotBlank(shopJson)) {
            //存在、直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return Result.ok(shop);
            return shop;
        }

        //4.不存在，查询数据库
        Shop shop = getById(id);
        //5.数据库也不存在
        if (shop == null) {
            //解决缓存穿透问题，缓存一个空集合
            stringRedisTemplate.opsForValue().set(key, Collections.emptyList().toString(), RedisConstants.CACHE_NULL_TTL);
//            return Result.ok("店铺不存在！");
            return null;
        }
        //6.数据库存在，写入redis
        //写入时添加超时剔除缓存功能
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //7.返回
//        return Result.ok(shop);
        return shop;
    }*/

 /*   //获取互斥锁
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
*/
    public void savaShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));
    }
}
