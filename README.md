# 生活优选（点评类平台）

类大众点评平台，包含商户点评、优惠券秒杀、社交关注、附近商家搜索，对标「美团 / 大众点评」的点评业务。

## 技术栈

`Spring Boot` `MyBatis-Plus` `MySQL` `Redis` `RabbitMQ` `JWT`

## 核心实现

- **「一锁二判三更新」幂等秒杀接口**：分布式锁 + Redis 预减库存 + 数据库兜底，JMeter 压测（200 并发）QPS 1500+ 且未出现超卖
- **签到**：Redis BitMap 实现，相较行式存储节省约 90% 空间
- **UV 统计**：HyperLogLog 实现，百万级误差控制在 1% 以内（标准误差 0.81%）
- **缓存**：Cache Aside 策略，结合主动更新与超时剔除，降低数据库回源压力
- **异步解耦**：RabbitMQ 异步化下单流程，提升系统吞吐与响应速度

## 关于本项目

本项目用于系统实践 Spring Boot 后端开发与 Redis 高阶数据结构应用，重点在于高并发场景下的秒杀、签到、计数类问题的方案设计与压测验证。

## 运行说明

1. 环境依赖：JDK 17、Maven 3.6+、MySQL、Redis、RabbitMQ
2. 修改 `src/main/resources/application.yaml` 中的数据库与 Redis 连接信息（密码需自行配置）
3. 启动后访问 http://localhost:8081