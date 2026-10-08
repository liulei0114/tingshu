package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
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

    @Operation(summary = "保存声音")
    @PostMapping("/trackInfo/saveTrackInfo")
    public Result saveTrackInfo(@Validated @RequestBody TrackInfoVo trackInfoVo) {
        // 1.获取用户ID
        Long userId = AuthContextHolder.getUserId();
        // 2.调用业务层保存声音
        trackInfoService.saveTrackInfo(trackInfoVo, 1L);
        return Result.ok();
    }


}

