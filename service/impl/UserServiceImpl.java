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
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2. 如果不符合返回错误信息
             return Result.fail("手机号格式错误");
        }

        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4. 保存验证码到session
       // session.setAttribute("code",code);
           //update : 保存验证码到redis
        redisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY +phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        //5.发送验证码
        log.debug("发送短信验证码成功, 验证码{}" , code);
        //返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginFormDTO, HttpSession session) {
        //1. 校验手机号
        String phone = loginFormDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //2. 校验验证码
        // Object cacheCode = session.getAttribute("code");
        String cacheCode = redisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + loginFormDTO.getPhone());
        String code = loginFormDTO.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(loginFormDTO.getCode())) {
            //3. 不一致 报错
            return Result.fail("验证码错误");
        }
        //4. 一致 根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5. 判断用户是否存在
        if (user == null) {
            //6. 不存在，创建新用户
           user =(User) createUserWithPhone(phone);
        }
        //6. 不存在，创建新用户并保存
        session.setAttribute("user",user);
        //7. 保存用户信息到redis
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> usermap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .ignoreNullValue().setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        System.out.println("usermap -- >" + usermap);
        String tokenKey= RedisConstants.LOGIN_USER_KEY + token;
        System.out.println("tokenkey -- >" + tokenKey);
        redisTemplate.opsForHash().putAll(tokenKey, usermap);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(tokenKey);
        System.out.println("entriess -- >" + entries);

        UserHolder.saveUser(userDTO);
        System.out.println(UserHolder.getUser());
        //设置过期时间
        redisTemplate.expire(tokenKey,30L,TimeUnit.SECONDS);


        return Result.ok(token);
    }

    @Override
    public Result sign() {
        //1. 获取当前用户
        Long id = UserHolder.getUser().getId();

        //2.获取日期
        LocalDateTime now = LocalDateTime.now();
        String keypreffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        //3.拼接key
        String key = RedisConstants.USER_SIGN_KEY + id + keypreffix;
        //4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        // - 1 因为 bit存储是从0开始
        //true 代表1
        redisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return null;
    }

    public Result signCount(){
        //1. 获取用户
        Long id = UserHolder.getUser().getId();
        //2. 获取时间
        LocalDateTime now = LocalDateTime.now();
        String keypreffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = RedisConstants.USER_SIGN_KEY + id + keypreffix;
        int dayofMonth = now.getDayOfMonth();

        List<Long> results = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().
                get(BitFieldSubCommands.BitFieldType.unsigned(dayofMonth)).valueAt(0));
        Long num = results.get(0);

        if (num == null || num == 0 ){
            return Result.ok(0);
        }

        int count = 0;
        while (true){
            //让这个数组与1作与运算，得到数字的最后一个bit位
            //判断这个bit是否为0
            //如果为0，说明未签到结束
            if ((num & 1) == 0) {
                break;
            }else{
                //如果为1，说明已经签到，计数器+1
                count++;
            }
            //把数字右移一位，抛弃最后一个bit位，继续下一位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    private Object createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //2. 保存用户
        save(user);
        return user;
    }
}
