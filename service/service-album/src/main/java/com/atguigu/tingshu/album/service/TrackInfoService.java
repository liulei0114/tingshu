package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface TrackInfoService extends IService<TrackInfo> {

    void saveTrackInfo(TrackInfoVo trackInfoVo, Long userId);

    void saveTrackStat(Long trackId, String statType, int statNum);

    Page<TrackListVo> getUserTrackPage(Page<TrackListVo> pageInfo, TrackInfoQuery trackInfoQuery);

    void updateTrackInfo(TrackInfo trackInfo);

    void removeTrackInfo(Long id);

    IPage<AlbumTrackListVo> findAlbumTrackPage(IPage<AlbumTrackListVo> pageInfo, Long albumId, Long userId);

    void updateTrackStat(TrackStatMqVo mqVo);

    TrackStatVo getTrackStatVo(Long trackId);

    List<TrackInfo> findWaitBuyTrackInfoList(Long userId, Long trackId, Integer trackCount);
}
