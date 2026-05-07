package cn.hollis.llm.mentor.function;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class TimeTools {
    @Tool(name = "getTimeByZoneId", description = "Get time by zone id")
    public String getTimeByZoneId(@ToolParam(description = "Time zone id, such as Asia/Shanghai") String zoneId) {
        System.out.println("getTimeByZoneId，zoneId=" + zoneId);
        ZoneId zid = ZoneId.of(zoneId);
        ZonedDateTime zonedDateTime = ZonedDateTime.now(zid);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        return zonedDateTime.format(formatter);
    }
}
