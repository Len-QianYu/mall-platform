package com.example.api.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.example.api.validator.UserValidateGroup;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// 用户表
@Data
@TableName("user")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @NotBlank(
        groups = {
        UserValidateGroup.Create.class,
        UserValidateGroup.Login.class
    })
    @Size(
        min = 2,
        max = 16,
        groups = {
        UserValidateGroup.Create.class,
        UserValidateGroup.Login.class
    })
    private String username;

    @Size(
        max = 32,
        groups = {
        UserValidateGroup.Create.class,
        UserValidateGroup.Update.class
    })
    private String name;

    // 只写不读：请求可反序列化写入，响应序列化时自动隐藏
    @NotBlank(
        groups = {
        UserValidateGroup.Create.class,
        UserValidateGroup.Login.class
    })
    @Size(
        min = 6,
        max = 32,
        groups = {
        UserValidateGroup.Create.class,
        UserValidateGroup.Login.class
    })
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    // 角色：user 普通用户 / admin 管理员（与前端账户类型取值一致）
    @NotBlank(
        groups = {
        UserValidateGroup.Login.class
    })
    private String role = "user";

    // 性别：F 女 / M 男（对应 schema 的 CHAR(1)）
    private String gender;

    @Email(
        groups = {
        UserValidateGroup.Create.class,
        UserValidateGroup.Update.class
    })
    @Size(
        max = 128,
        groups = {
        UserValidateGroup.Create.class,
        UserValidateGroup.Update.class
    })
    private String email;

    private String phone;

    // 数据库列为 avatar_url，MyBatis-Plus 默认驼峰映射即可对应
    private String avatarUrl;

    // 状态：1 启用 / 0 禁用（对应 schema 的 TINYINT）
    private Integer status = 1;

    private LocalDateTime registeredAt;

    // updated_at 由数据库 ON UPDATE CURRENT_TIMESTAMP 维护，这里只读不写
    @TableField(insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
        updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private LocalDateTime updatedAt;

}
