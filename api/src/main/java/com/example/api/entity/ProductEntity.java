package com.example.api.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.api.validator.ProductValidateGroup;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 商品表
@Data
@TableName("product")
public class ProductEntity {

    @TableId(type = IdType.AUTO)
    @NotNull(groups = { ProductValidateGroup.Update.class, ProductValidateGroup.UpdateStatus.class })
    private Integer id;

    @NotBlank(groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class })
    @Size(max = 128, groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class })
    private String name;

    // 副标题（卖点）
    @Size(max = 255, groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class })
    private String subtitle;

    // 商品详情：列类型 TEXT 最多 65535 字节，utf8mb4 每字最多 4 字节，按 1 万字限制留出余量
    @Size(max = 10000, groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class })
    private String description;

    @NotBlank(groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class })
    @Size(max = 32, groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class })
    private String category;

    // 售价（元），对应 DECIMAL(10, 2)
    @NotNull(groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class })
    @DecimalMin(value = "0.01", groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class })
    @Digits(integer = 8, fraction = 2, groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class })
    private BigDecimal price;

    @NotNull(groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class })
    @Min(value = 0, groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class })
    private Integer stock;

    // 销量由下单流程累加，管理端不能改
    private Integer sales;

    // 主图 URL（RustFS 接入前先存图片外链）
    @Size(max = 512, groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class })
    private String mainImage;

    // 状态：1 上架 / 0 下架
    @NotNull(groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class, ProductValidateGroup.UpdateStatus.class })
    @Min(value = 0, groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class, ProductValidateGroup.UpdateStatus.class })
    @Max(value = 1, groups = { ProductValidateGroup.Create.class, ProductValidateGroup.Update.class, ProductValidateGroup.UpdateStatus.class })
    private Integer status;

    // created_at / updated_at 由数据库维护，这里只读不写
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime createdAt;

    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

}
