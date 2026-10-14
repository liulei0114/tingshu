package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.UserPaidTrack;
import com.atguigu.tingshu.user.mapper.UserPaidTrackMapper;
import com.atguigu.tingshu.user.service.DeliveryStrategy;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component(SystemConstant.ORDER_ITEM_TYPE_TRACK)// 默认Bean的ID 为 albumDelivery
public class TrackDeliveryImpl implements DeliveryStrategy {
    @Autowired
    private UserPaidTrackMapper userPaidTrackMapper;

    @Autowired
    private AlbumFeignClient albumFeignClient;

    /**
     * 虚拟物品-声音-发货行为实现
     *
     * @param userPaidRecordVo
     */
    @Override
    public void delivery(UserPaidRecordVo userPaidRecordVo) {
        // 2. 处理虚拟物品"声音"发货逻辑
        // 2.1 根据订单编号查询已购声音记录，如果存在，则返回
        Long count = userPaidTrackMapper.selectCount(
                new LambdaQueryWrapper<UserPaidTrack>()
                        .eq(UserPaidTrack::getOrderNo, userPaidRecordVo.getOrderNo())
        );
        if (count > 0) {
            log.info("已存在该订单:{}的已购声音记录", userPaidRecordVo.getOrderNo());
            return;
        }
        // 2.2 如果不存在，则构建声音已购记录完成保存
        TrackInfo trackInfo = albumFeignClient.getTrackInfo(userPaidRecordVo.getItemIdList().get(0)).getData();
        List<Long> trackIdList = userPaidRecordVo.getItemIdList();
        for (Long trackId : trackIdList) {
            UserPaidTrack userPaidTrack = new UserPaidTrack();
            userPaidTrack.setOrderNo(userPaidRecordVo.getOrderNo());
            userPaidTrack.setUserId(userPaidRecordVo.getUserId());
            userPaidTrack.setAlbumId(trackInfo.getAlbumId());
            userPaidTrack.setTrackId(trackId);
            userPaidTrackMapper.insert(userPaidTrack);
        }
    }
}
