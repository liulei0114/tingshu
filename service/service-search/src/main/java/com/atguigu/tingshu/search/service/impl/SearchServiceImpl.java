package com.atguigu.tingshu.search.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.pinyin.PinyinUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.Suggestion;
import com.atguigu.tingshu.album.AlbumFeignClient;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.BaseCategory3;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.model.search.AttributeValueIndex;
import com.atguigu.tingshu.model.search.SuggestIndex;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.search.repository.AlbumInfoIndexRepository;
import com.atguigu.tingshu.search.repository.SuggestIndexRepository;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.user.client.UserFeignClient;
import com.atguigu.tingshu.vo.album.CategoryInfoVo;
import com.atguigu.tingshu.vo.search.AlbumInfoIndexVo;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.suggest.Completion;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


@Slf4j
@Service
@SuppressWarnings({"all"})
public class SearchServiceImpl implements SearchService {

    private static final String INDEX_NAME = "albuminfo";

    private static final String SUGGEST_INDEX = "suggestinfo";

    @Autowired
    private AlbumFeignClient albumFeignClient;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private AlbumInfoIndexRepository albumInfoIndexRepository;

    @Autowired
    private SuggestIndexRepository suggestIndexRepository;

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

        // 3.将专辑标题存入提词索引库
        this.saveSuggestIndex(albumInfoIndex);
    }

    @Override
    public void saveSuggestIndex(AlbumInfoIndex albumInfoIndex) {
        // 1.构建索引库文档对象
        SuggestIndex suggestIndex = new SuggestIndex();
        suggestIndex.setId(albumInfoIndex.getId().toString());
        String albumTitle = albumInfoIndex.getAlbumTitle();
        suggestIndex.setTitle(albumTitle);
        suggestIndex.setKeyword(new Completion(new String[]{albumTitle}));
        // 1.1 将汉字转为汉语拼音 jing dian liu sheng ji
        String albumTitlePinyin = PinyinUtil.getPinyin(albumTitle, "");
        suggestIndex.setKeywordPinyin(new Completion(new String[]{albumTitlePinyin}));
        // 1.1 将汉字转为汉语拼音首字母
        String albumTitleFirstLetter = PinyinUtil.getFirstLetter(albumTitle, "");
        suggestIndex.setKeywordSequence(new Completion(new String[]{albumTitleFirstLetter}));

        // 2.存入提词文档记录到提词索引库
        suggestIndexRepository.save(suggestIndex);
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
    public List<Map<String, Object>> channel(Long category1Id) {
        // 1.根据1级分类ID获取置顶三级分类ID
        // 1.1 远程调用远程调用专辑微服务获取置顶3级分类
        Result<List<BaseCategory3>> topBaseCategory3Result = albumFeignClient.findTopBaseCategory3(category1Id);
        if (topBaseCategory3Result == null || topBaseCategory3Result.getCode() != 200) {
            log.error("远程调用【专辑服务（findTopBaseCategory3）】失败,失败category1Id:{}", category1Id);
            String message = topBaseCategory3Result != null ? topBaseCategory3Result.getMessage() : "";
            throw new GuiguException(500, "远程调用【专辑服务（findTopBaseCategory3）】失败," + message);
        }
        List<BaseCategory3> topBaseCategory3 = topBaseCategory3Result.getData();
        // 1.2 获取置顶三级分类ID列表
        List<FieldValue> fieldValueList = topBaseCategory3
                .stream()
                .map(c3 -> FieldValue.of(c3.getId()))
                .collect(Collectors.toList());
        // 1.3 构建ES检索DSL：
        // 按置顶三级分类ID列表过滤 + 按category3Id分组聚合（取前10个分组） + 桶内按hotScore降序取前10个专辑
        if (CollUtil.isEmpty(fieldValueList)) {
            return new ArrayList<>();
        }
        SearchRequest searchRequest = new SearchRequest.Builder()
                .index(INDEX_NAME)
                // 聚合查询不需要原始文档，size=0提速
                .size(0)
                .query(query -> query.bool(bool -> bool.filter(
                        filter -> filter.terms(terms -> terms.field("category3Id")
                                .terms(termsValue -> termsValue.value(fieldValueList))))))
                .aggregations("category3Id_agg", aggregation -> aggregation
                        // 聚合桶数=置顶分类数，保证每个置顶分类都有对应桶
                        .terms(termsAgg -> termsAgg.field("category3Id").size(fieldValueList.size()))
                        .aggregations("topHits", subAggregation -> subAggregation.topHits(topHits -> topHits
                                .size(10)
                                .sort(sort -> sort.field(fieldSort -> fieldSort.field("hotScore").order(SortOrder.Desc))))))
                .build();

        // 2.执行聚合检索
        SearchResponse<Void> searchResponse;
        try {
            searchResponse = elasticsearchClient.search(searchRequest, Void.class);
        } catch (IOException e) {
            log.error("执行ES聚合检索【channel】失败,失败category1Id:{}", category1Id, e);
            throw new GuiguException(500, "执行ES聚合检索【channel】失败," + e.getMessage());
        }

        // 3.解析聚合结果：每个置顶三级分类封装一个Map（分类对象+热度TOP10专辑列表）
        // 3.1 遍历聚合桶，按category3Id索引桶数据（category3Id为long字段，对应lterms聚合）
        Map<Long, List<AlbumInfoIndexVo>> category3AlbumListMap = new HashMap<>();
        LongTermsAggregate longTermsAggregate = searchResponse.aggregations().get("category3Id_agg").lterms();
        for (LongTermsBucket longTermsBucket : longTermsAggregate.buckets().array()) {
            List<AlbumInfoIndexVo> albumInfoIndexVoList = longTermsBucket.aggregations().get("topHits")
                    .topHits().hits().hits().stream()
                    .map(hit -> {
                        AlbumInfoIndex albumInfoIndex = hit.source().to(AlbumInfoIndex.class);
                        AlbumInfoIndexVo albumInfoIndexVo = new AlbumInfoIndexVo();
                        BeanUtil.copyProperties(albumInfoIndex, albumInfoIndexVo);
                        return albumInfoIndexVo;
                    }).collect(Collectors.toList());
            category3AlbumListMap.put(longTermsBucket.key(), albumInfoIndexVoList);
        }

        // 3.2 按置顶三级分类列表顺序（fieldValueList顺序）组装返回结果，与ES聚合桶的doc_count顺序无关
        List<Map<String, Object>> channelList = new ArrayList<>();
        for (BaseCategory3 baseCategory3 : topBaseCategory3) {
            Map<String, Object> channelMap = new HashMap<>();
            channelMap.put("baseCategory3", baseCategory3);
            // 某置顶分类在索引库中无专辑时，返回空列表占位，保证顺序完整
            channelMap.put("list", category3AlbumListMap.getOrDefault(baseCategory3.getId(), new ArrayList<>()));
            channelList.add(channelMap);
        }
        return channelList;
    }

    @Override
    public List<String> completeSuggest(String keyword) {
        try {
            // 1.发起自动补全请求
            SearchResponse<SuggestIndex> searchResponse = elasticsearchClient.search(s ->
                            s.index(SUGGEST_INDEX)
                                    .suggest(
                                            s1 -> s1.suggesters("letter-suggest", fs -> fs.prefix(keyword).completion(c -> c.field("keywordSequence").size(10).skipDuplicates(true)))
                                                    .suggesters("pinyin-suggest", s2 -> s2.prefix(keyword).completion(c -> c.field("keywordPinyin").size(10).skipDuplicates(true)))
                                                    .suggesters("keyword-suggest", s2 -> s2.prefix(keyword).completion(c -> c.field("keyword").size(10).skipDuplicates(true)))
                                    )
                    , SuggestIndex.class);
            // 2.解析ES自动补全结果
            Set<String> hashSet = new HashSet<>();
            // 2.1 解析建议结果-通过不同建议参数名获取汉字、拼音等提示词结果
            hashSet.addAll(this.parseSuggestResult(searchResponse, "letter-suggest"));
            hashSet.addAll(this.parseSuggestResult(searchResponse, "pinyin-suggest"));
            hashSet.addAll(this.parseSuggestResult(searchResponse, "keyword-suggest"));
            // 2.2 如果解析建议提示词列表长度小于10，采用全文查询尝试补全到10个
            if (hashSet.size() < 10) {
                // 2.2.1 根据用户录入字符进行全文检索
                SearchResponse<AlbumInfoIndex> matchSearchResponse = elasticsearchClient.search(
                        s -> s.index(INDEX_NAME)
                                .query(q -> q.match(m -> m.field("albumTitle").query(keyword)))
                                .size(10)
                        , AlbumInfoIndex.class
                );
                HitsMetadata<AlbumInfoIndex> hits = matchSearchResponse.hits();
                List<Hit<AlbumInfoIndex>> hitList = hits.hits();
                if (CollectionUtil.isNotEmpty(hitList)) {
                    for (Hit<AlbumInfoIndex> hit : hitList) {
                        AlbumInfoIndex source = hit.source();
                        // 2.2.2 将检索到专辑标题内容加入到提词结果列表中
                        hashSet.add(source.getAlbumTitle());
                        if (hashSet.size() >= 10) {
                            break;
                        }
                    }
                }
            }
            if (hashSet.size() >= 10) {
                // 如果提词结果列表中大于10截取前10个
                return new ArrayList<>(hashSet).subList(0, 10);
            } else {
                return new ArrayList<>(hashSet);
            }
        } catch (IOException e) {
            log.error("[搜索服务]关键字自动补全异常：{}", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Collection<String> parseSuggestResult(SearchResponse<SuggestIndex> searchResponse, String suggestName) {
        // 根据自定义建议词参数名称获取结果列表
        List<String> list = new ArrayList<>();
        List<Suggestion<SuggestIndex>> suggestionList = searchResponse.suggest().get(suggestName);
        if (CollectionUtil.isNotEmpty(suggestionList)) {
            // 遍历得到建议对象
            for (Suggestion<SuggestIndex> suggestIndexSuggestion : suggestionList) {
                for (CompletionSuggestOption<SuggestIndex> option : suggestIndexSuggestion.completion().options()) {
                    SuggestIndex suggestIndex = option.source();
                    list.add(suggestIndex.getTitle());
                }
            }
        }
        return list;
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
