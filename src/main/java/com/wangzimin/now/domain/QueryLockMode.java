package com.wangzimin.now.domain;

/**
 * 定义数据库读取是否需要悲观写锁。
 *
 * <p>锁片段集中在枚举中，训练配置服务无需使用条件表达式拼接匿名 SQL 字符串。</p>
 */
public enum QueryLockMode {
    NONE(""),
    FOR_UPDATE(" FOR UPDATE");

    private final String sqlSuffix;

    /**
     * 创建查询锁模式。
     *
     * @param sqlSuffix 追加在完整查询末尾的受控 SQL 片段
     */
    QueryLockMode(String sqlSuffix) {
        this.sqlSuffix = sqlSuffix;
    }

    /**
     * 返回受控 SQL 后缀。
     *
     * @return 空字符串或 FOR UPDATE 子句
     */
    public String sqlSuffix() {
        return sqlSuffix;
    }
}
