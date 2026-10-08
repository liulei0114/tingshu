package com.atguigu.tingshu.vo.album;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.List;

@Data
public class CategoryInfoVo {

    private Long categoryId;

    private String categoryName;

    @JsonIgnore
    private Long parentCategoryId;

    private List<CategoryInfoVo> categoryChild;
}
