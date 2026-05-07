package cn.hollis.llm.mentor.agent.mapper;

import cn.hollis.llm.mentor.agent.entity.AiSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI会话 Mapper 接口
 */
@Mapper
public interface AiSessionMapper extends BaseMapper<AiSession> {
}
