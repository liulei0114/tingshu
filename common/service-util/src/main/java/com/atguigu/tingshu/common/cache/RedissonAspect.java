package com.atguigu.tingshu.common.cache;


import com.atguigu.tingshu.common.constant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
@Aspect
@Component
public class RedissonAspect {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Around("@annotation(redissonCache)")
    public Object around(ProceedingJoinPoint joinPoint, RedissonCache redissonCache) throws Throwable {
        String params = "none";
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0 && args[0] != null) {
            params = Arrays.stream(args)
                    .map(Object::toString)
                    .collect(Collectors.joining("_"));
        }
        try {
            String redisKey = redissonCache.prefix() + params;
            Object o = redisTemplate.opsForValue().get(redisKey);
            if (o != null) {
                return o;
            }

            RLock lock = redissonClient.getLock(params + RedisConstant.CACHE_LOCK_SUFFIX);
            boolean b = lock.tryLock();
            if (b) {
                try {
                    Object result = joinPoint.proceed();
                    redisTemplate.opsForValue().set(redisKey, result, redissonCache.ttl(), redissonCache.unit());
                    return result;
                } finally {
                    lock.unlock();
                }

            } else {
                Thread.sleep(100);
                return this.around(joinPoint, redissonCache);
            }
        } catch (Throwable e) {
            log.error("Redis服务异常：{}", e.getMessage());
            return joinPoint.proceed();
        }
    }
}
