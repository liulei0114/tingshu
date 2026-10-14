package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.model.user.UserPaidAlbum;
import com.atguigu.tingshu.user.mapper.UserPaidAlbumMapper;
import com.atguigu.tingshu.user.service.DeliveryStrategy;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component(SystemConstant.ORDER_ITEM_TYPE_ALBUM)
public class AlbumDeliveryImpl implements DeliveryStrategy {

    @Autowired
    private UserPaidAlbumMapper userPaidAlbumMapper;

    /**
     * 虚拟物品-专辑-发货行为实现
     *
     * @param userPaidRecordVo
     */
    @Override
    public void delivery(UserPaidRecordVo userPaidRecordVo) {
        // 1.1 根据订单编号查询已购专辑记录，如果存在，则返回
        Long count = userPaidAlbumMapper.selectCount(
                new LambdaQueryWrapper<UserPaidAlbum>()
                        .eq(UserPaidAlbum::getOrderNo, userPaidRecordVo.getOrderNo())
        );
        if (count > 0) {
            log.info("已存在该订单:{}的已购专辑记录", userPaidRecordVo.getOrderNo());
            return;
        }
        // 1.2 如果不存在，则构建专辑已购记录完成保存
        UserPaidAlbum userPaidAlbum = new UserPaidAlbum();
        userPaidAlbum.setOrderNo(userPaidRecordVo.getOrderNo());
        userPaidAlbum.setUserId(userPaidRecordVo.getUserId());
        userPaidAlbum.setAlbumId(userPaidRecordVo.getItemIdList().get(0));
        userPaidAlbumMapper.insert(userPaidAlbum);
    }
}
