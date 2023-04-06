package com.hmdp.config;/**
 * @author Ansel Zhong
 * coding time
 */

import com.hmdp.interceptor.LoginInterceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 @title hm-dianping
 @author Ansel Zhong
 @Date 2023/3/20
 @Description
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

  @Autowired
  StringRedisTemplate redisTemplate;
  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    //拦截器按照添加顺序执行
    registry.addInterceptor(new LoginInterceptor(redisTemplate)).
            excludePathPatterns("/user/code",
                    "/user/login",
                    "/blog/hot",
                    "/shop/**",
                    "/shop-type/**",
                    "/upload/**",
                    "/voucher/**"
                    ).order(1);

    WebMvcConfigurer.super.addInterceptors(registry);
  }
}
