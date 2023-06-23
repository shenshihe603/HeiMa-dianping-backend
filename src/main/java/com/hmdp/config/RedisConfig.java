package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author CSJ
 * @version 1.0
 * @decription
 * @createTime 2023/3/22 星期三 17:10
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedissonClient redissonClient(){
        //配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://47.113.227.2:6379").setPassword("lcredis");
        //创建RedissonClient对象
        return Redisson.create(config);
    }

}
