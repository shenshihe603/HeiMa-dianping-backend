package com.hmdp.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author CSJ
 * @version 1.0
 * @decription
 * @createTime 2023/3/3 星期五 16:23
 */
public class SimpleRedisLock implements ILock{
    private final String name;
    private final StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private final String KEY_PREFIX = "lock:";
    private final String ID_PREFIX = UUID.randomUUID().toString();
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
//        //获取线程id
//        long threadID = Thread.currentThread().getId();
        //获取线程组合标识
        String threadID = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX+name, threadID, timeoutSec, TimeUnit.SECONDS);
        //避免Boolean和boolean之间拆箱导致的空指针问题
            //因为Boolean类型是一个类，所以Boolean类型对象的默认值是null
        return Boolean.TRUE.equals(success);
    }

    /**
     * 使用lua脚本删除锁，解决原子性误删问题
     */
    @Override
    public void unlock() {
        //使用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX+name),
                ID_PREFIX + Thread.currentThread().getId()
        );
    }
//    @Override
//    public void unlock() {
//        //获取线程组合标识
//        String threadID = ID_PREFIX + Thread.currentThread().getId();
//        //获取锁标识
//        String id = stringRedisTemplate.opsForValue()
//                .get(KEY_PREFIX + name);
//        if (threadID.equals(id)) {
//            //释放锁
//            stringRedisTemplate.delete(KEY_PREFIX + name);
//        }
//
//    }
}
