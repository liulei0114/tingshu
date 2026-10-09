package com.atguigu.tingshu.account.service.impl;

import com.atguigu.tingshu.account.mapper.UserAccountDetailMapper;
import com.atguigu.tingshu.account.mapper.UserAccountMapper;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.model.account.UserAccountDetail;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {

    @Autowired
    private UserAccountMapper userAccountMapper;

    @Autowired
    private UserAccountDetailMapper userAccountDetailMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initUserAccount(Map<String, Object> map) {
        // 1.新增账户记录
        Long userId = (Long) map.get("userId");
        BigDecimal amount = (BigDecimal) map.get("amount");
        String title = (String) map.get("title");
        String orderNo = (String) map.get("orderNo");
        UserAccount userAccount = new UserAccount();
        userAccount.setUserId(userId);
        userAccount.setTotalAmount(amount);
        userAccount.setAvailableAmount(amount);
        userAccount.setTotalIncomeAmount(amount);
        userAccount.setLockAmount(new BigDecimal("0.00"));
        userAccount.setTotalPayAmount(new BigDecimal("0.00"));
        userAccountMapper.insert(userAccount);
        // 2.新增账户变动日志
        this.saveUserAccountDetail(userId, title, SystemConstant.ACCOUNT_TRADE_TYPE_DEPOSIT, amount, orderNo);

    }

    private void saveUserAccountDetail(Long userId, String title, String tradeType, BigDecimal amount, String orderNo) {
        UserAccountDetail userAccountDetail = new UserAccountDetail();
        userAccountDetail.setUserId(userId);
        userAccountDetail.setTitle(title);
        userAccountDetail.setTradeType(tradeType);
        userAccountDetail.setAmount(amount);
        userAccountDetail.setOrderNo(orderNo);
        userAccountDetailMapper.insert(userAccountDetail);
    }
}
