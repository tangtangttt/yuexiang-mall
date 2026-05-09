package com.yuex.mobile.customer.dto;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

/**
 * 流式输出统一 JSON 行协议（与 dodo-agent 前端解析方式一致）
 */
public final class CustomerAgentResponse {

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_THINKING = "thinking";
    public static final String TYPE_REFERENCE = "reference";
    public static final String TYPE_ERROR = "error";
    public static final String TYPE_RECOMMEND = "recommend";
    /** 首包下发服务端确定的 sessionId，便于前端持久化 */
    public static final String TYPE_SESSION = "session";

    private String type;
    private String content;
    private Integer count;
    private Object data;

    public CustomerAgentResponse() {
    }

    public CustomerAgentResponse(String type, String content) {
        this.type = type;
        this.content = content;
    }

    public CustomerAgentResponse(String type, String content, Integer count) {
        this.type = type;
        this.content = content;
        this.count = count;
    }

    public static String session(String sessionId) {
        return new CustomerAgentResponse(TYPE_SESSION, sessionId).toJson();
    }

    public static String text(String content) {
        return new CustomerAgentResponse(TYPE_TEXT, content).toJson();
    }

    public static String thinking(String content) {
        return new CustomerAgentResponse(TYPE_THINKING, content).toJson();
    }

    public static String reference(String content, Integer count) {
        return new CustomerAgentResponse(TYPE_REFERENCE, content, count).toJson();
    }

    public static String reference(String content) {
        try {
            var arr = JSON.parseArray(content);
            if (arr != null) {
                return reference(content, arr.size());
            }
        } catch (Exception ignored) {
        }
        return reference(content, null);
    }

    public static String error(String content) {
        return new CustomerAgentResponse(TYPE_ERROR, content).toJson();
    }

    public static String recommend(String content, Integer count) {
        return new CustomerAgentResponse(TYPE_RECOMMEND, content, count).toJson();
    }

    public static String recommend(String content) {
        return recommend(content, null);
    }

    public String toJson() {
        JSONObject obj = new JSONObject();
        obj.put("type", type);
        if (content != null) {
            obj.put("content", content);
        }
        if (count != null) {
            obj.put("count", count);
        }
        if (data != null) {
            obj.put("data", data);
        }
        if (TYPE_RECOMMEND.equals(type) && content != null) {
            try {
                obj.put("content", JSON.parse(content));
            } catch (Exception e) {
                obj.put("content", content);
            }
        }
        if (TYPE_REFERENCE.equals(type) && content != null) {
            try {
                obj.put("content", JSON.parse(content));
            } catch (Exception e) {
                obj.put("content", content);
            }
        }
        return obj.toJSONString();
    }
}
