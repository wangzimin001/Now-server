# Now-server（此刻后端）项目长期上下文

更新日期：2026-08-10

## 项目配对

- 前端本地目录：`D:\codes\Now\Now-web`
- 前端仓库：<https://github.com/wangzimin001/Now-web>
- 后端本地目录：`D:\codes\Now\Now-server`
- 后端仓库：<https://github.com/wangzimin001/Now-server>
- 默认分支：`main`

## 当前状态

后端已初始化为 Java 17 + Spring Boot 4.1 + MyBatis-Plus + MySQL + Flyway 工程。当前已完成健康检查、训练看板、训练方案、动作库和训练历史查询 API，并通过 Flyway 建立训练 MVP 业务表；登录鉴权和训练写入/同步尚未实现。

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

1. 新增开始训练、保存组记录和完成训练的写接口。
2. 前端本地优先保存，网络恢复后再幂等同步服务端。
3. 在训练记录闭环稳定后再设计“此刻”自己的账号和鉴权。

## 2026-08-10 本机同步记录

- 本机前端目录：`D:\codes\Now\Now-web`；
- 本机后端目录：`D:\codes\Now\Now-server`；
- 后端已同步到提交 `6751f83`，同步前工作区干净；
- 已使用 Microsoft OpenJDK 17.0.10 执行 `mvn test`；
- 测试结果：1 个测试通过，0 失败，构建成功；
- Maven 依赖通过阿里云镜像下载；
- MySQL 8.0.33 客户端可用，`MySQL80` 服务正在 3306 端口运行；
- 当前仍只有健康检查和空基线迁移，下一步先验证前端运行，再设计训练记录 MVP。

## 2026-08-10 本机运行记录

- 后端已使用 Microsoft OpenJDK 17.0.10 和 Maven 启动，监听 `8081` 端口；
- `GET http://127.0.0.1:8081/api/v1/health` 已实际返回 `UP`；
- MuMu Android 15 模拟器已验证可以连接宿主机 `10.0.2.2:8081`；手机端调用本机后端时应使用该宿主机地址，不能使用模拟器自己的 `127.0.0.1`；
- 当前启动进程未设置 `DB_PASSWORD`。由于数据源是延迟连接，HTTP 服务可以启动，但 `GET /actuator/health` 会因 MySQL 拒绝空密码而返回 `DOWN`；自定义健康接口的 `UP` 不代表数据库已连接；
- 需要数据库功能时，在启动后端的同一个 PowerShell 窗口执行 `$env:DB_PASSWORD = Read-Host '请输入本机 MySQL 密码（仅当前窗口有效）'`，再执行 `mvn spring-boot:run`；不要把密码直接写在命令、配置文件、文档或 Git 中；
- Maven 首次运行依赖已通过阿里云镜像下载完成，后续启动会更快；
- 下一步先确认本机 MySQL 中存在 `now_app` 数据库，再启用 Flyway 并实现第一批业务表和接口。

## 2026-08-10 MySQL 凭据核对

- MySQL Workbench 的本机连接配置确认为 `root@localhost:3306`；
- 用户提供的密码已仅在临时进程环境中进行验证，本机 MySQL 返回 `Access denied`；密码内容未写入文件、日志或 Git；
- 当前后端 HTTP 服务仍可运行，但数据库连接和 Actuator 数据库健康检查仍为 `DOWN`；
- 下一步需要用户确认正确密码，或明确授权重置本机 MySQL `root` 密码后，再创建/检查 `now_app` 数据库并重启后端。

## 2026-08-09 开发进展

- 已新增 `V2__training_mvp.sql`，建立动作、训练方案、方案动作、训练场次、场次动作和组记录六类业务表，并加入本地演示数据。
- 已提供 `GET /api/v1/dashboard`、`GET /api/v1/workout-plans`、`GET /api/v1/exercises`、`GET /api/v1/workouts/history`。
- Spring Boot 4 已改用 `spring-boot-starter-flyway`，本机 `now_app` 数据库迁移成功。
- Maven 测试通过，服务运行在 `8081`。

### 与“不叉手”项目的隔离约定

- “不叉手”继续使用 `8080` 和 `not_stand_by`；“此刻”使用 `8081` 和 `now_app`。
- “此刻”接口统一放在 `/api/v1`，但不复用“不叉手”的 DTO、会话令牌、数据表或运行配置。
- 数据库密码只通过 `DB_PASSWORD` 传入，绝不写入仓库。

### 当前下一步

1. 新增开始训练、保存组记录和完成训练的写接口。
2. 前端本地优先保存，网络恢复后再幂等同步服务端。
3. 在训练记录闭环稳定后再设计“此刻”自己的账号和鉴权。
