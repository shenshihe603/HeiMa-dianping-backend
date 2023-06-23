package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @author CSJ
 * @version 1.0
 * @decription
 * @createTime 2023/2/20 星期一 21:45
 */
@Component
public class RedisIdWorker {
    /**
     * 定义初始时间戳，用来计算id增长差值
     */
    private static final Long START_STAMP=1676931420L;
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;
    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     *
     * @param keyPrefix redis业务前缀，区分不同业务
     * @return 全局唯一Id=序列号（0)+时间戳（31）+序列号（32）
     */
    public long nextId(String keyPrefix) {
        //1.获取时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - START_STAMP;
        //2.获取序列号
        //2.1获取日期，生成按日的key
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix+":" + date);
        //3.返回全局唯一Id
        return timeStamp << COUNT_BITS | increment;
    }

    public static void main(String[] args) {
        //纪元时间
        LocalDateTime of = LocalDateTime.of(2023, 2, 20, 22, 17);
        //时间戳是指格林威治时间1970年01月01日00时00分00秒(北京时间1970年01月01日08时00分00秒)起至现在的总秒数。
        //时间戳转化为Date或LocalDateTime时，需要添加ZoneId（地区）或ZoneOffset（偏移数据）来转为本地时间。
        long second = of.toEpochSecond(ZoneOffset.UTC);
        System.out.println("second:"+second);
    }
}
