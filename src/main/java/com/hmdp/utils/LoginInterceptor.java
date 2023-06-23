package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @author CSJ
 * @version 1.0
 * @decription
 * @createTime 2023/1/12 星期四 17:48
 */
//这里是mvc拦截器，因为实现了HandlerInterceptor
    //由于该类是自己写的，不是spring管控的，所以不能用依赖注入的方式，需要用构造器
public class LoginInterceptor implements HandlerInterceptor {

/* 优化登录拦截器以后，不需要这些了
    private StringRedisTemplate stringRedisTemplate;

    //类的实例化在MvcConfig里面，所以要给MvcConfig里面的实例化加参数new LoginInterceptor(stringRedisTemplate)
    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }*/

    //控制器方法前执行,校验登录状态
    //如果用户一直在页面操作，则每次执行请求钱都会被拦截为了给token有效期刷新
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /*优化前的代码，只有一个拦截器时

        //1.获取session
        //todo 改1.获取Token,这里是前端设置的
//        HttpSession session = request.getSession();
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)){
            //token不存在，拦截，返回401状态码
            response.setStatus(401);
            return false;
        }
        //2.获取session中的用户
        //todo 改2.从redis获取用户
//        Object user = session.getAttribute("user");
        Map<Object, Object> user = stringRedisTemplate.opsForHash()
                .entries(LOGIN_USER_KEY + token);
        //3.判断用户是否存在
        if(user.isEmpty()){
            //4.不存在，拦截，返回401状态码
            response.setStatus(401);
            return false;
        }
        //todo 5.用户存在，保存用户信息到Threadlocal
        //5.1 map用户信息转化为UserDTO信息
        UserDTO userDTO = BeanUtil.fillBeanWithMap(user, new UserDTO(), true);
        //5.2保存用户信息到Threadlocal
//        UserHolder.saveUser((UserDTO) user);
        UserHolder.saveUser(userDTO);
        //todo 刷新有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //6.放行
        return true;//true代表继续下面操作，否则中断请求*/

        //todo 登录拦截器优化后，不管redis，只从TreadLocal中判断是否存在用户信息
        // 1.判断是否需要拦截（ThreadLocal中是否有用户）
        if (UserHolder.getUser() == null) {
            // 没有，需要拦截，设置状态码
            response.setStatus(401);
            // 拦截
            return false;
        }
        // 有用户，则放行
        return true;
    }
/*优化登录拦截器以后，不需要这些了
    //控制器方法后（即视图渲染结束以后）执行，常用于资源清理、记录日志
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }*/
}
