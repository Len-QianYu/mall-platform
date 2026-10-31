package com.example.api.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.api.entity.ProductEntity;

@Mapper
public interface ProductMapper extends BaseMapper<ProductEntity> {

    // 已用过的全部分类（去重）
    @Select("SELECT DISTINCT category FROM product ORDER BY category")
    List<String> selectCategories();

}
