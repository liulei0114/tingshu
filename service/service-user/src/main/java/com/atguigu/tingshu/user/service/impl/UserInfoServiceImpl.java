package com.atguigu.tingshu.user.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.model.user.UserListenProcess;
import com.atguigu.tingshu.model.user.UserPaidAlbum;
import com.atguigu.tingshu.model.user.UserPaidTrack;
import com.atguigu.tingshu.user.mapper.UserInfoMapper;
import com.atguigu.tingshu.user.mapper.UserPaidAlbumMapper;
import com.atguigu.tingshu.user.mapper.UserPaidTrackMapper;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.user.service.UserListenProcessService;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserListenProcessVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Autowired
    private AlbumFeignClient albumFeignClient;

    @Autowired
    private UserPaidAlbumMapper userPaidAlbumMapper;

    @Autowired
    private UserPaidTrackMapper userPaidTrackMapper;

    @Autowired
    private UserListenProcessService userListenProcessService;


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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserInfo(UserInfoVo userInfoVo, String token) {
        userInfoMapper.updateById(BeanUtil.copyProperties(userInfoVo, UserInfo.class));
        String key = RedisConstant.USER_LOGIN_KEY_PREFIX + token;
        UserInfoVo user = (UserInfoVo) redisTemplate.opsForValue().get(key);
        user.setAvatarUrl(userInfoVo.getAvatarUrl());
        user.setNickname(userInfoVo.getNickname());
        Long remain = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        // 读剩余秒数
        if (remain != null && remain > 0) {
            redisTemplate.opsForValue().set(key, user, remain, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(key, user, RedisConstant.USER_LOGIN_KEY_TIMEOUT, TimeUnit.SECONDS);
        }
    }

    @Override
    public List<AlbumTrackListVo> userIsPaidTrack(Long userId, Long albumId, List<AlbumTrackListVo> needCheckPayStatusTrackIdList) {
        /**
         * 1.登录了，vip逻辑
         *   2.1 是否是vip，专辑是否是vip免费
         *      2.1.1 是，全部免费
         * 2.付费逻辑
         *   3.1 查询用户专辑和声音购买记录，有则免费，没有则收费
         */
        // 获取专辑信息
        Result<AlbumInfo> albumInfoResult = albumFeignClient.getAlbumInfo(albumId);
        if (albumInfoResult == null || albumInfoResult.getCode() != 200) {
            log.error("远程调用【专辑服务（getAlbumInfo）】失败,getAlbumInfo:{}", albumId);
            String message = albumInfoResult != null ? albumInfoResult.getMessage() : "";
            throw new GuiguException(500, "远程调用【专辑服务（getAlbumInfo）】失败," + message);
        }
        AlbumInfo albumInfo = albumInfoResult.getData();
        // 用户信息
        UserInfo userInfo = userInfoMapper.selectById(userId);

        // 获取前几之后的ids
        List<Long> ids = needCheckPayStatusTrackIdList.stream().filter(s -> s.getOrderNum() > albumInfo.getTracksForFree()).map(s->s.getTrackId()).collect(Collectors.toList());
        // 是vip 并且专辑是vip免费
        if (this.validateVipStatus(userInfo) && SystemConstant.ALBUM_PAY_TYPE_VIPFREE.equals(albumInfo.getPayType())) {
            return setTrackPayStatus(needCheckPayStatusTrackIdList, false, null);
        }
        // 付费逻辑了，查询用户专辑和声音购买记录
        UserPaidAlbum userPaidAlbum = userPaidAlbumMapper.selectOne(new LambdaQueryWrapper<UserPaidAlbum>().eq(UserPaidAlbum::getUserId, userId).eq(UserPaidAlbum::getAlbumId, albumId));
        if (userPaidAlbum != null) {
            return setTrackPayStatus(needCheckPayStatusTrackIdList, false, null);
        }
        List<UserPaidTrack> userPaidTracks = userPaidTrackMapper.selectList(new LambdaQueryWrapper<UserPaidTrack>().eq(UserPaidTrack::getUserId, userId).eq(UserPaidTrack::getAlbumId, albumId));
        if (CollUtil.isNotEmpty(userPaidTracks)) {
            List<Long> trackIds = userPaidTracks.stream().map(userPaidTrack -> userPaidTrack.getTrackId()).toList();
            needCheckPayStatusTrackIdList.stream().filter(s -> s.getOrderNum() > albumInfo.getTracksForFree()).forEach(albumTrackListVo -> {
                if (!trackIds.contains(albumTrackListVo.getTrackId())) {
                    albumTrackListVo.setIsShowPaidMark(true);
                }
            });
            return needCheckPayStatusTrackIdList;
        }
        return setTrackPayStatus(needCheckPayStatusTrackIdList, true, ids);
    }

    @Override
    public BigDecimal getTrackBreakSecond(Long userId, Long trackId) {

        UserListenProcess userListenProcess = userListenProcessService.getUserListenProcess(userId, trackId);
        if (userListenProcess != null) {
            return userListenProcess.getBreakSecond();
        }

        return BigDecimal.valueOf(0);
    }

    @Override
    public void updateUserListenProcess(Long userId, UserListenProcessVo userListenProcessVo) {
        UserListenProcess userListenProcess = userListenProcessService.getUserListenProcess(userId, userListenProcessVo.getTrackId());
        if (userListenProcess == null) {
            userListenProcess = new UserListenProcess();
            userListenProcess.setId(String.valueOf(userId) + '_' + String.valueOf(userListenProcessVo.getTrackId()));
            userListenProcess.setUserId(userId);
            userListenProcess.setTrackId(userListenProcessVo.getTrackId());
            userListenProcess.setBreakSecond(BigDecimal.valueOf(0));
            userListenProcess.setIsShow(1);
            userListenProcess.setAlbumId(userListenProcessVo.getAlbumId());
            userListenProcess.setCreateTime(new Date());
            userListenProcess.setUpdateTime(new Date());
        }else{
            userListenProcess.setBreakSecond(userListenProcessVo.getBreakSecond());
            userListenProcess.setUpdateTime(new Date());
        }
        userListenProcessService.saveOrUpdateUserListenProcess(userListenProcess);

        String redisKey
                = RedisConstant.USER_TRACK_REPEAT_STAT_PREFIX + userId + "_" + userListenProcessVo.getAlbumId() + "_" + userListenProcessVo.getTrackId();
        //5.1.2 采用Redis提供set k v ex nx  命令，设置RedisKey，设置过期时间，写入成功则发送MQ消息，更新统计数值；写入失败则忽略
        Date date = new Date();
        long ttl = DateUtil.endOfDay(date).getTime() - date.getTime();
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", ttl, TimeUnit.MILLISECONDS);
        if (flag) {
            //5.2.1 构建MQ消息VO对象 包含：专辑ID、声音ID、统计类型、增量数值、businessNo-UUID（用于消费者端幂等性处理），TODO VO必须序列化接口
            TrackStatMqVo mqVo = new TrackStatMqVo();
            mqVo.setBusinessNo(IdUtil.fastUUID());
            mqVo.setAlbumId(userListenProcessVo.getAlbumId());
            mqVo.setTrackId(userListenProcessVo.getTrackId());
            mqVo.setStatType(SystemConstant.TRACK_STAT_PLAY);
            mqVo.setCount(1);
            //5.2.2 发送MQ消息
            rabbitService.sendMessage(MqConst.EXCHANGE_TRACK, MqConst.ROUTING_TRACK_STAT_UPDATE, mqVo);
        }
    }

    public List<AlbumTrackListVo> setTrackPayStatus(List<AlbumTrackListVo> result, Boolean isShowPaidMark, List<Long> ids) {
        if (ids != null) {
            for (AlbumTrackListVo albumTrackListVo : result) {
                if (ids.contains(albumTrackListVo.getTrackId())) {
                    albumTrackListVo.setIsShowPaidMark(isShowPaidMark);
                }
            }
        } else {
            for (AlbumTrackListVo albumTrackListVo : result) {
                albumTrackListVo.setIsShowPaidMark(isShowPaidMark);
            }
        }
        return result;
    }

    public Boolean validateVipStatus(UserInfo userInfo) {
        if (userInfo == null) {
            throw new GuiguException(500, "未查询到用户信息");
        }
        // 空安全处理：isVip为null按普通用户处理；vipExpireTime为null视为已过期，避免拆箱/空指针
        return Objects.equals(userInfo.getIsVip(), 1)
                && userInfo.getVipExpireTime() != null
                && userInfo.getVipExpireTime().after(new Date());
    }

}
