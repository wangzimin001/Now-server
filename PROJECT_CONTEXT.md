# Now-server（此刻后端）项目长期上下文

更新日期：2026-08-11

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

## 2026-08-11 动作数据库接入

- 已根据 `D:\codes\Now\exercises-dataset-main` 导入 1,324 个动作，生成脚本为 `scripts/generate-exercise-library.mjs`，生成迁移为 `V4__exercise_dataset.sql`；最近一次生成检查未发现未翻译的英文词元；
- `V3__extend_exercise_library.sql` 扩展动作表，保存来源动作 ID、英文名、中文分类、器械、目标肌群、辅助肌群、中文/英文动作要领、图片和 GIF 地址、媒体署名及常用度排序；
- 分类顺序为胸部、背部、肩部、大腿、上臂、腰腹、小腿、前臂、有氧、颈部；分类数量合计 1,324，查询默认按分类和常用度排序；
- 新增 `GET /api/v1/exercise-categories`，`GET /api/v1/exercises` 支持 `category`、`keyword`、`page`、`limit` 并返回分页结果；
- 本机 `now_app` 已按 V1、V2、V3、V4 顺序完成迁移，动作总数和各分类数量已通过 MySQL 查询核对；数据库凭据仅通过临时环境变量使用，没有写入代码、文档或 Git；
- `mvn test` 通过（2 个测试、0 失败），`mvn -DskipTests package` 成功，服务监听 `8081`，Actuator 健康状态实测为 `UP`；
- 动作元数据、程序和文字属于 MIT 范围；图片与 GIF 属于 Gym visual，不属于 MIT。应用继续展示 `© Gym visual — https://gymvisual.com/`，公开或商业发布前必须确认单独媒体授权。

## 2026-08-11 训练模板写接口与训练记录落库

- 新增 `WorkoutService` 承担写操作，模板查询仍由 `FitnessQueryService` 负责，避免读写逻辑继续集中在单个类中；
- 新增 `POST /api/v1/workout-plans`、`PUT /api/v1/workout-plans/{planId}`、`DELETE /api/v1/workout-plans/{planId}`；删除采用 `is_active = FALSE` 软删除，历史训练保留原模板引用；
- 新增 `POST /api/v1/workouts`，在一个事务中写入 `workout_session`、`session_exercise` 和 `set_record`，服务端按已完成组重新计算训练容量，未完成组保存为 `SKIPPED`；
- 模板写请求校验名称、预计时长、动作数量、组数、次数和休息秒数；更新已停用模板返回不存在，模板动作替换失败时事务会回滚；
- 删除全部模板时首页查询会返回“暂无训练模板”占位数据，不再因单行查询为空而报错；
- 现有 V2 表结构已经覆盖上述数据，不需要新增数据库迁移；
- `mvn test` 通过：4 个测试、0 失败；真实 MySQL 已验证模板创建、编辑、停用和训练完成事务，验证产生的临时模板、动作组和训练历史均已按精确 ID 清理；
- 最新 JAR 已重新启动在 `8081`，`/actuator/health` 为 `UP`，活动模板 3 个、历史 3 条，与联调前一致；数据库凭据没有写入源码、文档或 Git。

## 2026-08-11 训练模板使用统计与动作 GIF

- `GET /api/v1/workout-plans` 新增模板级 `usageCount` 和 `lastUsedAt`，分别表示已完成训练的累计次数与最近一次完成时间；从未使用的模板返回 `0` 和 `null`；
- 使用统计通过按 `plan_id` 独立聚合 `workout_session` 中 `COMPLETED` 记录后再关联模板，避免与模板动作明细连接时重复计数；
- 模板动作响应新增 `gifUrl`，来自动作表的 GIF 地址，供训练台纵向模板卡片展示；
- 模板与 `plan_exercise` 仍只保存动作 ID 和训练参数，不保存 GIF 地址；查询时通过 `exercise` 动作库关联取得 GIF；
- 新增 `V5__map_legacy_plan_exercises_to_dataset.sql`，把 V2 的 8 个旧模板动作 ID 映射到 V4 正式动作库 ID；历史 `session_exercise` 快照和旧动作记录保持不变；
- 新增 `FitnessQueryServiceTest` 覆盖使用次数、最近使用时间、GIF 映射与独立聚合 SQL；`mvn test` 通过（5 个测试、0 失败）；
- 当前监听 `8081` 的仍是修改前启动的旧 JAR，因此当前进程的模板响应尚无新字段；下次按安全方式提供数据库环境变量并重启后端时，Flyway 会应用 V5，新的统计和 GIF 查询随新进程生效；
- `mvn -DskipTests package` 已完成编译与普通 JAR 生成，但 Spring Boot 重打包因当前运行中的旧 JAR 被 Windows 锁定而无法重命名；这是进程文件锁，不是编译或测试失败，停止旧进程后可重新打包。
