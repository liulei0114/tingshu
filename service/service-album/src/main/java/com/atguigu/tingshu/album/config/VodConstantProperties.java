package com.atguigu.tingshu.album.config;


import com.qcloud.vod.VodUploadClient;
import com.tencentcloudapi.common.Credential;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix="vod") //读取节点
@Data
public class VodConstantProperties {

    private Integer appId;
    private String secretId;
    private String secretKey;
    //https://cloud.tencent.com/document/api/266/31756#.E5.9C.B0.E5.9F.9F.E5.88.97.E8.A1.A8
    private String region;
    private String procedure;
    private String tempPath;
    private String playKey;

    @Bean
    public VodUploadClient vodUploadClient() {
        return new VodUploadClient(secretId, secretKey);
    }

    /**
     * 调用点播平台认证对象
     * @return
     */
    @Bean
    public Credential cred() {
        return new Credential(secretId, secretKey);
    }
}
