package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.BaseCategory3;
import com.atguigu.tingshu.model.album.BaseCategoryView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BaseCategoryViewMapper extends BaseMapper<BaseCategoryView> {

    List<BaseCategory3> selectTopBaseCategory3(Long category1Id);

}
