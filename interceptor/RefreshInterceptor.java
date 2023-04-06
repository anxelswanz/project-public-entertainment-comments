package com.hmdp.interceptor;/**
 * @author Ansel Zhong
 * coding time
 */

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 @title hm-dianping
 @author Ansel Zhong
 @Date 2023/3/21
 @Description
 */
public class RefreshInterceptor implements HandlerInterceptor {

  StringRedisTemplate redisTemplate;

  public RefreshInterceptor(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    //1. 判断是否需要拦截
    if (UserHolder.getUser() == null) {
      response.setStatus(401);
      return false;
    }
    //1. 获取session
    HttpSession session = request.getSession();

    String token = request.getHeader("authorization");
    System.out.println("token = " + token);
    String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
    System.out.println("tokenKey -->" + tokenKey);
    Map<Object, Object> userMap = redisTemplate.opsForHash().entries(tokenKey);
    System.out.println("usermap == >" + userMap);
    UserDTO userDTO1 = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
    //设置过期时间
    //   String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
    System.out.println("tokenKey --> " + tokenKey);
    redisTemplate.expire(tokenKey,30L, TimeUnit.SECONDS);
    //有用户就放行
    return true;
  }

}
