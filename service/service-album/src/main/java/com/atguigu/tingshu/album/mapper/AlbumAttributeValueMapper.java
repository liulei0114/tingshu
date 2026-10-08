package com.atguigu.tingshu.album.mapper;

import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AlbumAttributeValueMapper extends BaseMapper<AlbumAttributeValue> {

    void insertBatch(List<AlbumAttributeValue> albumAttributeValueList);
}
