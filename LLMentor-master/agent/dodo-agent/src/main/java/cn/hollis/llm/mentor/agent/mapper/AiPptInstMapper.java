package cn.hollis.llm.mentor.agent.mapper;

import cn.hollis.llm.mentor.agent.entity.record.pptx.AiPptInst;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI PPT 实例 Mapper 接口
 */
@Mapper
public interface AiPptInstMapper extends BaseMapper<AiPptInst> {
}
