package com.atguigu.tingshu.account.mapper;

import com.atguigu.tingshu.model.account.UserAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccount> {
    /**
     * 采用悲观锁，检查账户余额是否满足扣款需求
     * @param userId
     * @param amount
     * @return
     */
    UserAccount checkDeduction(@Param("userId") Long userId, @Param("amount") BigDecimal amount);

}
