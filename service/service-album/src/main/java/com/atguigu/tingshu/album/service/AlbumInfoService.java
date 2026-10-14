package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

public interface AlbumInfoService extends IService<AlbumInfo> {


    Long saveAlbumInfo(AlbumInfoVo albumInfoVo);

    Page<AlbumListVo> findUserAlbumPage(Page<AlbumListVo> pageParam, AlbumInfoQuery albumInfoQuery);

    void removeAlbumInfo(Long id);

    AlbumInfo getAlbumInfo(Long id);

    void updateAlbumInfo(Long id, AlbumInfoVo albumInfoVo);

    List<AlbumInfo> findUserAllAlbum(Long userId);

    AlbumStatVo getAlbumStatVo(Long albumId);

    AlbumInfo getAlbumInfoFromDbAndCache(Long id);

    List<Map<String, Object>> getUserTrackPaidList(Long userId, Long trackId);
}
