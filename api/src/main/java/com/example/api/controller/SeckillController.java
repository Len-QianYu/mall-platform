package com.example.api.controller;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.dto.SeckillActivityView;
import com.example.api.service.SeckillService;
import com.example.api.utils.Pagination;
import com.example.api.utils.ResultTemplate;

@RestController
@RequestMapping("/seckill")
public class SeckillController {
    private final SeckillService service;
    public SeckillController(SeckillService service) { this.service = service; }

    @GetMapping
    public ResultTemplate index(@RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "12") Integer pageSize, @RequestParam(required = false) String phase) {
        if (phase != null && !Set.of("LIVE", "UPCOMING", "SOLD_OUT", "ENDED").contains(phase)) {
            ResultTemplate invalid = new ResultTemplate();
            invalid.setStatus(false);
            return invalid.putPayload("message", "活动筛选条件不正确");
        }
        Pagination pagination = Pagination.paginate(this.service.count(phase), Math.min(pageSize, 100), page);
        return new ResultTemplate().putPayload("activities", this.service.fetch(phase, pagination))
            .putPayload("pagination", pagination);
    }

    @GetMapping("/{id}")
    public ResultTemplate detail(@PathVariable Long id) {
        SeckillActivityView activity = this.service.fetchById(id).orElse(null);
        ResultTemplate result = new ResultTemplate();
        if (activity == null) {
            result.setStatus(false);
            return result.putPayload("message", "秒杀活动不存在");
        }
        return result.putPayload("activity", activity);
    }
}
