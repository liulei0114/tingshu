package com.atguigu.tingshu.search.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.model.search.AttributeValueIndex;
import com.atguigu.tingshu.search.repository.AlbumInfoIndexRepository;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Slf4j
@Service
@SuppressWarnings({"all"})
public class SearchServiceImpl implements SearchService {

    @Autowired
    private AlbumFeignClient albumFeignClient;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private AlbumInfoIndexRepository albumInfoIndexRepository;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Override
    public void upperAlbum(Long albumId) {
        AlbumInfoIndex albumInfoIndex = new AlbumInfoIndex();

        // 改造成异步任务编排+线程池
        // 任务一：查询专辑信息
        CompletableFuture<AlbumInfo> completableFutureAlbumInfo = CompletableFuture.supplyAsync(() -> {
            // 查询专辑
            Result<AlbumInfo> albumInfo = albumFeignClient.getAlbumInfo(albumId);
            if (albumInfo == null || albumInfo.getCode() != 200) {
                log.error("远程调用【专辑服务（getAlbumInfo）】失败,失败albumId:{}", albumId);
                String message = albumInfo != null ? albumInfo.getMessage() : "";
                throw new GuiguException(500, "远程调用【专辑服务（getAlbumInfo）】失败," + message);
            }
            AlbumInfo albumInfoData = albumInfo.getData();
            // 构建es数据
            BeanUtil.copyProperties(albumInfoData, albumInfoIndex);
            if (CollUtil.isNotEmpty(albumInfoData.getAlbumAttributeValueVoList())) {
                List<AttributeValueIndex> attributeValueIndexList = albumInfoData.getAlbumAttributeValueVoList().stream().map(albumAttributeValueVo -> {
                    return new AttributeValueIndex(albumAttributeValueVo.getAttributeId(), albumAttributeValueVo.getValueId());
                }).collect(Collectors.toList());
                albumInfoIndex.setAttributeValueIndexList(attributeValueIndexList);
            }
            return albumInfoData;
        }, taskExecutor);

        // 任务二：查询专辑分类信息
        CompletableFuture<Void> completableFutureAlbumCategory = completableFutureAlbumInfo.thenAcceptAsync((albumInfo) -> {
            // 专辑分类信息
            Result<BaseCategoryView> baseCategoryView = albumFeignClient.getCategoryView(albumInfoIndex.getCategory3Id());
            if (baseCategoryView == null || baseCategoryView.getCode() != 200) {
                log.error("远程调用【专辑服务（getCategoryView）】失败,失败category3Id:{}", albumInfoIndex.getCategory3Id());
                String message = baseCategoryView != null ? baseCategoryView.getMessage() : "";
                throw new GuiguException(500, "远程调用【专辑服务（getCategoryView）】失败," + message);
            }

            albumInfoIndex.setCategory1Id(baseCategoryView.getData().getCategory1Id());
            albumInfoIndex.setCategory2Id(baseCategoryView.getData().getCategory2Id());
        }, taskExecutor);

        // 任务三：查询用户信息
        CompletableFuture<Void> completableFutureUserInfo = completableFutureAlbumInfo.thenAcceptAsync((albumInfo) -> {
            // 用户信息
            Result<UserInfoVo> userInfoVo = userFeignClient.getUserInfoVo(albumInfo.getUserId());
            if (userInfoVo == null || userInfoVo.getCode() != 200) {
                log.error("远程调用【用户服务（getUserInfoVo）】失败,失败userId:{}", albumInfo.getUserId());
                String message = userInfoVo != null ? userInfoVo.getMessage() : "";
                throw new GuiguException(500, "远程调用【用户服务（getUserInfoVo）】失败," + message);
            }

            albumInfoIndex.setAnnouncerName(userInfoVo.getData().getNickname());
        }, taskExecutor);

        // 任务四：封装统计数值
        CompletableFuture<Void> completableFutureStat = CompletableFuture.runAsync(() -> {
            // 封装统计数值
            // 5.1 封装播放量数值
            int playStatNum = RandomUtil.randomInt(1000, 2000);
            albumInfoIndex.setPlayStatNum(playStatNum);
            // 5.2 封装订阅量数值
            int subscribeStatNum = RandomUtil.randomInt(800, 1000);
            albumInfoIndex.setSubscribeStatNum(subscribeStatNum);
            // 5.3 封装购买量数值
            int buyStatNum = RandomUtil.randomInt(100, 500);
            albumInfoIndex.setBuyStatNum(buyStatNum);
            // 5.4 封装评论量数值
            int commentStatNum = RandomUtil.randomInt(500, 1000);
            albumInfoIndex.setCommentStatNum(commentStatNum);

            // 5.5 基于以上生成统计数值计算出当前文档热度分值  热度=累加（不同统计数值*权重）
            BigDecimal bigDecimal1 = new BigDecimal("0.1").multiply(BigDecimal.valueOf(playStatNum));
            BigDecimal bigDecimal2 = new BigDecimal("0.2").multiply(BigDecimal.valueOf(subscribeStatNum));
            BigDecimal bigDecimal3 = new BigDecimal("0.3").multiply(BigDecimal.valueOf(buyStatNum));
            BigDecimal bigDecimal4 = new BigDecimal("0.4").multiply(BigDecimal.valueOf(commentStatNum));
            BigDecimal hotScore = bigDecimal1.add(bigDecimal2).add(bigDecimal3).add(bigDecimal4);
            albumInfoIndex.setHotScore(hotScore.doubleValue());
            // 6.保存专辑索引库文档对象
        }, taskExecutor);

        CompletableFuture.allOf(
                        completableFutureAlbumInfo,
                        completableFutureAlbumCategory,
                        completableFutureUserInfo,
                        completableFutureStat
                )
                .join();

        albumInfoIndexRepository.save(albumInfoIndex);
    }

    @Override
    public void lowerAlbum(String albumId) {
        albumInfoIndexRepository.deleteById(albumId);
    }
}
