package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.ScrollResult;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PathVariable;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IFollowService followService;

    @Override
    public Result queryBlogById(Long id) {
        //1. 查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        //2.查询blog有关的用户
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        //3. 查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);


    }

    private void isBlogLiked(Blog blog) {
        //2. 判断当前用户是否点赞
        String key = "blog:like" + blog.getId();
        Double score= redisTemplate.opsForZSet().score(key, blog.getUserId().toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        //1. 获取登录用户
        Long userId = UserHolder.getUser().getId();

        //2. 判断当前用户是否点赞 zadd key value score
        String key = "blog:like" + id;
        Double score = redisTemplate.opsForZSet().score(key, userId.toString());
        //3. 如果为点赞可以点赞
        if (score == null){
            //3.1 数据库点赞数+1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //3.2 保存用户到Redis的set集合
            if (isSuccess){
                Boolean isMember = redisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else {

            //4. 如果已点赞，则取消点赞
            //4.1 数据库点赞数-1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //4.2 把用户从Redis的set集合移除
            if (isSuccess){
                redisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = "blog:like" + id;

        //1. 查询top5 zrange key 0 4
        Set<String> top5 = redisTemplate.opsForZSet().range(key, 0, 4);
        if (top5.isEmpty() || top5 == null){
            return Result.ok(Collections.emptyList());
        }
        //2. 解析用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //id拼成字符串
        String idStr = StrUtil.join(",", ids);
        //3. 根据用户id查询用户
        List<UserDTO> list = userService.query()
                .in("id",ids)
                .last("order by field(id, "+idStr +")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        //4.返回
        return Result.ok(list);
    }


    public Result saveBlog(Blog blog){
        //1. 查询当前用户
        Long id = UserHolder.getUser().getId();
        //2. 保存
        blog.setUserId(id);
        boolean isSuccess = save(blog);
        if (!isSuccess){
            return Result.fail("保存失败");
        }
        //3. 查询当前用户的所有粉丝
        List<Follow> follow_user = followService.query().eq("follow_user_id", id).list();

        for (Follow follow : follow_user) {
            Long userId = follow.getUserId();
            System.out.println("粉丝ID" + userId);
            String key = "feed:"+userId;
            //时间戳为score
            redisTemplate.opsForZSet().add(key, blog.getId().toString(),System.currentTimeMillis());
        }
        System.out.println("已推送所有粉丝");
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogFollow(Long max, Integer offset) {
        //1. 获取当前用户
        Long id = UserHolder.getUser().getId();
        System.out.println("当前用户为==>" + id);
        //2.获取收件箱  ZREVRANGEBYSCORE key max min WITHSCORES LIMIT offset count
        String key = "feed:"+id;
        Set<ZSetOperations.TypedTuple<String>> typedTuples
                = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 3);
        if (typedTuples.isEmpty() || typedTuples == null){
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        //3.解析
        long minValue = 0; // 上次一的最小值
        int os = 1; // 偏移量
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String value = typedTuple.getValue();
            Long blogId = Long.valueOf(value);
            System.out.println("获取到的关注的人博客ID==>" + blogId);
            ids.add(blogId);
            long min = typedTuple.getScore().longValue();
            if (min == minValue){
                os++;
            }else {
                minValue = min;
                os = 1;
            }
        }
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogList = query().in("id", ids)
                .last("order by field(id, " + idStr + ")")
                .list();
        for (Blog blog : blogList) {
            isBlogLiked(blog);
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogList);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minValue);

        return Result.ok(scrollResult);
    }
}
