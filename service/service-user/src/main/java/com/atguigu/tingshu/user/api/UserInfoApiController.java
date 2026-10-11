package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.login.AuthLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "用户管理接口")
@RestController
@RequestMapping("api/user")
@SuppressWarnings({"all"})
public class UserInfoApiController {

    @Autowired
    private UserInfoService userInfoService;

    /**
     * 根据用户ID查询用户/主播基本信息
     *
     * @param userId
     * @return
     */
    @Operation(summary = "根据用户ID查询用户/主播基本信息")
    @GetMapping("/userInfo/getUserInfoVo/{userId}")
    public Result<UserInfoVo> getUserInfoVo(@PathVariable Long userId) {
        UserInfoVo userInfoVo = userInfoService.getUserInfo(userId);
        return Result.ok(userInfoVo);
    }

    @Operation(summary = "提交需要检查购买状态声音ID列表，响应每个声音购买状态")
    @PostMapping("/userInfo/userIsPaidTrack/{userId}/{albumId}")
    public Result<List<AlbumTrackListVo>> userIsPaidTrack(
            @PathVariable Long userId,
            @PathVariable Long albumId,
            @RequestBody List<AlbumTrackListVo> needCheckPayStatusTrackIdList
    ) {
        needCheckPayStatusTrackIdList = userInfoService.userIsPaidTrack(userId, albumId, needCheckPayStatusTrackIdList);
        return Result.ok(needCheckPayStatusTrackIdList);
    }

}

