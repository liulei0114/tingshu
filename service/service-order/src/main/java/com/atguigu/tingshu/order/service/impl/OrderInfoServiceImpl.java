package com.atguigu.tingshu.order.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.order.OrderDerate;
import com.atguigu.tingshu.model.order.OrderDetail;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.order.helper.SignHelper;
import com.atguigu.tingshu.order.mapper.OrderInfoMapper;
import com.atguigu.tingshu.order.service.OrderInfoService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.order.OrderDerateVo;
import com.atguigu.tingshu.vo.order.OrderDetailVo;
import com.atguigu.tingshu.vo.order.OrderInfoVo;
import com.atguigu.tingshu.vo.order.TradeVo;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public OrderInfoVo trade(TradeVo tradeVo) {
        OrderInfoVo orderInfo = new OrderInfoVo();
        List<OrderDetailVo> orderDetailList = new ArrayList();
        List<OrderDerateVo> orderDerateList = new ArrayList();
        String itemType = tradeVo.getItemType();
        if (SystemConstant.ORDER_ITEM_TYPE_VIP.equals(itemType)) {
            // 购买vip
            Result<VipServiceConfig> vipServiceConfigResult = userFeignClient.getVipServiceConfig(tradeVo.getItemId());
            if (vipServiceConfigResult == null || vipServiceConfigResult.getCode() != 200) {
                log.error("远程调用【用户服务（getVipServiceConfig）】失败,失败itemId:{}", tradeVo.getItemId());
                String message = vipServiceConfigResult != null ? vipServiceConfigResult.getMessage() : "";
                throw new GuiguException(500, "远程调用【用户服务（getVipServiceConfig）】失败," + message);
            }
            VipServiceConfig vipServiceConfig = vipServiceConfigResult.getData();
            // 原始价格
            BigDecimal originalAmount = vipServiceConfig.getPrice();
            // 折后价格
            BigDecimal orderAmount = vipServiceConfig.getDiscountPrice();
            // 填充订单价格信息
            orderInfo = this.buildOrderInfoPrice(orderInfo, originalAmount, orderAmount);
            // 构建订单详情
            OrderDetailVo orderDetail = new OrderDetailVo();
            orderDetail.setItemId(tradeVo.getItemId());
            orderDetail.setItemName("会员套餐：" + vipServiceConfig.getName());
            orderDetail.setItemUrl(vipServiceConfig.getImageUrl());
            orderDetail.setItemPrice(orderAmount);
            orderDetailList.add(orderDetail);
            if (originalAmount.compareTo(orderAmount) == 1) {
                // 构建订单优惠信息
                OrderDerateVo orderDerate = new OrderDerateVo();
                orderDerate.setDerateType(SystemConstant.ORDER_DERATE_VIP_SERVICE_DISCOUNT);
                orderDerate.setDerateAmount(orderInfo.getDerateAmount());
                orderDerate.setRemarks("套餐限时减免：" + orderInfo.getDerateAmount());
                orderDerateList.add(orderDerate);
            }

        } else if (SystemConstant.ORDER_ITEM_TYPE_ALBUM.equals(itemType)) {
            // TODO: 专辑购买
        } else if (SystemConstant.ORDER_ITEM_TYPE_TRACK.equals(itemType)) {
            // TODO: 声音购买
        } else {
            log.error("不支持的订单类型:{}", itemType);
            throw new GuiguException(500, "不支持的订单类型");
        }

        orderInfo.setOrderDetailVoList(orderDetailList);
        orderInfo.setOrderDerateVoList(orderDerateList);
        orderInfo.setItemType(itemType);
        // 5.3.2 流水号机制第一步：生成本次订单流水号，解决回退、重复点击提交造成订单重复提交问题
        // 5.3.2.1. 构建当前用户本次订单流水号Key
        Long userId = AuthContextHolder.getUserId();
        String tradeKey = RedisConstant.ORDER_TRADE_NO_PREFIX + userId;

        //5.3.2.2. 构建当前用户本次订单流水号UUID值
        String tradeNo = IdUtil.randomUUID();
        //5.3.2.3. 写入Redis有效时间5分钟
        redisTemplate.opsForValue().set(tradeKey, tradeNo, 5, TimeUnit.MINUTES);
        orderInfo.setTradeNo(tradeNo);
        //5.3.3 签名机制
        //5.3.3.1. 生成本次订单时间戳
        long timeMillis = System.currentTimeMillis();
        orderInfo.setTimestamp(timeMillis);
        //5.3.3.2 将订单VO转为Map，生成本次订单签名值 TODO 由于付款方式属性payWay为null不需要参与签名
        Map<String, Object> orderInfoMap = BeanUtil.beanToMap(orderInfo, false, true);
        String sign = SignHelper.getSign(orderInfoMap);
        //5.3.3.3. 封装Vo中签名值
        orderInfo.setSign(sign);
        //6.响应订单vo，客户端完成订单页面渲染
        return orderInfo;
    }

    public OrderInfoVo buildOrderInfoPrice(OrderInfoVo orderInfo, BigDecimal originalAmount, BigDecimal orderAmount) {
        BigDecimal derateAmount = new BigDecimal("0.00");
        if (originalAmount.compareTo(orderAmount) == 1) {
            derateAmount = originalAmount.subtract(orderAmount);
        }
        orderInfo.setOriginalAmount(originalAmount);
        orderInfo.setDerateAmount(derateAmount);
        orderInfo.setOrderAmount(orderAmount);
        return orderInfo;
    }
}
