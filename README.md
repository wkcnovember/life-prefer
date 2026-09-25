# 生活优选（点评类平台）

类大众点评平台，包含商户点评、优惠券秒杀、社交关注、附近商家搜索。

## 技术栈

`Spring Boot` `MyBatis-Plus` `MySQL` `Redis` `RabbitMQ` `JWT`

## 核心实现

- 「一锁二判三更新」幂等秒杀接口：分布式锁 + Redis 预减库存 + 数据库兜底，JMeter 压测 QPS 1500+ 无超卖
- Redis BitMap 签到（较行式存储省约 90% 空间）、HyperLogLog 统计 UV（百万级误差 1% 以内）
- Cache Aside 缓存策略 + 主动更新与超时剔除
- RabbitMQ 异步化解耦下单流程

## 状态

项目代码整理中，将逐步提交上线。
