package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author CSJ
 * @version 1.0
 * @decription
 * @createTime 2023/1/12 星期四 18:06
 */
//WebMvcConfigurer配置类其实是Spring内部的一种配置方式
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 添加拦截器,postman下单优惠券时候需要把过滤器关掉
     * @param registry
     * //token刷新的拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // 第一层拦截器。拦截器主要用途：进行用户登录状态的拦截，日志的拦截等
        registry.addInterceptor(new LoginInterceptor())//里面是登录拦截器的实例化，属于第二层拦截
                .excludePathPatterns(//用于设置不需要拦截的过滤规则
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                ).order(1);
        // token刷新的拦截器，设置该拦截器执行顺序为第一个
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    }
}
