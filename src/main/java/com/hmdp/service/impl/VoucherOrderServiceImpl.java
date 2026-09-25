package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    //阻塞队列
    /*private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);*/
    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct//当前类初始化完毕就执行
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    //线程任务
    private class VoucherOrderHandler implements Runnable{

        @Override
        public void run() {
           while (true){
               //1.获取消息队列中的订单消息
               List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                       Consumer.from("g1", "c1"),
                       StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                       StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
               );
               //2.判断订单信息是否为空
                if (list == null || list.isEmpty()){
                    //若为null,说明没有消息，继续下一次循环
                    continue;
                }
               //3.解析数据,创建订单
               MapRecord<String, Object, Object> record = list.get(0);
               Map<Object, Object> value = record.getValue();
               VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
               createVoucherOrder(voucherOrder);
               //4.确认信息 XACK
               try {
                   stringRedisTemplate.opsForStream().acknowledge("s1","g1",record.getId());
               } catch (Exception e) {
                   log.error("处理订单异常",e);
                   //处理异常信息
                   handlePendingList();
               }

           }
        }


        private void handlePendingList(){
            while (true){
                //1.获取pending-list中的订单信息
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create("stream.orders", ReadOffset.from("0"))
                );
                //2.判断订单信息是否为空
                if (list == null || list.isEmpty()){
                    //若为Null，说明没有异常信息，结束循环
                    break;
                }
                //解析数据
                MapRecord<String, Object, Object> record = list.get(0);
                Map<Object, Object> value = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                //3.创建订单
                createVoucherOrder(voucherOrder);
                //4.确认信息XACK
                try {
                    stringRedisTemplate.opsForStream().acknowledge("s1","g1",record.getId());
                } catch (Exception e) {
                    log.error("处理pendding订单异常",e);

                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    public IVoucherOrderService proxy ;
    private void handleVoucherOrder(VoucherOrder voucherOrder){
        //1.获取用户
        Long userId = voucherOrder.getUserId();
        //2.创建锁对象
        RLock redisLock = redissonClient.getLock("lock:order:" + userId);
        //3.尝试获取锁
        boolean isLock = redisLock.tryLock();
        //4.判断获取锁是否成功
        if (!isLock){
            //获取锁失败 直接返回失败或重试
            log.error("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            redisLock.unlock();
        }


    }


    /**
     * 秒杀卷下单
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        //1.执行Lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        //2.判断结果是否为0
        int r = result.intValue();
        //2.1不为0 代表没有购买资格
        if (r != 0){
            return Result.fail(r==1?"库存不足":"不能重复下单");
        }

        //获取代理对象
        proxy = (IVoucherOrderService)AopContext.currentProxy();
        //3.返回订单id
        return Result.ok(orderId);

    }
   /* @Override
    public Result seckillVoucher(Long voucherId) {

        //1.根据优惠券id查询优惠卷
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2.根据获取到的优惠券信息判断秒杀是否开始
        if (LocalDateTime.now().isBefore(seckillVoucher.getBeginTime())){
            //2.1 秒杀未开始  返回错误
            return Result.fail("秒杀未开始!");
        }
        if (LocalDateTime.now().isAfter(seckillVoucher.getEndTime())){
            //2.2秒杀已结束  返回错误
            return Result.fail("秒杀已结束!");
        }
        //4.开始   判断秒杀卷库存是否充足
        if (seckillVoucher.getStock()<1){
            return Result.fail("库存不足!");
        }
        Long userId = UserHolder.getUser().getId();
        //创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        //获取锁
        boolean isLock = lock.tryLock(1200);
        if (!isLock){
            //加锁失败
            return Result.fail("不允许重复下单");
        }
        try {
            //获取代理对象
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            //释放锁
            lock.unLock();
        }

    }*/

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //5.1 查询订单
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        int counts = count.intValue();
        //5.2判断是否存在
        if (counts>0){
            //用户已经购买过了
            log.error("用户已经购买过了");
            return ;
        }
        //6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock -1 ")
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0)
                .update();
        if (!success){
            //扣减失败
            log.error("库存不足");
            return;
        }
        save(voucherOrder);

    }
}
