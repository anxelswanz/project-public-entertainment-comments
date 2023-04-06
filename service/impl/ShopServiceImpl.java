package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {



    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryById(Long id) {

//        new CacheClient(redisTemplate).queryWithPassThrough
//                (1,1, Shop.class, this::getById);
        String lockKey = null ;
         Shop shop = null;
        try{
            String key = RedisConstants.CACHE_SHOP_KEY + id;
            //1. 现在redis 查
            String shopJson = redisTemplate.opsForValue().get(key);
            if (StringUtils.isNotBlank(shopJson)) {
                shop = JSONUtil.toBean(shopJson, Shop.class);
                return Result.ok(shop);
            }
            //命中是否是空值
            if (shopJson != "") {
                // 返回错误信息
                return Result.fail("店铺不存在");
            }
            lockKey = "lock:shop:" + id;
            Boolean isTrue = tryLock(lockKey);
            if (!isTrue) {
                Thread.sleep(50);
                return queryById(id);
            }

            //没有就查数据库
            shop = getById(id);
            if (shop == null) {
                return Result.fail("店铺不存在");
            }
            redisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            //存在就存在redis
            shopJson = JSONUtil.toJsonStr(shop);
            redisTemplate.opsForValue().set(key,shopJson);
        }catch (Exception e) {

        }finally {
            unlock(lockKey);
            return Result.ok(shop);
        }


    }

    private Boolean tryLock(String key) {
        Boolean isLock = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(isLock);
    }

    private void unlock(String key) {
          redisTemplate.delete(key);
    }


    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y){
        //1. 判断是否需要根据坐标查询
        if (x == null || y == null) {
            Page<Shop> page = query().eq("type_id", typeId)
                    .page(new Page<>(current, 10));
            return Result.ok(page.getRecords());
        }
        //2.计算分页参数
        int from = (current - 1) * 10;
        int to = current * 10;
        //3.查询redis，按照距离排序、排序
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        //4.解析id
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate
                .opsForGeo()
                .search(key, GeoReference.fromCoordinate(x, y)
                        , new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(to));

        //5.根据id查询shop
        if (results == null){
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        List<Long> ids = new ArrayList<>();
        Map<Long, Distance> map = new HashMap<>();
        //StringBuilder stringBuilder = new StringBuilder();
        list.stream().skip(from)
                .forEach(result -> {
                    String idStr = result.getContent().getName();
                    System.out.println("idStr = " + idStr);
                    String s = idStr.replaceAll("\"", "");
                    Long idLong = Long.valueOf(s);
                    ids.add(idLong);
                    Distance distance = result.getDistance();
                    System.out.println("distance = " + distance.getValue());
                    map.put(idLong,distance);
                });
        String join = StrUtil.join(",", ids);
        System.out.println("ids = " + ids);
        List<Shop> shops = query().in("id", ids)
                .last("order by field(id," + join + ")").list();

        for (Shop shop : shops) {
            Long id = shop.getId();
            Distance distance = map.get(id);
            double value = distance.getValue();
            shop.setDistance(value);
        }

        //6.返回
        return Result.ok(shops);
    }
}
