//package com.hmdp.config;/**
// * @author Ansel Zhong
// * coding time
// */
//
//import org.redisson.Redisson;
//import org.redisson.config.Config;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
///**
// @title hm-dianping
// @author Ansel Zhong
// @Date 2023/3/29
// @Description
// */
//@Configuration
//public class RedissonConfig {
//
//
//  @Bean
//  public Redisson redisson(){
//    Config config = new Config();
//    config.useSingleServer().setAddress("redis://127.0.0.1").setDatabase(0);
//    return (Redisson)Redisson.create(config);
//  }
//}
