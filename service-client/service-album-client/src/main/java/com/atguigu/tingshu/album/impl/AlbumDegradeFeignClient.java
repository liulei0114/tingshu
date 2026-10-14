package com.atguigu.tingshu.album.impl;


import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.*;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.atguigu.tingshu.vo.album.CategoryInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class AlbumDegradeFeignClient implements AlbumFeignClient {


    @Override
    public Result<AlbumInfo> getAlbumInfo(Long id) {
        log.error("[专辑服务]提供远程调用方法getAlbumInfo执行服务降级, id: {}", id);
        return null;
    }

    @Override
    public Result<BaseCategoryView> getCategoryView(Long category3Id) {
        log.error("[专辑服务]提供远程调用方法getCategoryView执行服务降级, category3Id: {}", category3Id);
        return null;
    }

    @Override
    public Result<List<BaseCategory3>> findTopBaseCategory3(Long category1Id) {
        log.error("[专辑服务]提供远程调用方法findTopBaseCategory3执行服务降级, category1Id: {}", category1Id);
        return null;
    }

    @Override
    public Result<AlbumStatVo> getAlbumStatVo(Long albumId) {
        log.error("[专辑服务]提供远程调用方法getAlbumStatVo执行服务降级, albumId: {}", albumId);
        return null;
    }

    @Override
    public Result<TrackInfo> getTrackInfo(Long id) {
        log.error("[专辑服务]提供远程调用方法getTrackInfo执行服务降级, id: {}", id);
        return null;
    }

    @Override
    public Result<List<BaseCategory1>> getAllCategory1() {
        log.error("[专辑服务]提供远程调用方法getAllCategory1执行服务降级");
        return null;
    }

    @Override
    public Result<List<TrackInfo>> findWaitBuyTrackInfoList(Long trackId, Integer trackCount) {
        log.error("[专辑服务]提供远程调用方法findWaitBuyTrackInfoList执行服务降级, trackId: {}, trackCount: {}", trackId, trackCount);
        return null;
    }


}
