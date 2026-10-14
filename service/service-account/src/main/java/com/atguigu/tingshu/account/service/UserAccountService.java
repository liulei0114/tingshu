package com.atguigu.tingshu.account.service;

import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.vo.account.AccountDeductVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;
import java.util.Map;

public interface UserAccountService extends IService<UserAccount> {


    void initUserAccount(Map<String, Object> map);

    BigDecimal getAvailableAmount(Long userId);

    void deduct(AccountDeductVo accountDeductVo);
}
