package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.AlbumStatMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackStatMapper;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.album.service.AuditService;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.AlbumStat;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.album.TrackStat;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.model.user.UserPaidTrack;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.album.*;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

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

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private AlbumInfoService albumInfoService;

    @Autowired
    private AlbumStatMapper albumStatMapper;


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

    @Override
    public IPage<AlbumTrackListVo> findAlbumTrackPage(IPage<AlbumTrackListVo> pageInfo, Long albumId, Long userId) {
        TrackInfoQuery trackInfoQuery = new TrackInfoQuery();
        trackInfoQuery.setAlbumId(albumId);
        trackInfoQuery.setUserId(userId);
        trackInfoQuery.setStatus(SystemConstant.TRACK_STATUS_PASS);

        Page<TrackListVo> trackListVoPage = new Page<TrackListVo>();
        trackListVoPage.setCurrent(pageInfo.getCurrent());
        trackListVoPage.setSize(pageInfo.getSize());

        Page<TrackListVo> trackList = trackInfoMapper.selectTrackInfoList2(trackListVoPage, trackInfoQuery);
        pageInfo.setTotal(trackList.getTotal());
        pageInfo.setPages(trackList.getPages());
        List<AlbumTrackListVo> list = trackList.getRecords().stream().map(trackListVo -> {
            return BeanUtil.copyProperties(trackListVo, AlbumTrackListVo.class);
        }).toList();
        /**
         * 1.用户是否登录，
         *   1.1 专辑免费，全部免费
         *   1.2 专辑不免费，前五免费，其余付费
         *
         * 2.登录了，vip逻辑
         *   2.1 是否是vip，专辑是否是vip免费
         *      2.1.1 是，全部免费
         * 3.付费逻辑
         *   3.1 查询用户专辑和声音购买记录，有则免费，没有则收费
         */
        // 获取专辑信息
        AlbumInfo albumInfo = albumInfoService.getAlbumInfo(albumId);

        // 获取前几之后的ids
        List<Long> ids = list.stream().filter(s -> s.getOrderNum() > albumInfo.getTracksForFree()).map(s -> s.getTrackId()).toList();

        // 没有登录，前几免费，其余付费
        if (userId == null) {
            list = setTrackPayStatus(list, true, ids);
            pageInfo.setRecords(list);
            return pageInfo;
        }

        // 免费
        if (SystemConstant.ALBUM_PAY_TYPE_FREE.equals(albumInfo.getPayType())) {
            list = setTrackPayStatus(list, false, null);
            pageInfo.setRecords(list);
            return pageInfo;
        }

        Result<List<AlbumTrackListVo>> listResult = userFeignClient.userIsPaidTrack(userId, albumId, list);
        if (listResult == null || listResult.getCode() != 200) {
            log.error("远程调用【专辑服务（userIsPaidTrack）】失败,失败albumId:{}", albumId);
            String message = listResult != null ? listResult.getMessage() : "";
            throw new GuiguException(500, "远程调用【专辑服务（userIsPaidTrack）】失败," + message);
        }
        pageInfo.setRecords(listResult.getData());
        return pageInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTrackStat(TrackStatMqVo mqVo) {
        // 1.更新声音统计数值
        trackStatMapper.update(
                null,
                new LambdaUpdateWrapper<TrackStat>()
                        .eq(TrackStat::getTrackId, mqVo.getTrackId())
                        .eq(TrackStat::getStatType, mqVo.getStatType())
                        .setSql("stat_num = stat_num + " + mqVo.getCount())
        );

        // 2.如果是"播放"or"评论"还需要更新专辑统计数值
        if (SystemConstant.TRACK_STAT_PLAY.equals(mqVo.getStatType())) {
            // 更新专辑统计表播放量
            albumStatMapper.update(
                    null,
                    new LambdaUpdateWrapper<AlbumStat>()
                            .eq(AlbumStat::getAlbumId, mqVo.getAlbumId())
                            .eq(AlbumStat::getStatType, SystemConstant.ALBUM_STAT_PLAY)
                            .setSql("stat_num = stat_num + " + mqVo.getCount())
            );
        }
        if (SystemConstant.TRACK_STAT_COMMENT.equals(mqVo.getStatType())) {
            // 更新专辑统计表评论量
            albumStatMapper.update(
                    null,
                    new LambdaUpdateWrapper<AlbumStat>()
                            .eq(AlbumStat::getAlbumId, mqVo.getAlbumId())
                            .eq(AlbumStat::getStatType, SystemConstant.ALBUM_STAT_COMMENT)
                            .setSql("stat_num = stat_num + " + mqVo.getCount())
            );
        }

    }

    @Override
    public TrackStatVo getTrackStatVo(Long trackId) {
        return trackInfoMapper.getTrackStatVo(trackId);

    }

    @Override
    public List<TrackInfo> findWaitBuyTrackInfoList(Long userId, Long trackId, Integer trackCount) {
        TrackInfo trackInfo = trackInfoMapper.selectById(trackId);
        if (trackInfo == null) {
            throw new GuiguException(500, "声音不存在");
        }
        Long albumId = trackInfo.getAlbumId();
        Integer orderNum = trackInfo.getOrderNum();
        // 查询专辑下所有声音
        LambdaQueryWrapper<TrackInfo> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(TrackInfo::getAlbumId, albumId).ge(TrackInfo::getOrderNum, orderNum).orderByAsc(TrackInfo::getOrderNum);
        // 查询用户已经购买过的声音
        Result<List<Long>> userPaidTrackListResult = userFeignClient.findUserPaidTrackList(albumId);
        if (userPaidTrackListResult == null || userPaidTrackListResult.getCode() != 200) {
            log.error("远程调用【用户服务（findUserPaidTrackList）】失败,失败albumId:{}", albumId);
            String message = userPaidTrackListResult != null ? userPaidTrackListResult.getMessage() : "";
            throw new GuiguException(500, "远程调用【用户服务（findUserPaidTrackList）】失败," + message);
        }
        List<Long> userPaidTrackList = userPaidTrackListResult.getData();
        if (CollUtil.isNotEmpty(userPaidTrackList)) {
            lambdaQueryWrapper.notIn(TrackInfo::getId, userPaidTrackList);
        }
        lambdaQueryWrapper.last("limit " + trackCount);
        List<TrackInfo> trackInfoList = trackInfoMapper.selectList(lambdaQueryWrapper);
        return trackInfoList;
    }

    public List<AlbumTrackListVo> setTrackPayStatus(List<AlbumTrackListVo> result, Boolean isShowPaidMark, List<Long> ids) {
        if (ids != null) {
            for (AlbumTrackListVo albumTrackListVo : result) {
                if (ids.contains(albumTrackListVo.getTrackId())) {
                    albumTrackListVo.setIsShowPaidMark(isShowPaidMark);
                }
            }
        } else {
            for (AlbumTrackListVo albumTrackListVo : result) {
                albumTrackListVo.setIsShowPaidMark(isShowPaidMark);
            }
        }
        return result;
    }


}
