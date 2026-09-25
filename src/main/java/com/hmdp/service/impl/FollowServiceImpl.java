package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    /**
     * 关注
     * @param followUserId 被关注的用户的id
     * @param isFollow 是否关注
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //1.获取当前登录的用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:"+userId;
        //2.判断到底是关注还是取关
        if (isFollow){
            //2.1关注，新增数据
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            boolean isSuccess = save(follow);
            if (isSuccess){
                //把关注用户的id放入redis的set集合中
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else {
            //2.2取关，删除关注表里的记录
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
            if (isSuccess){
                //把关注用户的id从redis的set集合中移除
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }


        return Result.ok();
    }

    /**
     * 是否关注
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.查询是否关注
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        //3.判断并返回结果
        return Result.ok(count>0);

    }

    /**
     * 共同关注
     * @param id
     * @return
     */
    @Override
    public Result followCommons(Long id) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:"+userId;
        //2.求交集
        String key2 = "follows:"+id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()){
            //无交集
            return Result.ok(Collections.emptyList());
        }
        //3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4.查询用户
        List<UserDTO> users = userService.listByIds(ids).stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
