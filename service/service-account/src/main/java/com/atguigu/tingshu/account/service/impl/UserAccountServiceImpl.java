package com.atguigu.tingshu.account.service.impl;

import com.atguigu.tingshu.account.mapper.UserAccountDetailMapper;
import com.atguigu.tingshu.account.mapper.UserAccountMapper;
import com.atguigu.tingshu.account.service.UserAccountService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.model.account.UserAccount;
import com.atguigu.tingshu.model.account.UserAccountDetail;
import com.atguigu.tingshu.vo.account.AccountDeductVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

    @Override
    public BigDecimal getAvailableAmount(Long userId) {
        LambdaQueryWrapper<UserAccount> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserAccount::getUserId, userId);
        queryWrapper.select(UserAccount::getAvailableAmount);
        UserAccount userAccount = userAccountMapper.selectOne(queryWrapper);
        if (userAccount != null) {
            return userAccount.getAvailableAmount();
        }
        return new BigDecimal("0.00");
    }

    @Override
    // @Transactional(rollbackFor = Exception.class) // sql 加了悲观锁，必须在事务里面，不然slect后就释放了，但是使用了seata分布式事务，就不能加这个注解
    public void deduct(AccountDeductVo accountDeductVo) {
        BigDecimal amount = accountDeductVo.getAmount();
        Long userId = accountDeductVo.getUserId();
        UserAccount userAccount = userAccountMapper.checkDeduction(userId, amount);
        if (userAccount == null) {
            throw new GuiguException(500, "账户余额不足");
        }

        // 扣减账户
        LambdaUpdateWrapper<UserAccount> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(UserAccount::getUserId, userId);
        updateWrapper.eq(UserAccount::getId, userAccount.getId());
        updateWrapper.setSql("total_amount = total_amount -" + amount)
                .setSql("available_amount = available_amount -" + amount)
                .setSql("total_pay_amount = total_pay_amount +" + amount);
        userAccountMapper.update(null, updateWrapper);

        // 记录账户变动日志
        this.saveUserAccountDetail(accountDeductVo.getUserId(), accountDeductVo.getContent(), SystemConstant.ACCOUNT_TRADE_TYPE_MINUS, accountDeductVo.getAmount(), accountDeductVo.getOrderNo());
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
