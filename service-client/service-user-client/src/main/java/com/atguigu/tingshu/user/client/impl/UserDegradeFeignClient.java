package com.atguigu.tingshu.user.client.impl;


import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class UserDegradeFeignClient implements UserFeignClient {

    @Override
    public Result<UserInfoVo> getUserInfoVo(Long userId) {
        log.error("[用户服务]提供远程调用getUserInfoVo执行服务降级");
        return null;
    }

    @Override
    public Result<List<AlbumTrackListVo>> userIsPaidTrack(Long userId, Long albumId, List<AlbumTrackListVo> needCheckPayStatusTrackIdList) {
        log.error("[用户服务]提供远程调用userIsPaidTrack执行服务降级");
        return null;
    }

    @Override
    public Result<VipServiceConfig> getVipServiceConfig(Long id) {
        log.error("[用户服务]提供远程调用getVipServiceConfig执行服务降级");
        return null;
    }

    @Override
    public Result<Boolean> isPaidAlbum(Long albumId) {
        log.error("[用户服务]提供远程调用isPaidAlbum执行服务降级");
        return null;
    }

    @Override
    public Result<List<Long>> findUserPaidTrackList(Long albumId) {
        log.error("[用户服务]提供远程调用findUserPaidTrackList执行服务降级");
        return null;
    }

    @Override
    public Result savePaidRecord(UserPaidRecordVo userPaidRecordVo) {
        log.error("[用户服务]远程调用savePaidRecord执行服务降级");
        return null;
    }


}
