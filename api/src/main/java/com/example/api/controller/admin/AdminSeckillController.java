package com.example.api.controller.admin;

import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.dto.SeckillRequests;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.SeckillService;
import com.example.api.utils.Pagination;
import com.example.api.utils.ResultTemplate;

@RestController
@RequestMapping("/admin/seckill")
public class AdminSeckillController {
    private final SeckillService service;
    public AdminSeckillController(SeckillService service) { this.service = service; }

    @GetMapping
    public ResultTemplate index(@RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "10") Integer pageSize) {
        Pagination pagination = Pagination.paginate(this.service.count(true), Math.min(pageSize, 100), page);
        return new ResultTemplate().putPayload("activities", this.service.fetch(true, pagination))
            .putPayload("pagination", pagination);
    }

    @PostMapping("/create")
    public ResultTemplate create(@RequestBody @Validated SeckillRequests.Create fields, BindingResult errors) {
        if (errors.hasErrors()) throw new ValidateFailedException("请检查商品、秒杀价、配额、限购数量和活动时间");
        return new ResultTemplate().putPayload("activity", this.service.create(fields)).putPayload("message", "秒杀活动已创建");
    }

    @PostMapping("/close")
    public ResultTemplate close(@RequestBody @Validated SeckillRequests.Close fields, BindingResult errors) {
        if (errors.hasErrors()) throw new ValidateFailedException("活动 ID 不正确");
        ResultTemplate result = new ResultTemplate();
        boolean closed = this.service.close(fields.id());
        result.setStatus(closed);
        return result.putPayload("message", closed ? "活动已关闭，剩余配额已归还普通库存" : "活动不存在或已关闭");
    }
}
