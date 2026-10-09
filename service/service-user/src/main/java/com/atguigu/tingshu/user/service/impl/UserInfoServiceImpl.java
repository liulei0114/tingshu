package com.atguigu.tingshu.user.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.user.mapper.UserInfoMapper;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.service.WxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private WxMaService wxMaService;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private RedisTemplate redisTemplate;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> wxLogin(String code) {
        try {
            WxMaJscode2SessionResult sessionInfo = wxMaService.getUserService().getSessionInfo(code);
            // 获取微信账号唯一标识
            String openid = sessionInfo.getOpenid();
            // 是不是新用户
            UserInfo userInfo = userInfoMapper.selectOne(new QueryWrapper<UserInfo>().eq("wx_open_id", openid));
            if (userInfo == null) {
                // 新用户
                userInfo = new UserInfo();
                userInfo.setWxOpenId(openid);
                userInfo.setNickname(IdUtil.nanoId());
                userInfo.setAvatarUrl("http://192.168.200.6:9000/tingshu/2024-08-07/c77b4b89-6f92-4dc7-894f-71626f14195a.jpg");
                userInfoMapper.insert(userInfo);
                // 初始化账户，方案一：Openfeign远程调用 分布式事务问题  方案二：采用MQ可靠性消息队列实现数据最终一致
                Map<String, Object> map = new HashMap<>();
                map.put("userId", userInfo.getId());
                map.put("amount", new BigDecimal("100"));
                map.put("title", "新用户专项体验金活动");
                map.put("orderNo", "ZS" + IdUtil.getSnowflakeNextId());
                // 3.2.2 发送消息到MQ
                rabbitService.sendMessage(MqConst.EXCHANGE_USER, MqConst.ROUTING_USER_REGISTER, map);
            }
            // 不是新用户
            String token = IdUtil.randomUUID();
            String loginToken = RedisConstant.USER_LOGIN_KEY_PREFIX + token;
            UserInfoVo userInfoVo = BeanUtil.copyProperties(userInfo, UserInfoVo.class);
            redisTemplate.opsForValue().set(loginToken, userInfoVo, RedisConstant.USER_LOGIN_KEY_TIMEOUT, TimeUnit.SECONDS);
            Map<String, String> result = new HashMap<>();
            result.put("token", token);
            return result;
        } catch (Exception e) {
            log.error("微信登录失败");
            throw new GuiguException(500, "微信登录失败");
        }
    }

    @Override
    public UserInfoVo getUserInfo(Long userId) {
        UserInfo userInfo = userInfoMapper.selectById(userId);
        if (userInfo != null) {
            return BeanUtil.copyProperties(userInfo, UserInfoVo.class);
        }
        throw new GuiguException(500, "用户不存在");
    }
}
