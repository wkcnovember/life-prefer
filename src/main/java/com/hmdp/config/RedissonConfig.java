package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.*;
import org.redisson.api.redisnode.BaseRedisNodes;
import org.redisson.api.redisnode.RedisNodes;
import org.redisson.client.codec.Codec;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient(){
        //配置
        Config config = new Config();
        //添加 Redis 地址，此处添加了单点的地址，也可以使用 config.useClusterServers() 添加集群地址
        config.useSingleServer().setAddress("redis://192.168.100.128:6379").setPassword("349747");
        //创建一个RedissonClient对象
        return Redisson.create(config);
    }
}