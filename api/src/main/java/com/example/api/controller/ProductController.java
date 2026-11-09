package com.example.api.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.entity.ProductEntity;
import com.example.api.service.ProductService;
import com.example.api.utils.Pagination;
import com.example.api.utils.ResultTemplate;

// 商城前台商品：游客可访问（见 SecurityConfiguration），只展示上架商品
@RestController
@RequestMapping("/product")
public class ProductController {

    // 每页条数上限：接口对游客开放，不设上限的话一次请求就能拉走全部商品
    private static final int MAX_PAGE_SIZE = 50;

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // 商品列表：关键字匹配商品名，可按分类筛选；sort 取值见 ProductService.fetchOnSale
    @GetMapping
    public ResultTemplate index(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String sort
    ) {
        ResultTemplate result = new ResultTemplate();

        // 先计数再分页：页码超出总页数时 Pagination 会收回到最后一页
        Integer total = this.productService.countOnSale(keyword, category);
        Pagination pagination = Pagination.paginate(total, Math.min(pageSize, MAX_PAGE_SIZE), page);
        List<ProductEntity> products = this.productService.fetchOnSale(keyword, category, sort, pagination);

        result.putPayload("products", products);
        result.putPayload("pagination", pagination);
        return result;
    }

    // 有上架商品的分类：首页分类入口和列表页筛选用
    @GetMapping("/categories")
    public ResultTemplate categories() {
        ResultTemplate result = new ResultTemplate();

        result.putPayload("categories", this.productService.fetchOnSaleCategories());
        return result;
    }

    // 商品详情：已下架的和不存在一样处理
    @GetMapping("/{id}")
    public ResultTemplate show(@PathVariable Integer id) {
        ResultTemplate result = new ResultTemplate();

        Optional<ProductEntity> product = this.productService.fetchOnSaleById(id);
        if (product.isEmpty()) {
            result.setStatus(false);
            result.putPayload("message", "商品不存在或已下架");
            return result;
        }

        result.putPayload("product", product.get());
        return result;
    }

}
