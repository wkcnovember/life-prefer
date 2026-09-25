package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
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
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = RedisConstants.CACHE_SHOP_TYPE_KEY;
        //从redis查询店铺类型
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);
        List<ShopType> typeList = null;

        //判断缓存是否命中
        if (StrUtil.isNotBlank(shopTypeJson)) {
            //命中 直接返回
            typeList = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(typeList);
        }
        //未命中 查询数据库
        typeList = list(new LambdaQueryWrapper<ShopType>().orderByAsc(ShopType::getSort));
        //判断数据库是否存在该数据
        if(CollUtil.isEmpty(typeList)) {
            //不存在数据 返回失败信息
            return Result.fail("店铺类型不存在!");
        }
        //存在数据 写入redis 并返回查询的数据
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(typeList),RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(typeList);
    }
}
