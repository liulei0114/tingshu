package com.atguigu.tingshu.user.service;

import com.atguigu.tingshu.vo.user.UserPaidRecordVo;

public interface DeliveryStrategy {

    /**
     * 虚拟物品"发货"抽象方法
     * @param userPaidRecordVo
     */
    void delivery(UserPaidRecordVo userPaidRecordVo);

}
