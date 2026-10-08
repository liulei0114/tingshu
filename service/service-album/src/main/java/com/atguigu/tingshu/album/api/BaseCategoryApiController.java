package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.album.CategoryAttributeValue;
import com.atguigu.tingshu.vo.album.CategoryInfoVo;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@Tag(name = "分类管理")
@RestController
@RequestMapping(value="/api/album")
@SuppressWarnings({"all"})
public class BaseCategoryApiController {

	@Autowired
	private BaseCategoryService baseCategoryService;


	@GetMapping("/category/getBaseCategoryList")
	public Result<List<CategoryInfoVo>> getCategoryList() {
		return Result.build(baseCategoryService.getCategoryList(), ResultCodeEnum.SUCCESS);
	}

	@GetMapping("/category/findAttribute/{category1Id}")
	public Result<List<CategoryAttributeValue>> findAttribute(@PathVariable Long category1Id) {
		return Result.build(baseCategoryService.findAttributeByCategory1Id(category1Id), ResultCodeEnum.SUCCESS);
	}
}

