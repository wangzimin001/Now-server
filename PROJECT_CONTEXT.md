# Now-server（此刻后端）项目长期上下文

更新日期：2026-08-08

## 项目配对

- 前端本地目录：`C:\编程\Now\Now-web`
- 前端仓库：<https://github.com/wangzimin001/Now-web>
- 后端本地目录：`C:\编程\Now\Now-server`
- 后端仓库：<https://github.com/wangzimin001/Now-server>
- 默认分支：`main`

## 当前状态

后端已初始化为 Java 17 + Spring Boot 4.1 + MyBatis-Plus + MySQL + Flyway 工程。当前只有 `GET /api/v1/health` 和空的 Flyway 基线迁移；业务 API、业务数据库表、登录鉴权均尚未设计。

所有数据库凭据必须通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 提供，绝不能写入代码、文档或 Git。

## 交接规则

每次提交后端代码时，必须在同一次提交中同步更新本文件，记录当前状态、已完成内容、未完成事项和明确的下一步。切换电脑时应同时克隆并检查 `Now-web` 和 `Now-server`。

## 恢复工作

```powershell
git clone https://github.com/wangzimin001/Now-server.git
cd Now-server
git status --short --branch
java -version
mvn -v
mvn test
```

## 下一步

1. 先在前端确定训练记录 MVP 的页面流程。
2. 再设计用户、动作、训练计划、训练场次和组记录表。
3. 通过新的 Flyway 迁移和版本化 REST API 实现第一批业务接口。
