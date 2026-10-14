package com.atguigu.tingshu.order.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import com.atguigu.tingshu.account.AccountFeignClient;
import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.order.OrderDerate;
import com.atguigu.tingshu.model.order.OrderDetail;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.order.helper.SignHelper;
import com.atguigu.tingshu.order.mapper.OrderDerateMapper;
import com.atguigu.tingshu.order.mapper.OrderDetailMapper;
import com.atguigu.tingshu.order.mapper.OrderInfoMapper;
import com.atguigu.tingshu.order.service.OrderDetailService;
import com.atguigu.tingshu.order.service.OrderInfoService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.account.AccountDeductVo;
import com.atguigu.tingshu.vo.order.OrderDerateVo;
import com.atguigu.tingshu.vo.order.OrderDetailVo;
import com.atguigu.tingshu.vo.order.OrderInfoVo;
import com.atguigu.tingshu.vo.order.TradeVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Autowired
    private AlbumFeignClient albumFeignClient;

    @Autowired
    private OrderDerateMapper orderDerateMapper;

    @Autowired
    private OrderDetailService orderDetailService;

    @Autowired
    private AccountFeignClient accountFeignClient;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Override
    public OrderInfoVo trade(TradeVo tradeVo) {
        OrderInfoVo orderInfo = new OrderInfoVo();
        List<OrderDetailVo> orderDetailList = new ArrayList();
        List<OrderDerateVo> orderDerateList = new ArrayList();
        Long userId = AuthContextHolder.getUserId();
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

        }
        if (SystemConstant.ORDER_ITEM_TYPE_ALBUM.equals(itemType)) {
            Boolean flag = userFeignClient.isPaidAlbum(tradeVo.getItemId()).getData();
            if (flag) {
                throw new GuiguException(500, "用户已购买专辑");
            }
            // 获取专辑信息
            Result<AlbumInfo> albumInfoResult = albumFeignClient.getAlbumInfo(tradeVo.getItemId());
            if (albumInfoResult == null || albumInfoResult.getCode() != 200) {
                log.error("远程调用【专辑服务（getAlbumInfo）】失败,失败itemId:{}", tradeVo.getItemId());
                String message = albumInfoResult != null ? albumInfoResult.getMessage() : "";
                throw new GuiguException(500, "远程调用【专辑服务（getAlbumInfo）】失败," + message);
            }
            AlbumInfo albumInfo = albumInfoResult.getData();
            // 原始价格
            BigDecimal originalAmount = albumInfo.getPrice();
            // 普通折扣比例
            BigDecimal normalDiscount = albumInfo.getDiscount();
            // 会员折扣比例
            BigDecimal vipDiscount = albumInfo.getVipDiscount();
            // 订单价格
            BigDecimal orderAmount = originalAmount;
            // 查用户类型
            Result<UserInfoVo> userInfoVoResult = userFeignClient.getUserInfoVo(userId);
            if (userInfoVoResult == null || userInfoVoResult.getCode() != 200) {
                log.error("远程调用【用户服务（getUserInfoVo）】失败,失败userId:{}", userId);
                String message = userInfoVoResult != null ? userInfoVoResult.getMessage() : "";
                throw new GuiguException(500, "远程调用【用户服务（getUserInfoVo）】失败," + message);
            }
            UserInfoVo userInfoVo = userInfoVoResult.getData();
            if (userInfoVo.getIsVip() == 1 && vipDiscount.doubleValue() != -1) {
                orderAmount = originalAmount.multiply(vipDiscount).divide(new BigDecimal("10"), 2, BigDecimal.ROUND_HALF_UP);
            } else if (userInfoVo.getIsVip() == 0 && normalDiscount.doubleValue() != -1) {
                orderAmount = originalAmount.multiply(normalDiscount).divide(new BigDecimal("10"), 2, BigDecimal.ROUND_HALF_UP);
            }
            orderInfo = this.buildOrderInfoPrice(orderInfo, originalAmount, orderAmount);
            // 构建订单详情
            OrderDetailVo orderDetail = new OrderDetailVo();
            orderDetail.setItemId(tradeVo.getItemId());
            orderDetail.setItemName("专辑：" + albumInfo.getAlbumTitle());
            orderDetail.setItemUrl(albumInfo.getCoverUrl());
            orderDetail.setItemPrice(orderAmount);
            orderDetailList.add(orderDetail);
            if (originalAmount.compareTo(orderAmount) == 1) {
                // 构建订单优惠信息
                OrderDerateVo orderDerate = new OrderDerateVo();
                orderDerate.setDerateType(SystemConstant.ORDER_DERATE_ALBUM_DISCOUNT);
                orderDerate.setDerateAmount(orderInfo.getDerateAmount());
                orderDerate.setRemarks("套餐限时减免：" + orderInfo.getDerateAmount());
                orderDerateList.add(orderDerate);
            }

        }
        if (SystemConstant.ORDER_ITEM_TYPE_TRACK.equals(itemType)) {
            Result<List<TrackInfo>> waitBuyTrackInfoListResult = albumFeignClient.findWaitBuyTrackInfoList(tradeVo.getItemId(), tradeVo.getTrackCount());
            if (waitBuyTrackInfoListResult == null || waitBuyTrackInfoListResult.getCode() != 200) {
                log.error("远程调用【专辑服务（findWaitBuyTrackInfoList）】失败,失败itemId:{}", tradeVo.getItemId());
                String message = waitBuyTrackInfoListResult != null ? waitBuyTrackInfoListResult.getMessage() : "";
                throw new GuiguException(500, "远程调用【专辑服务（findWaitBuyTrackInfoList）】失败," + message);
            }
            List<TrackInfo> waitBuyTrackInfoList = waitBuyTrackInfoListResult.getData();
            if (CollUtil.isEmpty(waitBuyTrackInfoList)) {
                throw new GuiguException(500, "没有找到购买的声音");
            }
            // 获取专辑信息
            Result<AlbumInfo> albumInfoResult = albumFeignClient.getAlbumInfo(waitBuyTrackInfoList.get(0).getAlbumId());
            if (albumInfoResult == null || albumInfoResult.getCode() != 200) {
                log.error("远程调用【专辑服务（getAlbumInfo）】失败,失败itemId:{}", tradeVo.getItemId());
                String message = albumInfoResult != null ? albumInfoResult.getMessage() : "";
                throw new GuiguException(500, "远程调用【专辑服务（getAlbumInfo）】失败," + message);
            }
            AlbumInfo albumInfo = albumInfoResult.getData();

            BigDecimal originalAmount = albumInfo.getPrice().multiply(new BigDecimal(waitBuyTrackInfoList.size()));
            // 声音没有折扣
            BigDecimal derateAmount = new BigDecimal("0.00");
            BigDecimal orderAmount = originalAmount;
            orderInfo = this.buildOrderInfoPrice(orderInfo, originalAmount, orderAmount);
            for (TrackInfo trackInfo : waitBuyTrackInfoList) {
                OrderDetailVo orderDetail = new OrderDetailVo();
                orderDetail.setItemId(trackInfo.getId());
                orderDetail.setItemName("声音：" + trackInfo.getTrackTitle());
                orderDetail.setItemUrl(trackInfo.getCoverUrl());
                orderDetail.setItemPrice(albumInfo.getPrice());
                orderDetailList.add(orderDetail);
            }
        }

        orderInfo.setOrderDetailVoList(orderDetailList);
        orderInfo.setOrderDerateVoList(orderDerateList);
        orderInfo.setItemType(itemType);
        // 5.3.2 流水号机制第一步：生成本次订单流水号，解决回退、重复点击提交造成订单重复提交问题
        // 5.3.2.1. 构建当前用户本次订单流水号Key
        String tradeKey = RedisConstant.ORDER_TRADE_NO_PREFIX + userId;

        // 5.3.2.2. 构建当前用户本次订单流水号UUID值
        String tradeNo = IdUtil.randomUUID();
        // 5.3.2.3. 写入Redis有效时间5分钟
        redisTemplate.opsForValue().set(tradeKey, tradeNo, 5, TimeUnit.MINUTES);
        orderInfo.setTradeNo(tradeNo);
        // 5.3.3 签名机制
        // 5.3.3.1. 生成本次订单时间戳
        long timeMillis = System.currentTimeMillis();
        orderInfo.setTimestamp(timeMillis);
        // 5.3.3.2 将订单VO转为Map，生成本次订单签名值 TODO 由于付款方式属性payWay为null不需要参与签名
        Map<String, Object> orderInfoMap = BeanUtil.beanToMap(orderInfo, false, true);
        String sign = SignHelper.getSign(orderInfoMap);
        // 5.3.3.3. 封装Vo中签名值
        orderInfo.setSign(sign);
        // 6.响应订单vo，客户端完成订单页面渲染
        return orderInfo;
    }

    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public Map<String, String> submitOrder(Long userId, OrderInfoVo orderInfoVo) {
        // 1.业务校验1，验证流水号，防止订单重复提交 采用Lua脚本判断删除原子性
        String tradeKey = RedisConstant.ORDER_TRADE_NO_PREFIX + userId;
        // KEYS[1]=流水号Key ARGV[1]=用户提交流水号值
        String script = "if redis.call(\"get\",KEYS[1]) == ARGV[1]\n" +
                "then\n" +
                "    return redis.call(\"del\",KEYS[1])\n" +
                "else\n" +
                "    return 0\n" +
                "end";
        DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>(script, Boolean.class);
        Boolean flag = (Boolean) redisTemplate.execute(redisScript, Arrays.asList(tradeKey), orderInfoVo.getTradeNo());
        if (!flag) {
            throw new GuiguException(500, "验证流水号失败");
        }

        // 2.业务校验2，验证签名,防止订单确认页中订单数据（商品、金额、数量）被篡改
        // 2.1 将参与签名订单VO转为Map，TODO 加签时付款方式属性payWay未参与签名
        Map<String, Object> orderInfoMap = BeanUtil.beanToMap(orderInfoVo);
        // 2.2 手动删除掉签名属性payWay
        orderInfoMap.remove("payWay");
        // 2.3 验签
        SignHelper.checkSign(orderInfoMap);
        // 保存订单信息
        OrderInfo orderInfo = this.saveOrderInfo(orderInfoVo);
        // 支付方式：1101-微信 1102-支付宝 1103-账户余额
        String payWay = orderInfoVo.getPayWay();
        if (SystemConstant.ORDER_PAY_ACCOUNT.equals(payWay)) {
            // 4.1 远程调用"账户服务"，检查扣减余额
            // 4.1.1 构建扣减账户余额VO对象AccountDeductVo
            AccountDeductVo accountDeductVo = new AccountDeductVo();
            accountDeductVo.setOrderNo(orderInfo.getOrderNo());
            accountDeductVo.setUserId(orderInfo.getUserId());
            accountDeductVo.setAmount(orderInfo.getOrderAmount());
            accountDeductVo.setContent(orderInfo.getOrderTitle());

            // 4.1.2 远程调用
            Result checkAndDeductResult = accountFeignClient.checkAndDeduct(accountDeductVo);
            if (checkAndDeductResult == null || checkAndDeductResult.getCode() != 200) {
                log.error("远程调用【账户服务（checkAndDeduct）】失败,失败orderNo:{}", orderInfo.getOrderNo());
                String message = checkAndDeductResult != null ? checkAndDeductResult.getMessage() : "";
                throw new GuiguException(500, "远程调用【账户服务（checkAndDeduct）】失败," + message);
            }
            // 扣款成功
            orderInfo.setOrderStatus(SystemConstant.ORDER_STATUS_PAID);
            orderInfoMapper.updateById(orderInfo);
            UserPaidRecordVo userPaidRecordVo = new UserPaidRecordVo();
            userPaidRecordVo.setOrderNo(orderInfo.getOrderNo());
            userPaidRecordVo.setUserId(orderInfo.getUserId());
            userPaidRecordVo.setItemType(orderInfo.getItemType());
            List<Long> itemIdList = orderInfoVo.getOrderDetailVoList().stream().map(orderDetailVo -> {
                return orderDetailVo.getItemId();
            }).toList();
            userPaidRecordVo.setItemIdList(itemIdList);
            // 保存购买记录
            Result savePaidRecordResult = userFeignClient.savePaidRecord(userPaidRecordVo);
            if (savePaidRecordResult == null || savePaidRecordResult.getCode() != 200) {
                log.error("远程调用【用户服务（savePaidRecord）】失败,失败orderNo:{}", orderInfo.getOrderNo());
                String message = savePaidRecordResult != null ? savePaidRecordResult.getMessage() : "";
                throw new GuiguException(500, "远程调用【用户服务（savePaidRecord）】失败," + message);
            }
        }
        // 5.处理付款方式为微信
        if (SystemConstant.ORDER_PAY_WAY_WEIXIN.equals(payWay)) {
            // 5.1 发送延迟关单消息，自动关闭超时未支付订单
            int delayTime = 60 * 15;
            rabbitService.sendDealyMessage(MqConst.EXCHANGE_CANCEL_ORDER, MqConst.ROUTING_CANCEL_ORDER, orderInfo.getId(), delayTime);
        }
        Map<String, String> map = new HashMap<>();
        map.put("orderNo", orderInfo.getOrderNo());
        return map;
    }

    @Override
    public OrderInfo saveOrderInfo(OrderInfoVo orderInfoVo) {
        OrderInfo orderInfo = BeanUtil.copyProperties(orderInfoVo, OrderInfo.class);
        List<OrderDetailVo> orderDetailListVo = orderInfoVo.getOrderDetailVoList();
        List<OrderDerateVo> orderDerateListVo = orderInfoVo.getOrderDerateVoList();
        String orderNo = DateUtil.today().replace("-", "") + IdUtil.getSnowflake().nextId();
        orderInfo.setUserId(AuthContextHolder.getUserId());
        orderInfo.setOrderNo(orderNo);
        orderInfo.setOrderStatus(SystemConstant.ORDER_STATUS_UNPAID);
        if (CollUtil.isNotEmpty(orderDetailListVo)) {
            OrderDetailVo orderDetailVo = orderDetailListVo.get(0);
            orderInfo.setOrderTitle(orderDetailVo.getItemName());
        }
        orderInfoMapper.insert(orderInfo);
        Long orderId = orderInfo.getId();

        // 2.封装订单明细对象,保存订单明细
        if (CollUtil.isNotEmpty(orderDetailListVo)) {
            List<OrderDetail> orderDetailList = orderDetailListVo
                    .stream()
                    .map(orderDetailVo -> {
                        OrderDetail orderDetail = BeanUtil.copyProperties(orderDetailVo, OrderDetail.class);
                        // 将订单明细关联订单ID
                        orderDetail.setOrderId(orderId);
                        return orderDetail;
                    }).collect(Collectors.toList());
            orderDetailService.saveBatch(orderDetailList);
        }
        // 3.封装订单优惠对象,保存订单优惠明细
        if (CollUtil.isNotEmpty(orderDerateListVo)) {
            orderDerateListVo.stream().forEach(orderDerateVo -> {
                OrderDerate orderDerate = BeanUtil.copyProperties(orderDerateVo, OrderDerate.class);
                orderDerate.setOrderId(orderId);
                orderDerateMapper.insert(orderDerate);
            });
        }
        return orderInfo;

    }

    @Override
    public OrderInfo getOrderInfo(String orderNo) {
        //1.根据订单编号查询订单信息
        LambdaUpdateWrapper<OrderInfo> orderInfoLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        orderInfoLambdaUpdateWrapper.eq(OrderInfo::getOrderNo, orderNo);
        OrderInfo orderInfo = orderInfoMapper.selectOne(orderInfoLambdaUpdateWrapper);
        if (orderInfo != null) {

            //2.根据订单ID查询订单明细列表
            LambdaUpdateWrapper<OrderDetail> orderDetailLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
            orderDetailLambdaUpdateWrapper.eq(OrderDetail::getOrderId, orderInfo.getId());
            List<OrderDetail> orderDetailList = orderDetailMapper.selectList(orderDetailLambdaUpdateWrapper);
            if (CollectionUtil.isNotEmpty(orderDetailList)) {
                orderInfo.setOrderDetailList(orderDetailList);
            }
            //3.根据订单ID查询订单优惠列表
            LambdaUpdateWrapper<OrderDerate> orderDerateLambdaUpdateWrapper = new LambdaUpdateWrapper<>();
            orderDerateLambdaUpdateWrapper.eq(OrderDerate::getOrderId, orderInfo.getId());
            List<OrderDerate> orderDerateList = orderDerateMapper.selectList(orderDerateLambdaUpdateWrapper);
            if (CollectionUtil.isNotEmpty(orderDerateList)) {
                orderInfo.setOrderDerateList(orderDerateList);
            }
        }
        return orderInfo;
    }

    @Override
    public Page<OrderInfo> getOrderByPage(Long userId, Page<OrderInfo> pageInfo) {
        pageInfo = orderInfoMapper.getOrderByPage(pageInfo, userId);
        return pageInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long orderId) {
        //1.根据订单ID查询订单信息
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        //2.判读支付状态 ：如果是未支付，则取消订单
        if (SystemConstant.ORDER_STATUS_UNPAID.equals(orderInfo.getOrderStatus())) {
            //2.1 设置订单状态为已取消
            orderInfo.setOrderStatus(SystemConstant.ORDER_STATUS_CANCEL);
            //2.2 更新订单信息
            orderInfoMapper.updateById(orderInfo);
        }
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
