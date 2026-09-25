package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {


    /**
     * 前置拦截器  用于判断用户是否登录
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //判断当前用户是否登录
        if (UserHolder.getUser() == null){
            //没有登录  返回状态码401
            response.setStatus(401);
            log.info("登录拦截器：阻拦,{}",UserHolder.getUser());
            return false;

        }
        //有用户 放行

        log.info("登录拦截器：放行");
        return true;

    }
}
