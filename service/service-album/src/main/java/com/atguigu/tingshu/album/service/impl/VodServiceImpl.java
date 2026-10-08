package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.config.VodConstantProperties;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.util.UploadFileUtil;
import com.atguigu.tingshu.vo.album.TrackMediaInfoVo;
import com.qcloud.vod.VodUploadClient;
import com.qcloud.vod.model.VodUploadRequest;
import com.qcloud.vod.model.VodUploadResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tencentcloudapi.vod.v20180717.models.DescribeMediaInfosRequest;
import com.tencentcloudapi.vod.v20180717.models.DescribeMediaInfosResponse;
import com.tencentcloudapi.vod.v20180717.models.MediaBasicInfo;
import com.tencentcloudapi.vod.v20180717.models.MediaInfo;
import com.tencentcloudapi.vod.v20180717.models.MediaMetaData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@Service
public class VodServiceImpl implements VodService {

    @Autowired
    private VodConstantProperties vodConstantProperties;

    @Autowired
    private VodUploadClient vodUploadClient;

    @Autowired
    private Credential credential;

    @Override
    public Map<String, String> uploadTrack(MultipartFile file) {
        try {
            //1.将上传文件保存到本地得到文件路径 TODO:后续采用定时任务清理临时目录下使用完毕文件
            String filePath = UploadFileUtil.uploadTempPath(vodConstantProperties.getTempPath(), file);
            //2.构造上传请求对象:设置媒体本地上传路径
            VodUploadRequest request = new VodUploadRequest();
            request.setMediaFilePath(filePath);
            //3.调用上传方法，传入接入点地域及上传请求。
            VodUploadResponse response = vodUploadClient.upload(vodConstantProperties.getRegion(), request);
            //4.解析上传文件结果，得到文件标识以及访问路径
            if (response != null) {
                String fileId = response.getFileId();
                String mediaUrl = response.getMediaUrl();
                return Map.of("mediaFileId", fileId, "mediaUrl", mediaUrl);
            }
            return null;
        } catch (Exception e) {
            log.error("上传文件失败", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public TrackMediaInfoVo getTrackMediaInfo(String mediaFileId) {
        try {
            // 1. 实例化点播客户端
            VodClient client = new VodClient(credential, vodConstantProperties.getRegion());
            // 2. 构造请求：根据文件id查询媒体信息
            DescribeMediaInfosRequest request = new DescribeMediaInfosRequest();
            request.setFileIds(new String[]{mediaFileId});
            // 3. 发起请求并获取响应
            DescribeMediaInfosResponse response = client.DescribeMediaInfos(request);
            MediaInfo[] mediaInfoSet = response.getMediaInfoSet();
            if (mediaInfoSet == null || mediaInfoSet.length == 0) {
                throw new GuiguException(400, "未获取到音频媒体信息");
            }
            MediaInfo mediaInfo = mediaInfoSet[0];
            MediaBasicInfo basicInfo = mediaInfo.getBasicInfo();
            MediaMetaData metaData = mediaInfo.getMetaData();

            TrackMediaInfoVo trackMediaInfoVo = new TrackMediaInfoVo();
            // 媒体文件类型
            trackMediaInfoVo.setType(basicInfo.getType());
            // 播放地址（开启Key防盗链时需追加playKey）
            trackMediaInfoVo.setMediaUrl(basicInfo.getMediaUrl() + "?playKey=" + vodConstantProperties.getPlayKey());
            // 声音时长（秒）
            trackMediaInfoVo.setDuration(metaData.getDuration());
            // 文件大小（字节）
            trackMediaInfoVo.setSize(metaData.getSize());
            return trackMediaInfoVo;
        } catch (TencentCloudSDKException e) {
            log.error("获取音频媒体信息失败", e);
            throw new RuntimeException(e);
        }
    }
}
