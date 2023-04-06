package com.hmdp.interceptor;/**
 * @author Ansel Zhong
 * coding time
 */

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
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
 @Date 2023/3/20
 @Description
 */
public class LoginInterceptor implements HandlerInterceptor {

  StringRedisTemplate redisTemplate;

  public LoginInterceptor(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    //1. 获取session
    HttpSession session = request.getSession();
    Cookie[] cookies = request.getCookies();
    for (Cookie cookie : cookies) {
      String value = cookie.getValue();
      System.out.println("value = " + value);
    }

    String token = request.getHeader("authorization");
    System.out.println("token = " + token);
    String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
    System.out.println("tokenKey -->" + tokenKey);
    Map<Object, Object> userMap = redisTemplate.opsForHash().entries(tokenKey);
    System.out.println("usermap == >" + userMap);
    if (userMap.isEmpty()) {
       response.setStatus(401);
       return false;
    }
    UserDTO userDTO1 = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
    //设置过期时间
 //   String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
    System.out.println("tokenKey --> " + tokenKey);
    redisTemplate.expire(tokenKey,30L, TimeUnit.SECONDS);


    //2. 获取用户
    User user = (User) session.getAttribute("user");
    UserDTO userDTO = new UserDTO();
    BeanUtils.copyProperties(user,userDTO);
    //3. 判断用户是否存在
    if (userDTO1 == null) {
      //4. 不存在，拦截 返回401状态码
      response.setStatus(401);
      return false;
    }
    //5. 存在，保护用户信息到ThreadLocal
    UserHolder.saveUser(userDTO1);
    System.out.println(UserHolder.getUser());
    System.out.println("保存userDTO1成功");
    //6. 放行
    return HandlerInterceptor.super.preHandle(request, response, handler);
  }

  @Override
  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
    HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    //移除用户
    UserHolder.removeUser();
    HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
  }
}

