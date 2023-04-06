package com.hmdp.utils;/**
 * @author Ansel Zhong
 * coding time
 */

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;

/**
 @title hm-dianping
 @author Ansel Zhong
 @Date 2023/3/22
 @Description
 */

@Component
public class RedisWorker {

  private static final long BEGIN_TIMESTAMP = 1640995200;
  private static final int COUNT_BIT = 32;

  @Autowired
  private StringRedisTemplate redisTemplate;


  public long nextId(String keyPrefix) {
      //1. 生成时间戳
    LocalDateTime now = LocalDateTime.now();
    long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
    long timestamp = nowSecond - BEGIN_TIMESTAMP;
    //2 生成序列号.
    String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    Long count = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

    //3. 拼接并返回
    return timestamp << 32 | count;
  }

  public static void main(String[] args) {
//    LocalDateTime time = LocalDateTime.of(2023, 1, 1, 0, 0, 0);
//    long second = time.toEpochSecond(ZoneOffset.UTC);
//    System.out.println(second);


  }
}
