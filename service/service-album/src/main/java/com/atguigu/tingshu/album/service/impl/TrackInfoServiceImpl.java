package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackStatMapper;
import com.atguigu.tingshu.album.service.AuditService;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.album.TrackStat;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.atguigu.tingshu.vo.album.TrackMediaInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class TrackInfoServiceImpl extends ServiceImpl<TrackInfoMapper, TrackInfo> implements TrackInfoService {

    @Autowired
    private TrackInfoMapper trackInfoMapper;

    @Autowired
    private AlbumInfoMapper albumInfoMapper;

    @Autowired
    private VodService vodService;

    @Autowired
    private TrackStatMapper trackStatMapper;

    @Autowired
    private AuditService auditService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTrackInfo(TrackInfoVo trackInfoVo, Long userId) {
        // 校验专辑是否存在
        AlbumInfo albumInfo = albumInfoMapper.selectById(trackInfoVo.getAlbumId());
        if (albumInfo == null) {
            throw new GuiguException(500, "专辑不存在");
        }
        TrackInfo trackInfo = BeanUtil.copyProperties(trackInfoVo, TrackInfo.class);
        trackInfo.setUserId(userId);
        trackInfo.setOrderNum(albumInfo.getIncludeTrackCount() + 1);
        TrackMediaInfoVo trackMediaInfoVo = vodService.getTrackMediaInfo(trackInfo.getMediaFileId());
        if (trackMediaInfoVo != null) {
            trackInfo.setMediaDuration(BigDecimal.valueOf(trackMediaInfoVo.getDuration()));
            trackInfo.setMediaSize(trackMediaInfoVo.getSize());
            trackInfo.setMediaType(trackMediaInfoVo.getType());
        }
        // 2.2.4 来源：用户上传
        trackInfo.setSource(SystemConstant.TRACK_SOURCE_USER);
        // 2.2.5 状态：待审核
        trackInfo.setStatus(SystemConstant.TRACK_STATUS_NO_PASS);
        // 2.2.6 封面图片 如果未提交使用所属专辑封面
        String coverUrl = trackInfo.getCoverUrl();
        if (StrUtil.isBlank(coverUrl)) {
            trackInfo.setCoverUrl(albumInfo.getCoverUrl());
        }
        trackInfoMapper.insert(trackInfo);
        // 更新专辑信息
        albumInfo.setIncludeTrackCount(albumInfo.getIncludeTrackCount() + 1);
        albumInfoMapper.updateById(albumInfo);

        // 4.新增声音统计记录
        this.saveTrackStat(trackInfo.getId(), SystemConstant.TRACK_STAT_PLAY, 0);
        this.saveTrackStat(trackInfo.getId(), SystemConstant.TRACK_STAT_COLLECT, 0);
        this.saveTrackStat(trackInfo.getId(), SystemConstant.TRACK_STAT_PRAISE, 0);
        this.saveTrackStat(trackInfo.getId(), SystemConstant.TRACK_STAT_COMMENT, 0);

        String text = trackInfo.getTrackTitle() + trackInfo.getTrackIntro();
        String suggestion = auditService.auditText(text);
        log.info("腾讯云审核意见 = {}", suggestion);
        if ("pass".equals(suggestion)) {
            trackInfo.setStatus(SystemConstant.TRACK_STATUS_PASS);
        } else if ("review".equals(suggestion)) {
            trackInfo.setStatus(SystemConstant.TRACK_STATUS_REVIEWING);
        } else {
            trackInfo.setStatus(SystemConstant.TRACK_STATUS_NO_PASS);
        }
        // 6.对点播平台音频文件进行审核（异步审核）
        String reviewTaskId = auditService.startReviewTask(trackInfo.getMediaFileId());
        trackInfo.setStatus(SystemConstant.TRACK_STATUS_REVIEWING);
        trackInfo.setReviewTaskId(reviewTaskId);
        trackInfoMapper.updateById(trackInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTrackStat(Long trackId, String statType, int statNum) {
        TrackStat trackStat = new TrackStat();
        trackStat.setTrackId(trackId);
        trackStat.setStatType(statType);
        trackStat.setStatNum(statNum);
        trackStatMapper.insert(trackStat);
    }

    @Override
    public Page<TrackListVo> getUserTrackPage(Page<TrackListVo> pageInfo, TrackInfoQuery trackInfoQuery) {
        return trackInfoMapper.selectTrackInfoList(pageInfo, trackInfoQuery);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTrackInfo(TrackInfo trackInfo) {
        //     是否修改了声音
        TrackInfo trackInfoDb = trackInfoMapper.selectById(trackInfo.getId());
        if (StrUtil.isBlank(trackInfo.getMediaFileId())) {
            throw new GuiguException(500, "媒体文件ID为空");
        }
        String text = trackInfo.getTrackTitle() + trackInfo.getTrackIntro();
        String suggestion = auditService.auditText(text);
        log.info("腾讯云审核意见 = {}", suggestion);
        if ("pass".equals(suggestion)) {
            trackInfo.setStatus(SystemConstant.TRACK_STATUS_PASS);
        } else if ("review".equals(suggestion)) {
            trackInfo.setStatus(SystemConstant.TRACK_STATUS_REVIEWING);
        } else {
            trackInfo.setStatus(SystemConstant.TRACK_STATUS_NO_PASS);
        }
        if (!trackInfoDb.getMediaFileId().equals(trackInfo.getMediaFileId())) {
            // 修改了声音
            // 1.修改点播平台文件信息
            TrackMediaInfoVo trackMediaInfoVo = vodService.getTrackMediaInfo(trackInfo.getMediaFileId());
            // 2.修改数据库中媒体文件信息
            if (trackMediaInfoVo != null) {
                trackInfo.setMediaDuration(BigDecimal.valueOf(trackMediaInfoVo.getDuration()));
                trackInfo.setMediaSize(trackMediaInfoVo.getSize());
                trackInfo.setMediaType(trackMediaInfoVo.getType());
            }
            // 点播平台删除声音
            vodService.deleteTrack(trackInfoDb.getMediaFileId());
            String reviewTaskId = auditService.startReviewTask(trackInfo.getMediaFileId());
            trackInfo.setStatus(SystemConstant.TRACK_STATUS_REVIEWING);
            trackInfo.setReviewTaskId(reviewTaskId);
        }
        // 更新声音
        trackInfoMapper.updateById(trackInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTrackInfo(Long id) {
        // 获取声音信息
        TrackInfo trackInfo = trackInfoMapper.selectById(id);
        if (trackInfo == null) {
            throw new GuiguException(500, "声音不存在");
        }
        // 获取专辑声音
        AlbumInfo albumInfo = albumInfoMapper.selectById(trackInfo.getAlbumId());
        // 更新专辑信息中的声音数量
        albumInfo.setIncludeTrackCount(albumInfo.getIncludeTrackCount() - 1);
        albumInfoMapper.updateById(albumInfo);
        // 删除数据库中声音信息
        trackInfoMapper.deleteById(id);
        // 更新声音orderNum
        LambdaUpdateWrapper<TrackInfo> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        lambdaUpdateWrapper.eq(TrackInfo::getAlbumId, trackInfo.getAlbumId());
        lambdaUpdateWrapper.gt(TrackInfo::getOrderNum, trackInfo.getOrderNum());
        lambdaUpdateWrapper.setSql("order_num = order_num - 1");
        trackInfoMapper.update(null, lambdaUpdateWrapper);
        // 删除声音统计记录
        LambdaQueryWrapper<TrackStat> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(TrackStat::getTrackId, id);
        trackStatMapper.delete(lambdaQueryWrapper);
        // 删除点播平台文件
        vodService.deleteTrack(trackInfo.getMediaFileId());

    }
}
