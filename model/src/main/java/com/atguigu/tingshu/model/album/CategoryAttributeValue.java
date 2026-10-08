package com.atguigu.tingshu.model.album;

import lombok.Data;

import java.util.List;

// 专辑属性值
@Data
public class CategoryAttributeValue {
    private Long id;

    private Long categoryId;

    private String attributeName;

    private String createTime;

    private List<BaseAttributeValue> attributeValueList;
}
