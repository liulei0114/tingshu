package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.login.AuthLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "微信授权登录接口")
@RestController
@RequestMapping("/api/user/wxLogin")
@Slf4j
public class WxLoginApiController {

    @Autowired
    private UserInfoService userInfoService;


    /**
     * 微信一键登录
     *
     * @param code 小程序端根据当前微信，生成访问为微信服务端临时凭据
     * @return {token:令牌}
     */
    @Operation(summary = "微信一键登录")
    @GetMapping("/wxLogin/{code}")
    public Result<Map<String, String>> wxLogin(@PathVariable String code) {
        Map<String, String> map = userInfoService.wxLogin(code);
        return Result.ok(map);
    }

    @AuthLogin
    @Operation(summary = "获取当前登录用户基本信息")
    @GetMapping("/getUserInfo")
    public Result<UserInfoVo> getUserInfo() {
        Long userId = AuthContextHolder.getUserId();
        UserInfoVo userInfoVo = userInfoService.getUserInfo(userId);
        return Result.ok(userInfoVo);
    }

    @AuthLogin
    @Operation(summary = "修改登录用户基本信息")
    @PostMapping("/updateUser")
    public Result updateUserInfo(@RequestBody UserInfoVo userInfoVo, @RequestHeader String token) {
        Long userId = AuthContextHolder.getUserId();
        userInfoVo.setId(userId);
        userInfoService.updateUserInfo(userInfoVo, token);
        return Result.ok();
    }
}