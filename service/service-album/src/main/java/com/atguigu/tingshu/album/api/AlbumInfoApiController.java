package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.common.login.AuthLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.atguigu.tingshu.vo.album.AlbumTrackListVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Tag(name = "专辑管理")
@RestController
@RequestMapping("api/album")
@SuppressWarnings({"all"})
public class AlbumInfoApiController {

	@Autowired
	private AlbumInfoService albumInfoService;

	@Autowired
	private TrackInfoService trackInfoService;

	@Operation(summary = "保存专辑信息")
	@PostMapping("/albumInfo/saveAlbumInfo")
	@AuthLogin
	public Result<Long> saveAlbumInfo(@Validated @RequestBody AlbumInfoVo albumInfoVo) {
		return Result.ok(albumInfoService.saveAlbumInfo(albumInfoVo));
	}

	@AuthLogin
	@Operation(summary = "分页条件查询当前登录用户发布专辑")
	@PostMapping("/albumInfo/findUserAlbumPage/{page}/{limit}")
	public Result<Page<AlbumListVo>> findUserAlbumPage(@PathVariable Long page,
                                                       @PathVariable Long limit,
                                                       @RequestBody AlbumInfoQuery albumInfoQuery) {
		Page<AlbumListVo> pageParam = new Page<>(page, limit);
		if(albumInfoQuery == null) {
			albumInfoQuery = new AlbumInfoQuery();
		}
		albumInfoQuery.setUserId(AuthContextHolder.getUserId());
		return Result.ok(albumInfoService.findUserAlbumPage(pageParam, albumInfoQuery));
	}

	@Operation(summary = "根据专辑ID删除专辑")
	@DeleteMapping("/albumInfo/removeAlbumInfo/{id}")
	@AuthLogin
	public Result removeAlbumInfo(@PathVariable Long id) {
		albumInfoService.removeAlbumInfo(id);
		return Result.ok();
	}

	@Operation(summary = "根据专辑ID查询专辑信息（包括专辑标签列表）")
	@GetMapping("/albumInfo/getAlbumInfo/{id}")
	public Result<AlbumInfo> getAlbumInfo(@PathVariable Long id) {
		AlbumInfo albumInfo = albumInfoService.getAlbumInfo(id);
		return Result.ok(albumInfo);
	}

	@Operation(summary = "更新专辑信息")
	@PutMapping("/albumInfo/updateAlbumInfo/{id}")
	public Result updateAlbumInfo(@PathVariable Long id, @Validated @RequestBody AlbumInfoVo albumInfoVo) {
		albumInfoService.updateAlbumInfo(id, albumInfoVo);
		return Result.ok();
	}

	@AuthLogin
	@Operation(summary = "获取当前用户全部专辑列表")
	@GetMapping("/albumInfo/findUserAllAlbumList")
	public Result<List<AlbumInfo>> getUserAllAlbumList(){
		return Result.ok(albumInfoService.findUserAllAlbum(AuthContextHolder.getUserId()));
	}

	/**
	 * 根据专辑ID查询专辑统计信息
	 * @param albumId
	 * @return
	 */
	@Operation(summary = "根据专辑ID查询专辑统计信息")
	@GetMapping("/albumInfo/getAlbumStatVo/{albumId}")
	public Result<AlbumStatVo> getAlbumStatVo(@PathVariable Long albumId){
		AlbumStatVo albumStatVo = albumInfoService.getAlbumStatVo(albumId);
		return Result.ok(albumStatVo);
	}

	@AuthLogin(required = false)
	@Operation(summary = "分页查询专辑下声音列表（动态渲染付费标识）")
	@GetMapping("/trackInfo/findAlbumTrackPage/{albumId}/{page}/{limit}")
	public Result<IPage<AlbumTrackListVo>> findAlbumTrackPage(
			@PathVariable Long albumId,
			@PathVariable Long page,
			@PathVariable Long limit) {
		//1.尝试获取用户ID
		Long userId = AuthContextHolder.getUserId();
		//2.构建分页对象：封装当前页码、每页记录数
		IPage<AlbumTrackListVo> pageInfo = new Page<>(page, limit);

		//3.调用业务逻辑层->持久层：封装：总记录数，总页数，当前页数据
		pageInfo = trackInfoService.findAlbumTrackPage(pageInfo, albumId, userId);

		//4.响应分页对象
		return Result.ok(pageInfo);
	}


}

