package cn.hollis.llm.mentor.langchain4j.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

public class TemperatureTools {

    @Tool(value = "Get temperature by city and date", name = "getTemperatureByCityAndDate")
    public String getTemperatureByCityAndDate(@P("city for get Temperature") String city, @P("date for get Temperature") String date) {
        System.out.println("getTemperatureByCityAndDate invoke...");
        return "23摄氏度";
    }
}
