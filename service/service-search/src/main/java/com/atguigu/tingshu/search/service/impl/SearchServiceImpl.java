package com.atguigu.tingshu.search.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.model.search.AttributeValueIndex;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.search.repository.AlbumInfoIndexRepository;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.search.AlbumInfoIndexVo;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Slf4j
@Service
@SuppressWarnings({"all"})
public class SearchServiceImpl implements SearchService {

    private static final String INDEX_NAME = "albuminfo";

    @Autowired
    private AlbumFeignClient albumFeignClient;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private AlbumInfoIndexRepository albumInfoIndexRepository;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

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

    @Override
    public AlbumSearchResponseVo search(AlbumIndexQuery albumIndexQuery) throws IOException {
        // 一、构建完整检索请求对象：包含完整DSL语句请求体参数
        SearchRequest searchRequest = buildDSL(albumIndexQuery);
        log.info("searchRequest:{}", searchRequest);
        // 二、执行检索
        SearchResponse<AlbumInfoIndex> searchResponse = elasticsearchClient.search(searchRequest, AlbumInfoIndex.class);

        // 三、解析检索结果
        return parseResult(searchResponse, albumIndexQuery);
    }

    @Override
    public AlbumSearchResponseVo parseResult(SearchResponse<AlbumInfoIndex> searchResponse, AlbumIndexQuery albumIndexQuery) {
        AlbumSearchResponseVo albumSearchResponseVo = new AlbumSearchResponseVo();

        // 1.封装总记录数及分页信息
        long total = searchResponse.hits().total() != null ? searchResponse.hits().total().value() : 0L;
        albumSearchResponseVo.setTotal(total);
        albumSearchResponseVo.setPageNo(albumIndexQuery.getPageNo());
        albumSearchResponseVo.setPageSize(albumIndexQuery.getPageSize());
        albumSearchResponseVo.setTotalPages((total + albumIndexQuery.getPageSize() - 1) / albumIndexQuery.getPageSize());

        // 2.封装当前页数据列表
        List<AlbumInfoIndexVo> albumInfoIndexVoList = searchResponse.hits().hits().stream().map(hit -> {
            AlbumInfoIndex albumInfoIndex = hit.source();
            AlbumInfoIndexVo albumInfoIndexVo = new AlbumInfoIndexVo();
            BeanUtil.copyProperties(albumInfoIndex, albumInfoIndexVo);
            // 2.1 高亮结果覆盖：用命中词高亮片段替换原始标题/简介
            if (CollUtil.isNotEmpty(hit.highlight())) {
                List<String> albumTitleHighlightList = hit.highlight().get("albumTitle");
                if (CollUtil.isNotEmpty(albumTitleHighlightList)) {
                    albumInfoIndexVo.setAlbumTitle(albumTitleHighlightList.get(0));
                }
            }
            return albumInfoIndexVo;
        }).collect(Collectors.toList());
        albumSearchResponseVo.setList(albumInfoIndexVoList);

        return albumSearchResponseVo;
    }

    @Override
    public SearchRequest buildDSL(AlbumIndexQuery albumIndexQuery) {
        // 1.构建请求参数
        SearchRequest.Builder builder = new SearchRequest.Builder();
        // 1.1 指定索引库
        builder.index(SearchServiceImpl.INDEX_NAME);
        // 1.2 分页
        int from = (albumIndexQuery.getPageNo() - 1) * albumIndexQuery.getPageSize();
        builder.from(from).size(albumIndexQuery.getPageSize());

        // 2.构建bool查询
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        // 2.1 关键字检索（专辑名称/简介 多字段分词匹配）
        if (StrUtil.isNotBlank(albumIndexQuery.getKeyword())) {
            boolQueryBuilder.must(m -> m.multiMatch(
                    multiMatchQuery -> multiMatchQuery
                            .query(albumIndexQuery.getKeyword())
                            .fields("albumTitle", "albumIntro")
            ));
            // 关键字高亮：命中词项用红色字体标注
            builder.highlight(highlight -> highlight
                    .fields("albumTitle", highlightField -> highlightField
                            .preTags("<span style='color:red'>")
                            .postTags("</span>"))
            );
        } else {
            boolQueryBuilder.must(m -> m.matchAll(matchAllQuery -> matchAllQuery));
        }

        // 2.2 一级分类过滤
        if (albumIndexQuery.getCategory1Id() != null) {
            boolQueryBuilder.filter(f -> f.term(
                    termQuery -> termQuery.field("category1Id").value(albumIndexQuery.getCategory1Id())
            ));
        }
        // 2.3 二级分类过滤
        if (albumIndexQuery.getCategory2Id() != null) {
            boolQueryBuilder.filter(f -> f.term(
                    termQuery -> termQuery.field("category2Id").value(albumIndexQuery.getCategory2Id())
            ));
        }
        // 2.4 三级分类过滤
        if (albumIndexQuery.getCategory3Id() != null) {
            boolQueryBuilder.filter(f -> f.term(
                    termQuery -> termQuery.field("category3Id").value(albumIndexQuery.getCategory3Id())
            ));
        }

        // 2.5 专辑属性过滤（属性id:属性值id  嵌套查询）
        if (CollUtil.isNotEmpty(albumIndexQuery.getAttributeList())) {
            for (String attribute : albumIndexQuery.getAttributeList()) {
                String[] attributeSplit = attribute.split(":");
                long attributeId = Long.parseLong(attributeSplit[0]);
                long valueId = Long.parseLong(attributeSplit[1]);
                boolQueryBuilder.filter(f -> f.nested(nestedQuery -> nestedQuery
                        .path("attributeValueIndexList")
                        .query(nq -> nq.bool(nb -> nb
                                .must(m -> m.term(termQuery -> termQuery
                                        .field("attributeValueIndexList.attributeId").value(attributeId)))
                                .must(m -> m.term(termQuery -> termQuery
                                        .field("attributeValueIndexList.valueId").value(valueId)))
                        ))
                ));
            }
        }
        builder.query(query -> query.bool(boolQueryBuilder.build()));

        // 3.排序  1：综合排序（热度分） 2：播放量 3：最近更新（发布时间）
        if (StrUtil.isNotBlank(albumIndexQuery.getOrder())) {
            String[] orderSplit = albumIndexQuery.getOrder().split(":");
            String orderField = switch (orderSplit[0]) {
                case "1" -> "hotScore";
                case "2" -> "playStatNum";
                case "3" -> "createTime";
                default -> "";
            };
            if (StrUtil.isNotBlank(orderField)) {
                SortOrder sortOrder = "desc".equalsIgnoreCase(orderSplit[1]) ? SortOrder.Desc : SortOrder.Asc;
                builder.sort(sortOption -> sortOption.field(
                        fieldSort -> fieldSort.field(orderField).order(sortOrder)
                ));
            }
        }

        return builder.build();
    }


}
