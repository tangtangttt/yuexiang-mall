-- 商城 AI 客服：会话记录 + 知识库（MySQL 8+，按需执行）
-- 知识检索 MVP 使用 LIKE / FULLTEXT，未使用向量列，避免与 MySQL 版本强绑定

CREATE TABLE IF NOT EXISTS `customer_service_session` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` VARCHAR(64) NOT NULL COMMENT '会话ID',
  `member_id` BIGINT NOT NULL COMMENT '会员用户ID（shop_user.id）',
  `agent_type` VARCHAR(32) DEFAULT 'mall_customer_service' COMMENT '智能体类型',
  `question` TEXT COMMENT '用户问题',
  `answer` LONGTEXT COMMENT 'AI最终回复',
  `thinking` TEXT COMMENT '思考过程汇总',
  `tools_used` VARCHAR(500) DEFAULT NULL COMMENT '工具名，逗号分隔',
  `reference_data` JSON DEFAULT NULL COMMENT '结构化参考数据',
  `recommend_questions` JSON DEFAULT NULL COMMENT '推荐问题JSON数组',
  `first_response_time` BIGINT DEFAULT NULL COMMENT '首包耗时ms',
  `total_response_time` BIGINT DEFAULT NULL COMMENT '总耗时ms',
  `status` TINYINT DEFAULT 1 COMMENT '1正常 0删除',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`),
  KEY `idx_member_time` (`member_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服会话明细';

CREATE TABLE IF NOT EXISTS `customer_knowledge` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `title` VARCHAR(200) NOT NULL,
  `content` MEDIUMTEXT NOT NULL,
  `category` VARCHAR(50) DEFAULT NULL,
  `tags` VARCHAR(200) DEFAULT NULL,
  `view_count` INT DEFAULT 0,
  `helpful_count` INT DEFAULT 0,
  `status` TINYINT DEFAULT 1 COMMENT '1启用 0禁用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  FULLTEXT KEY `ft_title_content` (`title`, `content`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI客服知识库';

-- 可选：初始化几条知识（执行前请确认表为空或自行调整）
-- INSERT INTO customer_knowledge (title, content, category, tags, status) VALUES
-- ('退换货政策','自签收之日起7天内，商品未使用且包装完好可申请退换货；特殊商品以详情页说明为准。','return','退换货,售后',1);
