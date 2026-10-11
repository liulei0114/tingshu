package com.atguigu.tingshu.search.service.impl;

import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.search.service.ItemService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class ItemServiceImpl implements ItemService {

    @Autowired
    private AlbumFeignClient albumFeignClient;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Override
    public Map<String, Object> getItem(Long albumId) {
        Map<String, Object> result = new HashMap<>();
        // 任务编排
        CompletableFuture<AlbumInfo> albumInfoTask = CompletableFuture.supplyAsync(() -> {
            // - **albumInfo**：当前专辑信息
            Result<AlbumInfo> albumInfoResult = albumFeignClient.getAlbumInfo(albumId);
            if (albumInfoResult == null || albumInfoResult.getCode() != 200) {
                log.error("远程调用【专辑服务（getAlbumInfo）】失败,失败albumId:{}", albumId);
                String message = albumInfoResult != null ? albumInfoResult.getMessage() : "";
                throw new GuiguException(500, "远程调用【专辑服务（getAlbumInfo）】失败," + message);
            }
            AlbumInfo albumInfo = albumInfoResult.getData();
            result.put("albumInfo", albumInfo);
            return albumInfo;
        }, taskExecutor);

        CompletableFuture<Void> albumStatVoTask = CompletableFuture.runAsync(() -> {
            // - **albumStatVo**：专辑统计信息
            Result<AlbumStatVo> albumStatVoResult = albumFeignClient.getAlbumStatVo(albumId);
            if (albumStatVoResult == null || albumStatVoResult.getCode() != 200) {
                log.error("远程调用【专辑服务（getAlbumStatVo）】失败,失败albumId:{}", albumId);
                String message = albumStatVoResult != null ? albumStatVoResult.getMessage() : "";
                throw new GuiguException(500, "远程调用【专辑服务（getAlbumStatVo）】失败," + message);
            }
            AlbumStatVo albumStatVo = albumStatVoResult.getData();
            result.put("albumStatVo", albumStatVo);
        }, taskExecutor);

        CompletableFuture<Void> categoryViewTask = albumInfoTask.thenAcceptAsync((albumInfo) -> {
            // - **baseCategoryView**：专辑分类信息
            Result<BaseCategoryView> categoryViewResult = albumFeignClient.getCategoryView(albumInfo.getCategory3Id());
            if (categoryViewResult == null || categoryViewResult.getCode() != 200) {
                log.error("远程调用【专辑服务（getCategoryView）】失败,失败albumId:{}", albumId);
                String message = categoryViewResult != null ? categoryViewResult.getMessage() : "";
                throw new GuiguException(500, "远程调用【专辑服务（getCategoryView）】失败," + message);
            }
            BaseCategoryView baseCategoryView = categoryViewResult.getData();
            result.put("baseCategoryView", baseCategoryView);
        }, taskExecutor);


        CompletableFuture<Void> announcerTask = albumInfoTask.thenAcceptAsync((albumInfo) -> {
            // - **announcer**：专辑主播信息
            Result<UserInfoVo> userInfoVoResult = userFeignClient.getUserInfoVo(albumInfo.getUserId());
            if (userInfoVoResult == null || userInfoVoResult.getCode() != 200) {
                log.error("远程调用【用户服务（getUserInfoVo）】失败,失败userId:{}", albumInfo.getUserId());
                String message = userInfoVoResult != null ? userInfoVoResult.getMessage() : "";
                throw new GuiguException(500, "远程调用【用户服务（getUserInfoVo）】失败," + message);
            }
            UserInfoVo data = userInfoVoResult.getData();
            result.put("announcer", data);
        }, taskExecutor);

        CompletableFuture.allOf(albumInfoTask, albumStatVoTask, categoryViewTask, announcerTask).join();

        return result;
    }
}
