package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.login.AuthLogin;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "声音管理")
@RestController
@RequestMapping("api/album")
@SuppressWarnings({"all"})
public class TrackInfoApiController {

    @Autowired
    private TrackInfoService trackInfoService;

    @Autowired
    private VodService vodService;

    @Operation(summary = "音视频文件上传点播平台")
    @PostMapping("/trackInfo/uploadTrack")
    public Result<Map<String, String>> uploadTrack(@RequestParam("file") MultipartFile file) {
        Map<String, String> map = vodService.uploadTrack(file);
        return Result.ok(map);
    }

    @AuthLogin
    @Operation(summary = "保存声音")
    @PostMapping("/trackInfo/saveTrackInfo")
    public Result saveTrackInfo(@Validated @RequestBody TrackInfoVo trackInfoVo) {
        // 1.获取用户ID
        Long userId = AuthContextHolder.getUserId();
        // 2.调用业务层保存声音
        trackInfoService.saveTrackInfo(trackInfoVo, AuthContextHolder.getUserId());
        return Result.ok();
    }

    @AuthLogin
    @Operation(summary = "条件分页查询当前用户声音列表（包含声音统计信息）")
    @PostMapping("/trackInfo/findUserTrackPage/{page}/{limit}")
    public Result<Page<TrackListVo>> getUserTrackPage(
            @PathVariable int page,
            @PathVariable int limit,
            @RequestBody TrackInfoQuery trackInfoQuery
    ) {
        //1.获取当前用户ID
        Long userId = AuthContextHolder.getUserId();
        trackInfoQuery.setUserId(userId);
        //2.构建分页所需分页对象
        Page<TrackListVo> pageInfo = new Page<>(page, limit);
        //3.查询业务层(持久层)获取分页数据
        pageInfo = trackInfoService.getUserTrackPage(pageInfo, trackInfoQuery);
        return Result.ok(pageInfo);
    }

    @Operation(summary = "根据声音ID查询声音信息")
    @GetMapping("/trackInfo/getTrackInfo/{id}")
    public Result<TrackInfo> getTrackInfo(@PathVariable Long id) {
        TrackInfo trackInfo = trackInfoService.getById(id);
        return Result.ok(trackInfo);
    }

    @Operation(summary = "修改声音信息")
    @PutMapping("/trackInfo/updateTrackInfo/{id}")
    public Result updateTrackInfo(@PathVariable Long id, @RequestBody TrackInfo trackInfo){
        trackInfoService.updateTrackInfo(trackInfo);
        return Result.ok();
    }

    @Operation(summary = "删除声音记录")
    @DeleteMapping("/trackInfo/removeTrackInfo/{id}")
    public Result removeTrackInfo(@PathVariable Long id){
        trackInfoService.removeTrackInfo(id);
        return Result.ok();
    }

}

