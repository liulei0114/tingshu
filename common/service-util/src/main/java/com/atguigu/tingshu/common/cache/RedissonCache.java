package com.atguigu.tingshu.common.cache;


import com.atguigu.tingshu.common.constant.RedisConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RedissonCache {

    /**
     * 存入Redis业务数据以及锁的前缀
     * @return
     */
    String prefix() default "";

    /**
     * 存入Redis缓存业务数据过期时间
     * @return
     */
    long ttl() default RedisConstant.ALBUM_TIMEOUT;

    /**
     * 时间单位
     * @return
     */
    TimeUnit unit() default TimeUnit.SECONDS;
}
