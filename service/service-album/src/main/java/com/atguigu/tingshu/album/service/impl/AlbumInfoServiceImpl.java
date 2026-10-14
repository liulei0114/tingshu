package com.atguigu.tingshu.album.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.atguigu.tingshu.album.mapper.AlbumAttributeValueMapper;
import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.AlbumStatMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.album.service.AuditService;
import com.atguigu.tingshu.common.cache.RedissonCache;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.AlbumStat;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Autowired
    private AuditService auditService;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private UserFeignClient userFeignClient;

    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    @Override
    public Long saveAlbumInfo(AlbumInfoVo albumInfoVo) {
        AlbumInfo albumInfo = BeanUtil.copyProperties(albumInfoVo, AlbumInfo.class);
        albumInfo.setUserId(AuthContextHolder.getUserId());
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
        String text = albumInfo.getAlbumTitle() + albumInfo.getAlbumIntro();
        String suggestion = auditService.auditText(text);
        log.info("腾讯云审核意见 = {}", suggestion);
        if ("pass".equals(suggestion)) {
            albumInfo.setStatus(SystemConstant.ALBUM_STATUS_PASS);
            albumInfoMapper.updateById(albumInfo);
            rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_UPPER, albumInfo.getId());
            return albumInfo.getId();
        }
        rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_LOWER, albumInfo.getId());
        throw new GuiguException(500, "专辑标题或内容存在违规！");

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
        rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_LOWER, id);
    }

    @Override
    public AlbumInfo getAlbumInfo(Long id) {
        // 从redis 里面获取
        String redisKey = RedisConstant.ALBUM_INFO_PREFIX + id;

        Object o = redisTemplate.opsForValue().get(redisKey);
        if (o != null) {
            return (AlbumInfo) o;
        }
        String lockKey = redisKey + RedisConstant.CACHE_LOCK_SUFFIX;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            // 获取锁
            boolean isLocked = lock.tryLock();
            if (!isLocked) {
                // 6.获取分布式锁失败，进行自旋（自旋可能获取锁成功线程会将业务数据已经放入缓存）
                // 获取锁失败
                return this.getAlbumInfo(id);
            }
            // 获取锁成功
            AlbumInfo albumInfo = albumInfoMapper.selectById(id);
            if (albumInfo == null) {
                throw new GuiguException(500, "专辑不存在");
            }
            List<AlbumAttributeValue> albumAttributeValues = albumAttributeValueMapper.selectAlbumAttributeValueByAlbumId(id);
            albumInfo.setAlbumAttributeValueVoList(albumAttributeValues);
            int ttl = RandomUtil.randomInt(100, 600);
            redisTemplate.opsForValue().set(redisKey, albumInfo, RedisConstant.ALBUM_TIMEOUT + ttl, TimeUnit.MINUTES);
            return albumInfo;
        } catch (Exception e) {
            log.error("获取锁失败", e);
            return this.getAlbumInfoFromDbAndCache(id);
        } finally {
            // 释放锁
            lock.unlock();
        }

    }

    @Override
    public AlbumInfo getAlbumInfoFromDbAndCache(Long id) {
        AlbumInfo albumInfo = albumInfoMapper.selectById(id);
        if (albumInfo == null) {
            throw new GuiguException(500, "专辑不存在");
        }
        List<AlbumAttributeValue> albumAttributeValues = albumAttributeValueMapper.selectAlbumAttributeValueByAlbumId(id);
        albumInfo.setAlbumAttributeValueVoList(albumAttributeValues);
        return albumInfo;
    }

    @Override
    public List<Map<String, Object>> getUserTrackPaidList(Long userId, Long trackId) {
        // 1.根据选择购买声音ID查询声音记录，得到声音序号，得到专辑ID
        TrackInfo trackInfo = trackInfoMapper.selectById(trackId);
        Integer orderNum = trackInfo.getOrderNum();
        Long albumId = trackInfo.getAlbumId();
        // 2.根据"起始声音"需要+专辑ID查询声音表得到“未购买声音列表”，这里可能包含已购买声音
        List<TrackInfo> waitBuyTrackList = trackInfoMapper.selectList(
                new LambdaQueryWrapper<TrackInfo>()
                        .eq(TrackInfo::getAlbumId, albumId)
                        .ge(TrackInfo::getOrderNum, orderNum)
                        .select(TrackInfo::getId)
                        .orderByAsc(TrackInfo::getOrderNum)
        );
        // 3.远程调用用户服务，得到用户已购声音ID列表，如果有值将已购买声音ID排除掉
        List<Long> userPaidTrackIdList = userFeignClient.findUserPaidTrackList(albumId).getData();
        if (CollUtil.isNotEmpty(userPaidTrackIdList)) {
            waitBuyTrackList =
                    waitBuyTrackList.stream()
                            .filter(t -> !userPaidTrackIdList.contains(t.getId()))
                            .collect(Collectors.toList());
        }
        AlbumInfo albumInfo = albumInfoMapper.selectById(albumId);
        BigDecimal price = albumInfo.getPrice();
        List<Map<String, Object>> result = new ArrayList<>();
        // 放入本集
        Map<String, Object> currentTrack = new HashMap<>();
        currentTrack.put("price", price);
        currentTrack.put("name", "本集");
        currentTrack.put("trackCount", 1);
        result.add(currentTrack);
        Integer size = waitBuyTrackList.size();
        for (Integer i = 10; i <= 50; i += 10) {
            Integer trackCount = Math.min(i, size);
            String totalPrice = price.multiply(new BigDecimal(trackCount)).setScale(2, BigDecimal.ROUND_HALF_UP).toString();
            Map<String, Object> track = new HashMap<>();
            track.put("price", totalPrice);
            track.put("name", "后" + trackCount + "集");
            track.put("trackCount", trackCount);
            result.add(track);
        }
        return result;
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

        String text = albumInfo.getAlbumTitle() + albumInfo.getAlbumIntro();
        String suggestion = auditService.auditText(text);
        log.info("腾讯云审核意见 = {}", suggestion);
        if ("pass".equals(suggestion)) {
            albumInfo.setStatus(SystemConstant.ALBUM_STATUS_PASS);
            albumInfoMapper.updateById(albumInfo);
            rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_UPPER, albumInfo.getId());
            return;
        }
        rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_LOWER, albumInfo.getId());
        throw new GuiguException(500, "专辑标题或内容存在违规！");
    }

    @Override
    public List<AlbumInfo> findUserAllAlbum(Long userId) {
        LambdaQueryWrapper<AlbumInfo> lambdaQueryWrapper = new LambdaQueryWrapper<>();
        lambdaQueryWrapper.select(AlbumInfo::getId, AlbumInfo::getAlbumTitle).eq(AlbumInfo::getUserId, userId).orderByDesc(AlbumInfo::getCreateTime).last("limit 200");
        List<AlbumInfo> albumInfoList = albumInfoMapper.selectList(lambdaQueryWrapper);
        return albumInfoList;
    }

    @Override
    @RedissonCache(prefix = RedisConstant.ALBUM_STAT_PREFIX)
    public AlbumStatVo getAlbumStatVo(Long albumId) {
        AlbumStatVo albumStatVo = albumStatMapper.selectAlbumStatVo(albumId);
        return albumStatVo;
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
