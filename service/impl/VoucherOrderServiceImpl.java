package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private Redisson redisson;



    @Autowired
    private RedisWorker redisWorker;
    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        RLock lock = redisson.getLock("seckill");

        lock.lock(30, TimeUnit.SECONDS);

        System.out.println("voucherId ---> " + voucherId);
        //1. 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        System.out.println("voucher 信息 -->" + voucher);
        //2. 判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        //3. 判断秒杀是否结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始");
        }
        //4. 判断秒杀是否充足
        if (voucher.getStock() < 1) {
            System.out.println("库存不足..");
            return Result.fail("库存不足..");
        }
        //5. 扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId)
                .update();
        if (!success) {
            return Result.fail("库存不足!");
        }

        System.out.println("对象--->" + UserHolder.getUser());
        Long id = UserHolder.getUser().getId();
        synchronized (id.toString().intern()){
            //用代理调用
            IVoucherOrderService proxy =(IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVouchOrder(voucherId,lock);
        }


    }

    @Transactional
    public Result createVouchOrder(Long voucherId,RLock lock){
        //6. 创建订单
        // 实现一人一单
            Long id = UserHolder.getUser().getId();
            Integer count = query().eq("user_id", id).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("已经买过了！一人一单！");
            }

            VoucherOrder voucherOrder = new VoucherOrder();
            long order = redisWorker.nextId("order");
            voucherOrder.setId(order);
            voucherOrder.setUserId(UserHolder.getUser().getId());
            // voucherOrder.setUserId(2023L);
            voucherOrder.setVoucherId(voucherId);
            save(voucherOrder);
            QueryChainWrapper<VoucherOrder> one = query().eq("id", order);
            System.out.println("voucherOrder -->" + one);

            lock.unlock();
            return Result.ok(order);

    }

}
