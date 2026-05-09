package com.yuex.mobile.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("customer_knowledge")
public class CustomerKnowledge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private String category;

    private String tags;

    private Integer viewCount;

    private Integer helpfulCount;

    private Integer status;

    private Date createTime;

    private Date updateTime;
}
