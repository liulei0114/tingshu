package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.common.login.AuthLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
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

	@Operation(summary = "获取当前用户全部专辑列表")
	@GetMapping("/albumInfo/findUserAllAlbumList")
	public Result<List<AlbumInfo>> getUserAllAlbumList(){
		return Result.ok(albumInfoService.findUserAllAlbum(AuthContextHolder.getUserId()));
	}


}

