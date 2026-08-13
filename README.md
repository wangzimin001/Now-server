# 此刻后端（Now-server）

> 代码托管已于 2026-08-11 切换：Gitee 是主仓库，GitHub 仅作为备用镜像。仍使用旧 GitHub 克隆的电脑，请先阅读 [仓库迁移与后续变更](./REPOSITORY_MIGRATION.md)，再继续拉取或提交代码。

这是“此刻”健身记录 App 的独立后端仓库，已提供训练看板、动作库、训练模板和训练记录等业务 API，并通过 Flyway 管理业务表与动作数据。

技术栈为 Java 17、Spring Boot 4.1、MyBatis-Plus 3.5.17、MySQL 8.4 LTS 和 Flyway。默认端口为 `8081`，基础健康接口为 `GET /api/v1/health`。

本地启动前，通过环境变量提供数据库连接信息；密码不得提交到仓库：

```powershell
$env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/now_app?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = '<本机 MySQL 密码>'
$env:FLYWAY_ENABLED = 'true'
mvn spring-boot:run
```

当前基线迁移不创建业务表。等训练记录、动作库和账号模型确认后，新增 `V2__...sql`，不要修改已经在其他环境执行过的迁移。
