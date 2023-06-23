package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        //添加商铺列表缓存
        //1.从redis获取商铺缓存
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;//构造key
        List<String> shopTypeJsons = stringRedisTemplate.opsForList().range(key, 0, -1);
        //2.判断是否命中
        if (!CollectionUtils.isEmpty(shopTypeJsons)) {
            //存在、直接返回
            //list转stream处理成bean对象然后转回list
            List<ShopType> shopTypeList = shopTypeJsons.stream()
                    .map(item -> JSONUtil.toBean(item, ShopType.class))//处理成bean对象
                    .sorted(Comparator.comparing(ShopType::getSort))//返回stream
                    .collect(Collectors.toList());//返回stream转为返回list
            return Result.ok(shopTypeList);
        }
        //4.不存在，查询数据库
//        List<ShopType> typeList = query().orderByAsc("sort").list();
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        //5.数据库也不存在
        if (CollectionUtils.isEmpty(shopTypes)) {
            ////解决缓存穿透问题，缓存一个空集合
            stringRedisTemplate.opsForValue()
                    .set(key, Collections.emptyList().toString(),RedisConstants.CACHE_NULL_TTL);
            return Result.fail("店铺列表不存在！");
        }
        //6.数据库存在，写入redis
        //把查出来的list数据List<ShopType>变成List<String>数据，然后存入
        List<String> stringTypeCache = shopTypes.stream()
                .sorted(Comparator.comparing(ShopType::getSort))
                .map(JSONUtil::toJsonStr)//注意这里不是(item -> item.toString())，而是
                .collect(Collectors.toList());
        stringRedisTemplate.opsForList().rightPushAll(key, stringTypeCache);
        stringRedisTemplate.expire(key, RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        //7.返回
        return Result.ok(shopTypes);
    }
}
