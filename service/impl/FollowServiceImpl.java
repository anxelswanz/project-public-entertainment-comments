package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    RedisTemplate redisTemplate;


    @Autowired
    IUserService userService;
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long id = UserHolder.getUser().getId();
        System.out.println("id=" + id);
        String key = "follow:" + id;
        //1. 判断关注还是取关
        if (isFollow){
            //2.新增
            Follow follow = new Follow();
            follow.setUserId(id);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);

            if (isSuccess) {
                redisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }else {
            //3.删除
            remove(new QueryWrapper<Follow>().eq("user_id",id).eq("follow_user_id",followUserId));
            redisTemplate.opsForSet().remove(key, followUserId);
        }


        return Result.ok();
    }

    @Override
    public Result isFollow(Long followId) {
        Long id = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", id).eq("follow_user_id", followId).count();
        return Result.ok(count > 0 );
    }

    @Override
    public Result commonFollows(Long id) {
        //1. 查询当前用户
        Long userId = UserHolder.getUser().getId();

        String key1 = "follow:"+userId;
        System.out.println("key1=" + key1);
        String key2 = "follow:"+id;
        System.out.println("key2=" +key2);
        Set<String> intersect = redisTemplate.opsForSet().intersect(key1, key2);
        System.out.println("intersect = " +intersect);
        System.out.println(intersect);
        List<Long> ids
                = intersect.stream().map(Long::valueOf).collect(Collectors.toList());

        System.out.println("ids=" + ids);
        List<User> users
                = userService.listByIds(ids);

        return Result.ok(users);
    }
}
