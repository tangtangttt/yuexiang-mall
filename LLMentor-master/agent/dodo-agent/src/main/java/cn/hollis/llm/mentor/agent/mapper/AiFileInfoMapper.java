package cn.hollis.llm.mentor.agent.mapper;

import cn.hollis.llm.mentor.agent.entity.AiFileInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件信息 Mapper 接口
 */
@Mapper
public interface AiFileInfoMapper extends BaseMapper<AiFileInfo> {
}
