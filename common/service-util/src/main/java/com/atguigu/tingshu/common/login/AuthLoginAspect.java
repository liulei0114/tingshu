package com.atguigu.tingshu.common.login;


import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
public class AuthLoginAspect {

    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;

    @Around("execution(* com.atguigu.tingshu.*.api.*.*(..)) && @annotation(authLogin)")
    public Object around(ProceedingJoinPoint pjp, AuthLogin authLogin) throws Throwable {
        log.info("AuthLoginAspect around start");

        // 从请求上下文中获取httprequest
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String token = request.getHeader("token");
        log.info("token: {}", token);
        if (token == null) {
            log.info("token is null,无权访问接口");
            return Result.build(null, ResultCodeEnum.LOGIN_AUTH);
        }
        String loginKey = RedisConstant.USER_LOGIN_KEY_PREFIX + token;
        UserInfoVo user = (UserInfoVo) redisTemplate.opsForValue().get(loginKey);
        if (user == null && authLogin.required()) {
            log.info("token is invalid,无权访问接口");
            return Result.build(null, ResultCodeEnum.LOGIN_AUTH);
        }
        if (user != null) {
            // 有用户信息
            AuthContextHolder.setUserId(user.getId());
        }

        // 二、目标方法 controller使用使用自定义认证注解的方法
        Object result = pjp.proceed();
        log.info("后置逻辑...");
        // 6.避免ThreadLocal引发的内存泄漏，清理ThreadLocal
        AuthContextHolder.removeUserId();
        return result;

    }
}
