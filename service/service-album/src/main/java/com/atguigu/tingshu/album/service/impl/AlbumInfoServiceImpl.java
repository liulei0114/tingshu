package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.atguigu.tingshu.album.mapper.AlbumAttributeValueMapper;
import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.AlbumStatMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.AlbumStat;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class AlbumInfoServiceImpl extends ServiceImpl<AlbumInfoMapper, AlbumInfo> implements AlbumInfoService {

    @Autowired
    private AlbumInfoMapper albumInfoMapper;

    @Autowired
    private AlbumAttributeValueMapper albumAttributeValueMapper;

    @Autowired
    private AlbumStatMapper albumStatMapper;

    @Autowired
    private TrackInfoMapper trackInfoMapper;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    @Override
    public Long saveAlbumInfo(AlbumInfoVo albumInfoVo) {
        AlbumInfo albumInfo = BeanUtil.copyProperties(albumInfoVo, AlbumInfo.class);
        // TODO: 替换成当前登录用户id
        albumInfo.setUserId(1L);
        if (SystemConstant.ALBUM_PAY_TYPE_VIPFREE.equals(albumInfo.getPayType()) || SystemConstant.ALBUM_PAY_TYPE_REQUIRE.equals(albumInfo.getPayType())) {
            // 只需要对VIP免费或付费资源设置试听集
            albumInfo.setTracksForFree(3);
        }
        albumInfo.setStatus(SystemConstant.ALBUM_STATUS_NO_PASS);
        // 保存专辑
        albumInfoMapper.insert(albumInfo);
        log.info("albumInfo.getId() = {}", albumInfo.getId());
        // 返回专辑id
        Long albumId = albumInfo.getId();
        // 保存专辑属性值
        buildAlbumAttributeValueList(albumId, albumInfoVo);

        // 初始化统计
        albumStatMapper.insert(new AlbumStat(albumId, SystemConstant.ALBUM_STAT_PLAY, 0));
        albumStatMapper.insert(new AlbumStat(albumId, SystemConstant.ALBUM_STAT_SUBSCRIBE, 0));
        albumStatMapper.insert(new AlbumStat(albumId, SystemConstant.ALBUM_STAT_BUY, 0));
        albumStatMapper.insert(new AlbumStat(albumId, SystemConstant.ALBUM_STAT_COMMENT, 0));
        // 4.TODO 对专辑中文本内容进行审核  & 索引库ES中新增记录
        return albumId;
    }

    @Override
    public Page<AlbumListVo> findUserAlbumPage(Page<AlbumListVo> pageParam, AlbumInfoQuery albumInfoQuery) {
        Page<AlbumListVo> page = albumInfoMapper.selectAlbumListVoPage(pageParam, albumInfoQuery);
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeAlbumInfo(Long id) {
        Long count = trackInfoMapper.selectCount(new LambdaQueryWrapper<TrackInfo>().eq(TrackInfo::getAlbumId, id));
        if (count > 0) {
            throw new GuiguException(500, "该专辑下存在关联声音");
        }
        albumInfoMapper.deleteById(id);
        albumAttributeValueMapper.delete(new LambdaQueryWrapper<AlbumAttributeValue>().eq(AlbumAttributeValue::getAlbumId, id));
        albumStatMapper.delete(new LambdaQueryWrapper<AlbumStat>().eq(AlbumStat::getAlbumId, id));
        // 5.TODO 基于MQ删除存在Elasticsearch（全文搜索引擎）中数据
    }

    @Override
    public AlbumInfo getAlbumInfo(Long id) {
        AlbumInfo albumInfo = albumInfoMapper.selectById(id);
        List<AlbumAttributeValue> albumAttributeValues = albumAttributeValueMapper.selectAlbumAttributeValueByAlbumId(id);
        albumInfo.setAlbumAttributeValueVoList(albumAttributeValues);
        return albumInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAlbumInfo(Long id, AlbumInfoVo albumInfoVo) {
        AlbumInfo albumInfo = BeanUtil.copyProperties(albumInfoVo, AlbumInfo.class);
        albumInfo.setId(id);
        albumInfo.setStatus(SystemConstant.ALBUM_STATUS_NO_PASS);
        albumInfoMapper.updateById(albumInfo);
        // 物理删除标签
        albumAttributeValueMapper.deleteByAlbumId(id);
        // 重新关联标签
        buildAlbumAttributeValueList(id, albumInfoVo);

    }

    public List<AlbumAttributeValue> buildAlbumAttributeValueList(Long albumId, AlbumInfoVo albumInfoVo) {
        // 保存专辑属性值
        List<AlbumAttributeValue> albumAttributeValueList = new ArrayList<>();
        albumInfoVo.getAlbumAttributeValueVoList().forEach(albumAttributeValueVo -> {
            AlbumAttributeValue albumAttributeValue = new AlbumAttributeValue();
            albumAttributeValue.setAlbumId(albumId);
            albumAttributeValue.setAttributeId(albumAttributeValueVo.getAttributeId());
            albumAttributeValue.setValueId(albumAttributeValueVo.getValueId());
            albumAttributeValueList.add(albumAttributeValue);
        });
        if (CollUtil.isNotEmpty(albumAttributeValueList)) {
            albumAttributeValueMapper.insertBatch(albumAttributeValueList);
        }
        return albumAttributeValueList;
    }
}
