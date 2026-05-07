package cn.hollis.llm.mentor.rag.generate;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

@Service
public class SqlQueryService {
    private ChatClient chatClient;

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    @PostConstruct
    public void init() {
        chatClient = ChatClient.builder(chatModel)
                .build();
    }

    private static final String TEXT_2_SQL_PROMPT = """
            # 角色
            你是一个SQL专家。请根据以下表结构信息将用户问题转换为SQL查询语句。特别注意，你只能查询，不能做修改、删除等操作。
            
            # 表结构信息
            
            {tables}
            
            # 用户问题
            
            {user_query}
            
            # 要求
            1. 只返回SQL语句，不需要包含任何解释和说明
            2. 确保SQL语法正确
            3. 使用上下文中提供的表名和字段名
            4. 如果根据所提供的表无法做查询，请直接返回空字符串""
            
            # 其他说明
            今天是:{today}
            """;

    private static final String TABLES = """
            CREATE TABLE "app"."staff_info"
            (
             "id" bigint NOT NULL ,
             "emp_id" varchar(64) NOT NULL ,
             "name" varchar(128) NOT NULL ,
             "job" varchar(128) NOT NULL ,
             "entry_time" date NOT NULL ,
             "gender" varchar(32) NOT NULL DEFAULT 'F'::character varying ,
             "birthday" date NOT NULL ,
             "educational_background" varchar(64) NOT NULL ,
             "director_id" varchar(64) ,
             "dept_id" varchar(64) NOT NULL ,
             "duty" varchar(512) ,
             "motto" varchar(1024) ,
             "pic_url" varchar(512) ,
            CONSTRAINT "pk_app_staff_info" PRIMARY KEY ("id")\s
            )
            WITH (
                FILLFACTOR = 100,
                OIDS = FALSE
            )
            ;
            ALTER TABLE "app"."staff_info" OWNER TO llmentor;
            COMMENT ON COLUMN "app"."staff_info"."emp_id" IS '工号';
            COMMENT ON COLUMN "app"."staff_info"."name" IS '姓名';
            COMMENT ON COLUMN "app"."staff_info"."job" IS '岗位';
            COMMENT ON COLUMN "app"."staff_info"."entry_time" IS '入职时间';
            COMMENT ON COLUMN "app"."staff_info"."gender" IS '性别：M表示女性，F表示男性';
            COMMENT ON COLUMN "app"."staff_info"."birthday" IS '出生年月日';
            COMMENT ON COLUMN "app"."staff_info"."educational_background" IS '学历：junior、undergraduate、master、doctor';
            COMMENT ON COLUMN "app"."staff_info"."director_id" IS '主管id';
            COMMENT ON COLUMN "app"."staff_info"."dept_id" IS '部门id';
            COMMENT ON COLUMN "app"."staff_info"."duty" IS '工作职责';
            COMMENT ON COLUMN "app"."staff_info"."motto" IS '个性签名';
            COMMENT ON COLUMN "app"."staff_info"."pic_url" IS '头像地址';
            
            
            /* 请确认以下SQL符合您的变更需求，务必确认无误后再提交执行 */
            
            CREATE TABLE "app"."dept_info"
            (
             "id" bigint NOT NULL ,
             "name" varchar(128) NOT NULL ,
             "owner_id" varchar(64) NOT NULL ,
             "parent_dept_id" bigint NOT NULL ,
             "desc" varchar(1024)\s
            )
            WITH (
                FILLFACTOR = 100,
                OIDS = FALSE
            )
            ;
            ALTER TABLE "app"."dept_info" OWNER TO llmentor;
            COMMENT ON COLUMN "app"."dept_info"."id" IS '部门id';
            COMMENT ON COLUMN "app"."dept_info"."name" IS '部门名称';
            COMMENT ON COLUMN "app"."dept_info"."owner_id" IS '主管工号';
            COMMENT ON COLUMN "app"."dept_info"."parent_dept_id" IS '父级部门id';
            COMMENT ON COLUMN "app"."dept_info"."desc" IS '部门描述';
            COMMENT ON TABLE "app"."dept_info" IS '部门信息';
            
            """;


    public String text2sql(String query) {
        PromptTemplate promptTemplate = new PromptTemplate(TEXT_2_SQL_PROMPT);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Prompt prompt = promptTemplate.create(Map.of("user_query", query, "tables", TABLES, "today", sdf.format(new Date())));

        String sql = chatClient.prompt(prompt)
                .call()
                .content();

        System.out.println("sql:" + sql);

        return sql;
    }

    public Object sqlQuery(String query, Class clazz) {
        if (StringUtils.isBlank(query)) {
            return null;
        }
        String sql = text2sql(query);
        if (StringUtils.isBlank(sql)) {
            return null;
        }
        return jdbcTemplate.queryForObject(sql, clazz);
    }
}
