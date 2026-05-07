package cn.hollis.llm.mentor.rag.rewriter;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询重写服务，原QuestionRewriteService
 */
@Service
@Slf4j
public class QueryRewriteService {

    @Autowired
    private ChatModel chatModel;

    //分解提示词
    private static final String DECOMPOSE_PROMPT = """
            # 角色
            你是一名专业的查询逻辑分析专家。
            
            # 任务
            将给定的“用户原始问题”分解为一系列**相互独立、逻辑清晰**，且可单独用于检索的子查询列表。
            你的输出必须是一个标准的JSON数组格式。
            
            # 用户原始问题
            {QUESTION}
            
            # 输出格式要求 (JSON Array)
            [
              "子查询1",
              "子查询2",
              "子查询3",
              "..."
            ]
            
            （不强制要求数组元素个数，可根据真实情况输出，至少保留1个）
            
            # 输出
            请直接输出JSON数组，不要包含解释或多余的文字。  """;

    //问题的富化
    private static final String ENRICH_PROMPT = """
            # 角色
            你是一个专业的问题重写优化器。
            
            # 任务
            根据提供的“对话历史”和“用户原始问题”，重写为一个独立、完整、且包含所有必要背景信息的新查询，用于RAG检索。
            
            ## 对话历史：
            {CHAT_HISTORY}
            
            ## 原始问题：
            {QUESTION}
            
            # 输出
            输出富化过后的新问题，不要包含多余的解释性内容
            """;

    //问题的多样化
    private static final String DIVERSIFY_PROMPT = """
            # 角色
            你是一名专业的语义扩展专家。
            
            # 任务
            为给定的“原始问题”生成**3个**语义相同但**措辞完全不同、且利于检索**的查询变体，以提高检索的召回率。
            你的输出必须是一个标准的JSON数组格式。
            
            # 原始问题
            {QUESTION}
            
            # 输出格式要求 (JSON Array)
            [
              "变体1",
              "变体2",
              "变体3"
            ]
            
            # 输出
            输出富化过后的新问题，不要包含多余的解释性内容
            """;

    private static final String STEP_BACK = """
             # 角色
            你是一个擅长抽象思维和原理推理的专家。
            
            # 任务
            请根据用户提出的具体问题，先“后退一步”，将其转化为一个更通用、更本质的问题，聚焦于背后的原理、规律、概念或一般性知识，而不是具体细节。
            
            # 原始问题
            
            {QUESTION}
            
            # 输出
            请只输出改写后的“后退问题”，不要解释，不要包含原始问题，也不要回答它。
            """;

    private static final String QUESTION = "QUESTION";
    private static final String CHAT_HISTORY = "CHAT_HISTORY";

    /**
     * 问题分解
     *
     * @param question
     * @return
     */
    public List<String> decompose(String question) {
        log.info("===========进入问题分解流程===========");
        log.info("原始问题: {}", question);
        PromptTemplate promptTemplate = new PromptTemplate(DECOMPOSE_PROMPT);
        promptTemplate.add(QUESTION, question);

        String result = chatModel.call(promptTemplate.create()).getResult().getOutput().getText();
        log.info("===========问题分解完成，结果: {} ===========", result);
        return JSON.parseArray(result, String.class);
    }

    /**
     * 问题富化
     */
    public String enrich(String chatHistory, String question) {
        log.info("===========进入问题富化流程===========");
        log.info("对话历史: {}", chatHistory);
        log.info("原始问题: {}", question);
        PromptTemplate promptTemplate = new PromptTemplate(ENRICH_PROMPT);
        promptTemplate.add(CHAT_HISTORY, chatHistory);
        promptTemplate.add(QUESTION, question);

        String result = chatModel.call(promptTemplate.create()).getResult().getOutput().getText();
        log.info("===========问题富化完成，结果: {} ===========", result);
        return result;
    }

    /**
     * 问题多样化
     */
    public List<String> diversify(String question) {
        log.info("===========进入问题多样化流程===========");
        log.info("原始问题: {}", question);
        PromptTemplate promptTemplate = new PromptTemplate(DIVERSIFY_PROMPT);
        promptTemplate.add(QUESTION, question);

        String result = chatModel.call(promptTemplate.create()).getResult().getOutput().getText();
        log.info("===========问题多样化完成，结果: {} ===========", result);
        return JSON.parseArray(result, String.class);
    }

    /**
     * 问题回退
     *
     * @param question
     * @return
     */
    public String stepBack(String question) {
        log.info("===========进入问题回退流程===========");
        log.info("原始问题: {}", question);
        PromptTemplate promptTemplate = new PromptTemplate(STEP_BACK);
        promptTemplate.add(QUESTION, question);

        String result = chatModel.call(promptTemplate.create()).getResult().getOutput().getText();
        log.info("===========问题回退完成，结果: {} ===========", result);
        return result;
    }

    // 组合方法
    public List<String> rewriteQuery(String query) {
        log.info("===========进入问题重写组合策略流程===========");
        log.info("原始问题: {}", query);

        //回退
        String stepBackQuery = this.stepBack(query);

        // 分解
        List<String> decomposedQueries = this.decompose(stepBackQuery);

        // 多样化
        List<String> finalQueries = new ArrayList<>();
        for (String subQuery : decomposedQueries) {
            List<String> variations = this.diversify(subQuery);
            finalQueries.addAll(variations);
        }

        if (finalQueries.isEmpty()) {
            finalQueries.add(query);
        }

        log.info("===========组合重写完成，最终查询列表: {} ===========", finalQueries);
        return finalQueries;
    }
}
