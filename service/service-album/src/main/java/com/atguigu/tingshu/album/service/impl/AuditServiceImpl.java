package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.codec.Base64;
import com.atguigu.tingshu.album.config.VodConstantProperties;
import com.atguigu.tingshu.album.service.AuditService;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.ims.v20201229.ImsClient;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationRequest;
import com.tencentcloudapi.ims.v20201229.models.ImageModerationResponse;
import com.tencentcloudapi.tms.v20201229.TmsClient;
import com.tencentcloudapi.tms.v20201229.models.TextModerationRequest;
import com.tencentcloudapi.tms.v20201229.models.TextModerationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author: atguigu
 * @create: 2025-05-29 14:12
 */
@Slf4j
@Service
public class AuditServiceImpl implements AuditService {

    @Autowired
    private Credential credential;

    @Autowired
    private VodConstantProperties vodConstantProperties;

    /**
     * 文本内容审核
     *
     * @param content
     * @return
     */
    @Override
    public String auditText(String content) {
        try {
            // 1.实例化要请求产品的client对象,clientProfile是可选的
            TmsClient client = new TmsClient(credential, vodConstantProperties.getRegion());
            // 2.实例化一个请求对象,每个接口都会对应一个request对象
            TextModerationRequest req = new TextModerationRequest();
            // 对审核内容进行Base64编码
            req.setContent(Base64.encode(content));
            // 3.返回的resp是一个TextModerationResponse的实例，与请求对象对应
            TextModerationResponse resp = client.TextModeration(req);
            // 4.解析文本审核结果
            if (resp != null) {
                String suggestion = resp.getSuggestion();
                return suggestion.toLowerCase();
            }
        } catch (TencentCloudSDKException e) {
            log.error("文本内容:审核失败", content);
            System.out.println(e.toString());
        }
        return null;
    }

    /**
     * 图片内容审核
     *
     * @param file
     * @return
     */
    @Override
    public String auditImage(MultipartFile file) {
        try {
            // 1.实例化要请求产品的client对象,clientProfile是可选的
            ImsClient client = new ImsClient(credential, vodConstantProperties.getRegion());
            // 2.实例化一个请求对象,每个接口都会对应一个request对象
            ImageModerationRequest req = new ImageModerationRequest();
            // 对图片进行Base64编码
            req.setFileContent(Base64.encode(file.getInputStream()));
            // 3.返回的resp是一个ImageModerationResponse的实例，与请求对象对应
            ImageModerationResponse resp = client.ImageModeration(req);
            if (resp != null) {
                String suggestion = resp.getSuggestion();
                return suggestion.toLowerCase();
            }
        } catch (Exception e) {
            log.error("图片内容,审核失败，{}，{}", e, file.getOriginalFilename());
        }
        return null;
    }


}
