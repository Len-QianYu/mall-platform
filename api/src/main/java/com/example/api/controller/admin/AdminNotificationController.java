package com.example.api.controller.admin;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.api.dto.OrderNotificationRequests;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.OrderNotificationService;
import com.example.api.utils.CurrentUser;
import com.example.api.utils.Pagination;
import com.example.api.utils.ResultTemplate;

@RestController
@RequestMapping("/admin/notification")
public class AdminNotificationController {
    private final OrderNotificationService notifications;

    public AdminNotificationController(OrderNotificationService notifications) { this.notifications = notifications; }

    @GetMapping
    public ResultTemplate index(@RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer pageSize,
        @RequestParam(defaultValue = "false") boolean unreadOnly, @AuthenticationPrincipal Jwt jwt) {
        Integer userId = CurrentUser.id(jwt);
        Pagination pagination = Pagination.paginate(this.notifications.count(userId, unreadOnly), Math.min(pageSize, 100), page);
        ResultTemplate result = new ResultTemplate();
        result.putPayload("notifications", this.notifications.fetch(userId, unreadOnly, pagination));
        result.putPayload("pagination", pagination);
        return result.putPayload("unreadCount", this.notifications.count(userId, true));
    }

    @GetMapping("/unread-count")
    public ResultTemplate unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new ResultTemplate().putPayload("unreadCount", this.notifications.count(CurrentUser.id(jwt), true));
    }

    @PostMapping("/read")
    public ResultTemplate read(@RequestBody @Validated OrderNotificationRequests.Read fields,
        BindingResult bindingResult, @AuthenticationPrincipal Jwt jwt) {
        if (bindingResult.hasErrors()) throw new ValidateFailedException("通知 ID 不正确");
        ResultTemplate result = new ResultTemplate();
        if (!this.notifications.markRead(CurrentUser.id(jwt), fields.id())) {
            result.setStatus(false);
            return result.putPayload("message", "通知不存在");
        }
        return result.putPayload("message", "已标为已读");
    }

    @PostMapping("/read-all")
    public ResultTemplate readAll(@AuthenticationPrincipal Jwt jwt) {
        this.notifications.markAllRead(CurrentUser.id(jwt));
        return new ResultTemplate().putPayload("message", "已全部标为已读");
    }
}
