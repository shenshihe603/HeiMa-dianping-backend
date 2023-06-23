package com.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

//用于逻辑过期，添加数据对象，增加逻辑过期时间
@Data
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
