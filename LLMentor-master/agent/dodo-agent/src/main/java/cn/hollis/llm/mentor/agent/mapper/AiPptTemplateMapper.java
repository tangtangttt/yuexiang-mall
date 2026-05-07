package cn.hollis.llm.mentor.agent.mapper;

import cn.hollis.llm.mentor.agent.entity.record.pptx.AiPptTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI PPT 模板 Mapper 接口
 */
@Mapper
public interface AiPptTemplateMapper extends BaseMapper<AiPptTemplate> {
}
