package com.atguigu.tingshu.album.api;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.atguigu.tingshu.album.service.FileUploadService;
import io.minio.*;
import io.minio.errors.MinioException;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.atguigu.tingshu.common.result.Result;


import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Tag(name = "上传管理接口")
@RestController
@RequestMapping("api/album")
@Slf4j
public class FileUploadApiController {

    @Autowired
    private FileUploadService fileUploadService;

    @PostMapping("/fileUpload")
    public Result<String> upload(MultipartFile file) {
        log.info("文件上传: {}", file.getOriginalFilename());
        String url = fileUploadService.uploadFile(file);
        if (StrUtil.isBlank(url)) {
            return Result.fail("文件上传失败");
        }
        return Result.ok(url);
    }


}
