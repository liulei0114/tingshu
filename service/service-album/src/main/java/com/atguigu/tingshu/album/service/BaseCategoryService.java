package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.model.album.BaseCategory1;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.atguigu.tingshu.model.album.CategoryAttributeValue;
import com.atguigu.tingshu.vo.album.CategoryInfoVo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface BaseCategoryService extends IService<BaseCategory1> {

    List<CategoryInfoVo> getCategoryList();

    List<CategoryAttributeValue> findAttributeByCategory1Id(Long category1Id);

    BaseCategoryView getCategoryView(Long category3Id);
}
