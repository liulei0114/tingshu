package com.atguigu.tingshu.search.service;

import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import com.atguigu.tingshu.model.search.AlbumInfoIndex;
import com.atguigu.tingshu.model.search.SuggestIndex;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SearchService {


    void upperAlbum(Long albumId);

    void lowerAlbum(String albumId);

    void saveSuggestIndex(AlbumInfoIndex albumInfoIndex);

    AlbumSearchResponseVo search(AlbumIndexQuery albumIndexQuery) throws IOException;

    /**
     * 基于查询条件封装ES检索DSL语句
     * @param albumIndexQuery 查询条件
     * @return
     */
    SearchRequest buildDSL(AlbumIndexQuery albumIndexQuery);
    /**
     * 解析ES响应结果
     * @param searchResponse
     * @param albumIndexQuery
     * @return
     */
    AlbumSearchResponseVo parseResult(SearchResponse<AlbumInfoIndex> searchResponse, AlbumIndexQuery albumIndexQuery);

    List<Map<String, Object>> channel(Long category1Id);

    List<String> completeSuggest(String keyword);

    Collection<String> parseSuggestResult(SearchResponse<SuggestIndex> searchResponse, String s);
}
