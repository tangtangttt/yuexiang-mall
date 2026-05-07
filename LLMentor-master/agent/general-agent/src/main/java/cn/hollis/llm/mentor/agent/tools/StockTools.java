package cn.hollis.llm.mentor.agent.tools;

import org.apache.commons.lang3.time.DateUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

public class StockTools {

    @Tool(name = "search_prices", description = "查询近期股票价格")
    public StockPriceResponse search_prices(@ToolParam(description = "股票代码") String code) {

        System.out.println("search_prices : " + code);
        StockPriceResponse response = new StockPriceResponse();

        if (code.equals("TESLA")) {
            response.setSuccess(true);
            Date today = new Date();
            response.setStockList(List.of(new Stock(today, new BigDecimal("12.21")),
                    new Stock(DateUtils.addDays(today, -1), new BigDecimal("12.24")),
                    new Stock(DateUtils.addDays(today, -2), new BigDecimal("12.26")),
                    new Stock(DateUtils.addDays(today, -3), new BigDecimal("12.28")),
                    new Stock(DateUtils.addDays(today, -4), new BigDecimal("12.32")),
                    new Stock(DateUtils.addDays(today, -5), new BigDecimal("12.40"))));
            return response;
        }

        response.setSuccess(false);
        response.setErrorMessage("请传入正确的股票代码");
        return response;
    }

    @Tool(name = "search_code", description = "根据公司名称查询股票代码")
    public String search_code(@ToolParam(description = "公司名称") String companyName) {
        System.out.println("search_code : " + companyName);
        if (companyName.equals("百度")) {
            return "BIDU";
        } else if (companyName.equals("阿里")) {
            return "BABA";
        } else if (companyName.equals("特斯拉")) {
            return "TESLA";
        } else {
            return "股票代码查询失败，请输出正确公司名，要求是中文";
        }
    }

    @Tool(name = "getNews", description = "根据公司名查询最近的新闻")
    public List<String> getNews(@ToolParam(description = "公司名称") String companyName) {
        System.out.println("getNews : " + companyName);
        return List.of("特斯拉无人驾驶在上海被禁用", "特斯拉创始人深陷财务危机", "特斯拉股价大跌");
    }
}

