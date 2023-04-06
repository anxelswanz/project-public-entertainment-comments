package com.hmdp.entity;/**
 * @author Ansel Zhong
 * coding time
 */

import lombok.Data;

import java.util.List;

/**
 @title hm-dianping
 @author Ansel Zhong
 @Date 2023/3/31
 @Description
 */
@Data
public class ScrollResult {
  private List<?> list;
  private Long minTime;
  private Integer offset;
}
