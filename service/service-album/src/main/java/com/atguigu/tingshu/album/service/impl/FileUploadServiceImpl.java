package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.util.IdUtil;
import com.atguigu.tingshu.album.config.MinioConstantProperties;
import com.atguigu.tingshu.album.service.AuditService;
import com.atguigu.tingshu.album.service.FileUploadService;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.MinioException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Slf4j
@Service

public class FileUploadServiceImpl implements FileUploadService {

    @Autowired
    private MinioConstantProperties minioProperties;

    @Autowired
    private AuditService auditService;

    @Override
    public String uploadFile(MultipartFile file) {
        try {
            BufferedImage bufferedImage = ImageIO.read(file.getInputStream());
            if (bufferedImage == null) {
                throw new GuiguException(500, "非法图片");
            }
            //对图片内容进行校验是否合法
            String suggestion = auditService.auditImage(file);
            log.info("腾讯云图片审核意见: {}", suggestion);
            if ("review".equals(suggestion) || "block".equals(suggestion)) {
                throw new GuiguException(500, "图片内容不合法");
            }
            MinioClient minioClient = minioProperties.getMinioClient();
            String bucketName = minioProperties.getBucketName();

            boolean found =
                    minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!found) {
                // Make a new bucket called 'asiatrip'.
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            } else {
                log.info("Bucket 'asiatrip' already exists.");
            }
            String objectName = IdUtil.simpleUUID()
                    + "-" + file.getOriginalFilename();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());
            String uploadUrl = minioProperties.getEndpointUrl() + "/" + bucketName + "/" + objectName;
            log.info("File uploaded successfully, object name: {}, upload url: {}", objectName, uploadUrl);
            return uploadUrl;
        } catch (MinioException e) {
            log.error("MinioException error occurred: " + e);
            throw new RuntimeException(e);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error occurred: " + e);
            throw new RuntimeException(e);
        }
    }
}
