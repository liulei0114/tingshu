package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.mapper.*;
import com.atguigu.tingshu.album.service.BaseCategoryService;
import com.atguigu.tingshu.model.album.*;
import com.atguigu.tingshu.vo.album.CategoryInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@SuppressWarnings({"all"})
public class BaseCategoryServiceImpl extends ServiceImpl<BaseCategory1Mapper, BaseCategory1> implements BaseCategoryService {

    @Autowired
    private BaseCategory1Mapper baseCategory1Mapper;

    @Autowired
    private BaseCategory2Mapper baseCategory2Mapper;

    @Autowired
    private BaseCategory3Mapper baseCategory3Mapper;

    @Autowired
    private BaseAttributeMapper baseAttributeMapper;

    @Autowired
    private BaseCategoryViewMapper baseCategoryViewMapper;


    public List<CategoryInfoVo> getCategoryList(Long category1Id) {
        LambdaQueryWrapper<BaseCategory1> queryWrapper1 = new LambdaQueryWrapper<BaseCategory1>().orderByAsc(BaseCategory1::getOrderNum);
        if (category1Id != null) {
            queryWrapper1.eq(BaseCategory1::getId, category1Id);
        }
        LambdaQueryWrapper<BaseCategory2> queryWrapper2 = new LambdaQueryWrapper<BaseCategory2>().orderByAsc(BaseCategory2::getOrderNum);
        LambdaQueryWrapper<BaseCategory3> queryWrapper3 = new LambdaQueryWrapper<BaseCategory3>().orderByAsc(BaseCategory3::getOrderNum);

        ArrayList<CategoryInfoVo> result = new ArrayList<>();
        baseCategory1Mapper.selectList(queryWrapper1).stream().forEach(baseCategory1 -> {
            CategoryInfoVo baseCategoryView = new CategoryInfoVo();
            baseCategoryView.setCategoryId(baseCategory1.getId());
            baseCategoryView.setCategoryName(baseCategory1.getName());
            baseCategoryView.setCategoryChild(new ArrayList<CategoryInfoVo>());
            result.add(baseCategoryView);
        });
        baseCategory2Mapper.selectList(queryWrapper2).stream().forEach(baseCategory2 -> {
            CategoryInfoVo baseCategoryView = new CategoryInfoVo();
            baseCategoryView.setCategoryId(baseCategory2.getId());
            baseCategoryView.setCategoryName(baseCategory2.getName());
            baseCategoryView.setParentCategoryId(baseCategory2.getCategory1Id());
            baseCategoryView.setCategoryChild(new ArrayList<CategoryInfoVo>());
            result.add(baseCategoryView);
        });
        baseCategory3Mapper.selectList(queryWrapper3).stream().forEach(baseCategory3 -> {
            CategoryInfoVo baseCategoryView = new CategoryInfoVo();
            baseCategoryView.setCategoryId(baseCategory3.getId());
            baseCategoryView.setCategoryName(baseCategory3.getName());
            baseCategoryView.setParentCategoryId(baseCategory3.getCategory2Id());
            baseCategoryView.setCategoryChild(null);
            result.add(baseCategoryView);
        });
        Map<Long, CategoryInfoVo> baseCategoryMap = result.stream().collect(Collectors.toMap(CategoryInfoVo::getCategoryId, categoryInfoVo->categoryInfoVo));

        result.forEach(baseCategoryView -> {
            if (baseCategoryView.getParentCategoryId() != null) {
                CategoryInfoVo parentCategory = baseCategoryMap.get(baseCategoryView.getParentCategoryId());
                if (parentCategory != null) {
                    parentCategory.getCategoryChild().add(baseCategoryView);
                }
            }
        });
        return result.stream()
                .filter(vo -> vo.getParentCategoryId() == null)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryAttributeValue> findAttributeByCategory1Id(Long category1Id) {
        return baseAttributeMapper.selectAttributeValueByCategoryId(category1Id);
    }

    @Override
    public BaseCategoryView getCategoryView(Long category3Id) {
        return baseCategoryViewMapper.selectById(category3Id);
    }

    @Override
    public List<BaseCategory3> findTopBaseCategory3(Long category1Id) {
        return baseCategoryViewMapper.selectTopBaseCategory3(category1Id);
    }


}
