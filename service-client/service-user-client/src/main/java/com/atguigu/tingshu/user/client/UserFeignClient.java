package com.atguigu.tingshu.user.client;

import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.user.client.impl.UserDegradeFeignClient;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户模块远程调用API接口
 * </p>
 *
 * @author atguigu
 */
@FeignClient(value = "service-user", fallback = UserDegradeFeignClient.class)
public interface UserFeignClient {
    /**
     * 根据用户ID查询用户/主播基本信息
     *
     * @param userId
     * @return
     */
    @GetMapping("/api/user/userInfo/getUserInfoVo/{userId}")
    Result<UserInfoVo> getUserInfoVo(@PathVariable Long userId);

    @PostMapping("/api/user/userInfo/userIsPaidTrack/{userId}/{albumId}")
    Result<List<AlbumTrackListVo>> userIsPaidTrack(
            @PathVariable Long userId,
            @PathVariable Long albumId,
            @RequestBody List<AlbumTrackListVo> needCheckPayStatusTrackIdList
    );

    @GetMapping("/api/user/vipServiceConfig/getVipServiceConfig/{id}")
    Result<VipServiceConfig> getVipServiceConfig(@PathVariable Long id);

    @GetMapping("/api/user/userInfo/isPaidAlbum/{albumId}")
    Result<Boolean> isPaidAlbum(@PathVariable Long albumId);

    /**
     * 查询当前用户某个专辑下已购声音ID列表
     * @param albumId
     * @return
     */
    @GetMapping("/api/user/userInfo/findUserPaidTrackList/{albumId}")
    Result<List<Long>> findUserPaidTrackList(@PathVariable Long albumId);
}
