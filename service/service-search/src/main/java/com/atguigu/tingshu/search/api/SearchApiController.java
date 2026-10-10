package com.atguigu.tingshu.search.api;

import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.query.search.AlbumIndexQuery;
import com.atguigu.tingshu.search.service.SearchService;
import com.atguigu.tingshu.vo.search.AlbumSearchResponseVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Tag(name = "搜索专辑管理")
@RestController
@RequestMapping("api/search")
@SuppressWarnings({"all"})
public class SearchApiController {

    @Autowired
    private SearchService searchService;


    @Operation(summary = "测试接口，上架专辑")
    @GetMapping("/albumInfo/upperAlbum/{albumId}")
    public Result upperAlbum(@PathVariable Long albumId) {
        searchService.upperAlbum(albumId);
        return Result.ok();
    }

    /**
     * 将指定专辑下架，从索引库删除文档
     *
     * @param albumId
     * @return
     */
    @Operation(summary = "将指定专辑下架")
    @GetMapping("/albumInfo/lowerAlbum/{albumId}")
    public Result lowerAlbum(@PathVariable String albumId) {
        searchService.lowerAlbum(albumId);
        return Result.ok();
    }

    @Operation(summary = "站内搜索")
    @PostMapping("/albumInfo")
    public Result<AlbumSearchResponseVo> search(@RequestBody AlbumIndexQuery albumIndexQuery) {
        try {
            AlbumSearchResponseVo vo = searchService.search(albumIndexQuery);
            return Result.ok(vo);
        } catch (IOException e) {
            throw new GuiguException(ResultCodeEnum.FAIL);
        }
    }

    /**
     * 查询1级分类下置顶3级分类热度TOP6专辑
     * @param category1Id
     * @return [{"baseCategory3":{三级分类对象},list:[专辑列表]},,{其他6个置顶分类热门专辑Map}]
     */
    @Operation(summary = "查询1级分类下置顶3级分类热度TOP6专辑")
    @GetMapping("/albumInfo/channel/{category1Id}")
    public Result<List<Map<String, Object>>> channel(@PathVariable Long category1Id) {
        List<Map<String, Object>> list = searchService.channel(category1Id);
        return Result.ok(list);
    }

    @Operation(summary = "根据用户已录入字符查询提词索引库进行自动补全关键字")
    @GetMapping("/albumInfo/completeSuggest/{keyword}")
    public Result<List<String>> completeSuggest(@PathVariable String keyword){
        List<String> list = searchService.completeSuggest(keyword);
        return Result.ok(list);
    }


}

