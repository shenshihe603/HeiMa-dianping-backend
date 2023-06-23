package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j//解决log.debug的报错
//ServiceImpl<UserMapper, User>这一块用的是MP
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.不符合,返回错误信息
            return Result.fail("手机格式错误！");

        }

        //3.符合，生成验证码，利用hutool的工具
        String code = "123456";//RandomUtil.randomNumbers(6);

        //4.保存到session
//        session.setAttribute("code",code);
        //todo 4改 短信验证码保存到redis，避免redis内存占用，添加验证码有效期
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.发送验证码（需要调用第三方，先写个假的）
        log.debug("发送验证码成功,验证码：{}",code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号码
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //不符合,返回错误信息
            return Result.fail("手机格式错误！");
        }
        //2.检验验证码
//        Object cacheCode = session.getAttribute("code");
        //todo 2.改.从redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();

        if (cacheCode == null || !cacheCode.equals(code)) {//不要忘了加！
            //不符合,返回错误信息
            return Result.fail("验证码错误！");
        }
        //3.验证码通过，查询用户
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user=createUserWithPhone(phone);
        }
        //4.保存用户到session
//        session.setAttribute("user",user);
//        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));//将user变成userDTO，只给部分用户信息

        //todo 4改，保存用户信息到redis
        //todo 4.1随机生成Token作为登录令牌
        //UUID是Universally Unique Identifier的缩写，它是在一定的范围内（从特定的名字空间到全球）唯一的机器生成的标识符

        String token = UUID.randomUUID().toString();
        //4.2将user对象转为HashMap对象存储
        //用户信息脱敏
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //用到了HuTool的BeanUtil.beanToMap()方法
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> {
                            if (fieldValue == null){
                                fieldValue = "0";
                            }else {
                                fieldValue = fieldValue.toString();
                            }
                            return fieldValue;

                        }



                ));

        //4.3存储到redis
        String tokenkey = LOGIN_USER_KEY + token;//redis的Key就是"login:code:"+随机的token
        stringRedisTemplate.opsForHash().putAll(tokenkey,userMap);
        //4.4设置token的有效期
        stringRedisTemplate.expire(tokenkey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //返回ok
        //改！！，返回参数里面加入token
        //思考：session有sessionId所以不需要登录凭证
        return Result.ok(token);
    }

//    public void logout(HttpServletRequest request) {
//        String token = request.getHeader("authorization");
//        String tokenkey = LOGIN_USER_KEY + token;//redis的Key就是"login:code:"+随机的token
//        stringRedisTemplate.delete(tokenkey);
//    }

    private User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //2.保存用户
        save(user);
        return user;
    }
}
