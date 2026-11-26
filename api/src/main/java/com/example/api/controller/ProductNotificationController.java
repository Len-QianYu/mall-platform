package com.example.api.controller;

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

import com.example.api.dto.ProductNotificationRequests;
import com.example.api.exception.ValidateFailedException;
import com.example.api.service.UserNotificationService;
import com.example.api.utils.CurrentUser;
import com.example.api.utils.Pagination;
import com.example.api.utils.ResultTemplate;
import com.example.api.websocket.NotificationSocketTickets;

@RestController
@RequestMapping("/notification")
public class ProductNotificationController {
    private final UserNotificationService notifications;
    private final NotificationSocketTickets tickets;

    public ProductNotificationController(UserNotificationService notifications, NotificationSocketTickets tickets) {
        this.notifications = notifications;
        this.tickets = tickets;
    }

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
    public ResultTemplate read(@RequestBody @Validated ProductNotificationRequests.Read fields,
        BindingResult bindingResult, @AuthenticationPrincipal Jwt jwt) {
        if (bindingResult.hasErrors()) throw new ValidateFailedException("通知 ID 不正确");
        ResultTemplate result = new ResultTemplate();
        if (!this.notifications.markRead(CurrentUser.id(jwt), fields.id(), fields.type())) {
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

    @PostMapping("/socket-ticket")
    public ResultTemplate socketTicket(@AuthenticationPrincipal Jwt jwt) {
        Number version = jwt.getClaim("authVersion");
        return new ResultTemplate().putPayload("ticket", this.tickets.issue(CurrentUser.id(jwt), jwt.getExpiresAt(),
            version == null ? 0L : version.longValue()));
    }
}
