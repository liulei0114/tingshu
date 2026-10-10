package com.atguigu.tingshu.album.api;

import cn.hutool.core.collection.CollUtil;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.album.BaseCategory3;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.model.album.CategoryAttributeValue;
import com.atguigu.tingshu.vo.album.CategoryInfoVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Tag(name = "分类管理")
@RestController
@RequestMapping(value = "/api/album")
@SuppressWarnings({"all"})
public class BaseCategoryApiController {

    @Autowired
    private BaseCategoryService baseCategoryService;


    @GetMapping("/category/getBaseCategoryList")
    public Result<List<CategoryInfoVo>> getCategoryList() {
        return Result.build(baseCategoryService.getCategoryList(null), ResultCodeEnum.SUCCESS);
    }

    @GetMapping("/category/findAttribute/{category1Id}")
    public Result<List<CategoryAttributeValue>> findAttribute(@PathVariable Long category1Id) {
        return Result.build(baseCategoryService.findAttributeByCategory1Id(category1Id), ResultCodeEnum.SUCCESS);
    }

    /**
     * 根据三级分类ID查询分类视图
     *
     * @param category3Id
     * @return
     */
    @Operation(summary = "根据三级分类ID查询分类视图")
    @GetMapping("/category/getCategoryView/{category3Id}")
    public Result<BaseCategoryView> getCategoryView(@PathVariable Long category3Id) {
        BaseCategoryView baseCategoryView = baseCategoryService.getCategoryView(category3Id);
        return Result.ok(baseCategoryView);
    }

    @Operation(summary = "根据一级分类Id查询置顶7个三级分类列表")
    @GetMapping("/category/findTopBaseCategory3/{category1Id}")
    public Result<List<BaseCategory3>> findTopBaseCategory3(@PathVariable Long category1Id) {
        List<BaseCategory3> list = baseCategoryService.findTopBaseCategory3(category1Id);
        return Result.ok(list);
    }

    /**
     * 查询1级分类下包含所有二级以及三级分类
     *
     * @param category1Id
     * @return
     */
    @Operation(summary = "查询1级分类下包含所有二级以及三级分类")
    @GetMapping("/category/getBaseCategoryList/{category1Id}")
    public Result<CategoryInfoVo> getBaseCategoryListByCategory1Id(@PathVariable Long category1Id) {
        List<CategoryInfoVo> categoryInfoVo = baseCategoryService.getCategoryList(category1Id);
        if (CollUtil.isNotEmpty(categoryInfoVo)) {
            return Result.ok(categoryInfoVo.get(0));
        }
        return Result.ok();
    }
}

