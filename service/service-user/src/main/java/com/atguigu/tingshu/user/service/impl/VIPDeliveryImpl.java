package com.atguigu.tingshu.user.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.model.user.UserVipService;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.user.mapper.UserInfoMapper;
import com.atguigu.tingshu.user.mapper.UserVipServiceMapper;
import com.atguigu.tingshu.user.mapper.VipServiceConfigMapper;
import com.atguigu.tingshu.user.service.DeliveryStrategy;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component(SystemConstant.ORDER_ITEM_TYPE_VIP)
public class VIPDeliveryImpl implements DeliveryStrategy {

    @Autowired
    private UserVipServiceMapper userVipServiceMapper;

    @Autowired
    private VipServiceConfigMapper vipServiceConfigMapper;

    @Autowired
    private UserInfoMapper userInfoMapper;

    /**
     * 虚拟物品-普通会员-发货行为实现
     *
     * @param userPaidRecordVo
     */
    @Override
    public void delivery(UserPaidRecordVo userPaidRecordVo) {
        // 3. 处理虚拟物品"会员"发货逻辑
        // 3.1 根据订单编号查询已购会员记录，如果存在，则返回
        Long count = userVipServiceMapper.selectCount(
                new LambdaQueryWrapper<UserVipService>()
                        .eq(UserVipService::getOrderNo, userPaidRecordVo.getOrderNo())
        );
        if (count > 0) {
            return;
        }
        // 3.2 判断当前用户是否为会员
        Boolean isVIP = false;
        UserInfo userInfo = userInfoMapper.selectById(userPaidRecordVo.getUserId());
        Date now = new Date();
        if (userInfo.getIsVip().intValue() == 1 && userInfo.getVipExpireTime().after(now)) {
            isVIP = true;
        }
        // 3.3 新增会员购买记录
        UserVipService userVipService = new UserVipService();
        userVipService.setOrderNo(userPaidRecordVo.getOrderNo());
        userVipService.setUserId(userPaidRecordVo.getUserId());
        // 3.3.1 获取会员套餐信息得到服务月数
        VipServiceConfig vipServiceConfig = vipServiceConfigMapper.selectById(userPaidRecordVo.getItemIdList().get(0));
        Integer serviceMonth = vipServiceConfig.getServiceMonth();
        // 3.3.2 计算本次会员购买记录生效时间
        // 3.3.3 计算本次会员购买记录失效时间
        if (!isVIP) {
            // 普通用户，生效时间为当前时间 失效时间=当前时间+购买会员套餐月数
            userVipService.setStartTime(now);
            DateTime expireTime = DateUtil.offsetMonth(now, serviceMonth);
            userVipService.setExpireTime(expireTime);
        } else {
            // 会员用户，生效时间为=会员的到期时间+1 失效时间=会员的到期时间+购买会员套餐月数
            Date vipExpireTime = userInfo.getVipExpireTime();  // 6.14
            userVipService.setStartTime(DateUtil.offsetDay(vipExpireTime, 1)); // 6.15
            userVipService.setExpireTime(DateUtil.offsetMonth(userVipService.getStartTime(), serviceMonth)); // 7.15
        }
        userVipServiceMapper.insert(userVipService);
        // 3.4 更新用户会员标识以及更新会员过期时间
        userInfo.setIsVip(1);
        userInfo.setVipExpireTime(userVipService.getExpireTime());
        userInfoMapper.updateById(userInfo);
    }
}
