package com.atguigu.tingshu.user.api;

import com.atguigu.tingshu.common.login.AuthLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.user.service.UserListenProcessService;
import com.atguigu.tingshu.vo.user.UserListenProcessVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Tag(name = "用户声音播放进度管理接口")
@RestController
@RequestMapping("api/user")
@SuppressWarnings({"all"})
public class UserListenProcessApiController {

	@Autowired
	private UserListenProcessService userListenProcessService;

	@Autowired
	private UserInfoService userInfoService;

	@AuthLogin(required = false)
	@Operation(summary = "获取声音播放进度")
	@GetMapping("/userListenProcess/getTrackBreakSecond/{trackId}")
	public Result<BigDecimal> getTrackBreakSecond(@PathVariable Long trackId) {
		//1.获取当前用户ID
		Long userId = AuthContextHolder.getUserId();
		//2.如果用户登录才查询上次播放进度
		if (userId != null) {
			BigDecimal breakSecond = userInfoService.getTrackBreakSecond(userId, trackId);
			return Result.ok(breakSecond);
		}
		return Result.ok(BigDecimal.valueOf(0));
	}

	@AuthLogin(required = false)
	@Operation(summary = "更新声音播放进度")
	@PostMapping("/userListenProcess/updateListenProcess")
	public Result updateListenProcess(@RequestBody UserListenProcessVo userListenProcessVo) {
		//1.获取当前用户ID
		Long userId = AuthContextHolder.getUserId();
		//2.如果用户登录才查询上次播放进度
		if (userId != null) {
			userInfoService.updateUserListenProcess(userId, userListenProcessVo);
		}
		return Result.ok();
	}

	@AuthLogin
	@GetMapping("/userListenProcess/getLatelyTrack")
	public Result<Map<String, Long>> getLatelyTrack(){
		Long userId = AuthContextHolder.getUserId();
		Map<String, Long> map = userListenProcessService.getLatelyTrack(userId);
		return Result.ok(map);
	}
}

