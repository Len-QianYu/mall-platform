package com.example.api.controller.admin;

import java.util.List;

import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.entity.ProductEntity;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.ProductService;
import com.example.api.utils.Pagination;
import com.example.api.utils.ResultTemplate;
import com.example.api.validator.ProductValidateGroup;

// 管理端商品管理（/admin/** 只允许管理员访问，见 SecurityConfiguration）
@RestController
@RequestMapping("/admin/product")
public class AdminProductController {

    private final ProductService productService;

    public AdminProductController(ProductService productService) {
        this.productService = productService;
    }

    // 商品列表：关键字匹配商品名，可按分类、上下架状态筛选
    @GetMapping
    public ResultTemplate index(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) Integer status
    ) {
        ResultTemplate result = new ResultTemplate();

        // 先计数再分页：页码超出总页数时 Pagination 会收回到最后一页
        Integer total = this.productService.count(keyword, category, status);
        Pagination pagination = Pagination.paginate(total, pageSize, page);
        List<ProductEntity> products = this.productService.fetch(keyword, category, status, pagination);

        result.putPayload("products", products);
        result.putPayload("pagination", pagination);
        return result;
    }

    // 已有分类：筛选和表单的下拉选项
    @GetMapping("/categories")
    public ResultTemplate categories() {
        ResultTemplate result = new ResultTemplate();

        result.putPayload("categories", this.productService.fetchCategories());
        return result;
    }

    @PostMapping("/create")
    public ResultTemplate create(
        @RequestBody @Validated({ ProductValidateGroup.Create.class }) ProductEntity fields,
        BindingResult bindingResult
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("商品信息不符合要求");
        }

        // 只取管理端可填写的字段：请求体绑定的是整个实体，直接保存的话客户端可以夹带 id、销量等字段
        ProductEntity product = new ProductEntity();
        product.setName(fields.getName());
        product.setSubtitle(fields.getSubtitle());
        product.setDescription(fields.getDescription());
        product.setCategory(fields.getCategory());
        product.setPrice(fields.getPrice());
        product.setStock(fields.getStock());
        product.setMainImage(fields.getMainImage());
        product.setStatus(fields.getStatus());

        this.productService.create(product);

        result.putPayload("message", "商品已新增");
        return result;
    }

    @PostMapping("/update")
    public ResultTemplate update(
        @RequestBody @Validated({ ProductValidateGroup.Update.class }) ProductEntity fields,
        BindingResult bindingResult
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("商品信息不符合要求");
        }

        if (this.productService.update(fields) == 0) {
            result.setStatus(false);
            result.putPayload("message", "商品不存在");
            return result;
        }

        result.putPayload("message", "保存成功");
        return result;
    }

    // 上架 / 下架
    @PostMapping("/status")
    public ResultTemplate status(
        @RequestBody @Validated({ ProductValidateGroup.UpdateStatus.class }) ProductEntity fields,
        BindingResult bindingResult
    ) {
        ResultTemplate result = new ResultTemplate();

        if (bindingResult.hasErrors()) {
            throw new ValidateFailedException("商品 ID 或状态不正确");
        }

        if (this.productService.updateStatus(fields.getId(), fields.getStatus()) == 0) {
            result.setStatus(false);
            result.putPayload("message", "商品不存在");
            return result;
        }

        result.putPayload("message", fields.getStatus() == 1 ? "已上架" : "已下架");
        return result;
    }

}
