# Now-server（此刻后端）项目长期上下文

## 2026-08-14 训练模板按用户删除

- 新增 V18 `user_hidden_workout_plan`，记录用户隐藏的系统模板；系统模板仍保持全局启用，不会因为单个用户删除而影响其他账号。
- `DELETE /api/v1/workout-plans/{planId}` 对个人模板继续执行软删除，对系统模板改为写入当前用户隐藏关系；模板列表、首页推荐、动作明细和训练写入权限均排除当前用户已隐藏模板。
- 新增个人模板软删除、系统模板按用户隐藏及列表过滤测试；Maven 共 14 项测试通过。
- 新版 JAR 已重新构建并启动，Flyway V18 成功；真实验证个人模板删除后不可见、系统模板对当前用户隐藏但数据库仍为启用状态。临时账号、模板和隐藏关系已清理，`8081` 健康状态为 `UP`。

## 2026-08-14 训练计划账号同步

- 新增 V17 `user_training_config`：每个用户一份训练配置，保存训练模式、完整周期计划 JSON、客户端修改时间、服务端更新时间和递增修订号。
- 新增登录后可用的 `GET /api/v1/training-config` 与 `PUT /api/v1/training-config`；用户 ID 只取自 JWT，不接受客户端指定，数据按账号隔离。
- 服务端校验训练模式和周期 JSON，周期限制 1–30 天、配置上限 64 KiB；较旧客户端时间提交不会覆盖数据库中的较新版本，而是返回最新配置及 `applied=false` 供前端恢复并提示。
- 新增控制器归属、首次写入、旧版本冲突和周期上限测试；Maven 共 12 项测试通过。
- 已重新构建并启动新版 JAR，`8081` 健康状态为 `UP`，Flyway V16/V17 均成功；真实验证了注册、空配置读取、周期配置写入和旧版本拒绝覆盖，临时账号及关联数据已按精确 ID 清理。数据库凭据未写入仓库或本文档。

## 2026-08-14 账号鉴权与用户数据隔离

- 新增用户名、密码注册与登录、当前账号、刷新令牌和退出接口。密码使用 BCrypt 单向哈希；访问令牌为 15 分钟 HS256 JWT，刷新令牌为 30 天随机令牌且数据库只保存 SHA-256 摘要，刷新时执行轮换和旧令牌撤销。
- 新增 V16 迁移：创建 `app_user`、`auth_refresh_token`，并为训练模板和训练会话增加用户归属；训练会话使用 `(owner_user_id, client_record_id)` 唯一约束实现离线同步幂等。
- 系统模板为公开只读数据；登录用户只能管理自己的模板，只能读取自己的历史。动作库、动作分类、健康检查及系统模板列表继续允许公开读取，训练写入和历史详情必须登录。
- 未设置 `AUTH_JWT_SECRET` 时仅为本机开发临时生成密钥，服务重启后既有访问令牌失效但刷新令牌仍可换发；正式部署必须提供不少于 32 字节且稳定保管的密钥，不得写入仓库或本文档。
- AuthService、控制器和用户归属 SQL 已覆盖测试；Maven 共 8 项测试通过，`git diff --check` 通过。
- 当前 `8081` 仍运行修改前的健康 JAR。当前命令环境无法安全继承数据库启动变量，因此没有停止旧服务；V16 迁移、注册登录及真实数据库隔离仍需在具备原启动环境的终端安全重启后验收。

## 2026-08-14 训练历史完成组数

- `GET /api/v1/workouts/history` 的摘要新增 `completedSetCount`，按 `set_record.status = 'COMPLETED'` 聚合，供前端训练历史的日／周／月组数柱状图使用。
- 历史摘要查询对动作数改用去重计数，避免关联组记录后重复统计动作；最近记录上限由 20 条扩展为 200 条，覆盖前端近 6 个月的数据复盘窗口。
- 首页最近训练响应同步携带完成组数，既有调用方可以忽略该新增字段。
- 已新增摘要 SQL 结构测试，Maven 7 项测试、`git diff --check` 和 CodeGraph 同步均通过。
- 当前 `8081` 健康接口仍为 `UP`，但运行的是旧 JAR，历史响应尚无 `completedSetCount`。主任务命令环境未继承 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，为避免停止后无法恢复，没有终止健康服务；在具备原数据库环境变量的终端重启后再验证历史接口。
- Windows 锁定运行中 JAR，常规 Spring Boot 重打包无法重命名目标文件；源码与测试类已编译，待安全停服后重新执行 `mvn package` 生成可执行 JAR。

## 后端变更后的运行规则

- 每次后端代码、Flyway 迁移或接口发生修改并通过测试后，必须让当前运行服务同步生效：优先可靠热部署；当前运行方式不支持热部署时，重新构建 JAR 并安全重启。
- 重启完成后必须实际验证 `/api/v1/health` 以及本次修改的接口；不得让旧 JAR 长期占用 `8081`，也不得把“源码已修改”误当成“运行态已更新”。
- 重启前仅检查数据库环境变量是否存在且可继承，不读取、不输出、不记录任何数据库密码；无法保证凭据安全时保留健康旧进程并报告。
- 2026-08-14 已重新构建并重启为新 JAR，运行进程启动于 09:55；旧数据库按 V5 建立 Flyway 基线后成功迁移至 V15。`/api/v1/health`、历史列表和历史详情均实测返回 200。数据库凭据仅以启动进程临时环境使用，未写入仓库或本文档。

## 2026-08-14 删除模板与历史难度字段

- 训练模板 API 的请求、响应、查询和写入逻辑不再包含 `level`；新增 V15 迁移删除 `workout_plan.level`。
- 训练历史表及响应原本未定义难度字段，无需修改数据结构；动作库 `exercise.level` 继续保留，不受本次调整影响。

## 2026-08-14 训练历史详情接口

- 新增 `GET /api/v1/workouts/history/{sessionId}`，返回训练摘要、动作顺序、每组重量、次数、状态、完成时间和实际组间歇。
- 历史摘要接口保持不变；详情查询只返回 `COMPLETED` 训练，找不到记录时返回 404。

更新日期：2026-08-13

## 2026-08-13 GitHub 迁移告知

- 新增 `REPOSITORY_MIGRATION.md`，并在 `README.md` 顶部加入入口，明确 Gitee 为主仓库、GitHub 为备用镜像，提供旧 GitHub 克隆切换到 Gitee 的命令，概括迁移后的已提交后端能力，并明确当前未提交的分类开发不会随远端同步。

## 项目配对

- 前端本地目录：`D:\codes\Now\Now-web`
- 前端 Gitee 主仓库：<https://gitee.com/zem_wang/Now-web>
- 前端 GitHub 备用仓库：<https://github.com/wangzimin001/Now-web>
- 后端本地目录：`D:\codes\Now\Now-server`
- 后端 Gitee 主仓库：<https://gitee.com/zem_wang/Now-server>
- 后端 GitHub 备用仓库：<https://github.com/wangzimin001/Now-server>
- 默认分支：`main`

## 当前状态

后端为 Java 17 + Spring Boot 4.1 + MyBatis-Plus + MySQL + Flyway 工程。当前已完成健康检查、训练看板、训练方案、动作库、训练写入、训练历史、账号鉴权、刷新令牌、用户数据隔离和离线同步幂等接口。

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

1. 在具备数据库环境变量和稳定 `AUTH_JWT_SECRET` 的启动环境中应用 V16，并完成注册、登录、刷新、退出和双账号隔离运行态验收。
2. 增加刷新令牌定期清理、账号停用和密码修改能力。
3. 云端部署时配置 HTTPS、数据库备份和密钥轮换策略。

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

## 2026-08-12 CodeGraph 代码索引

- 已使用 CodeGraph 1.5.0 在后端仓库根目录建立 `.codegraph/` 本地代码索引，支持 Java、JavaScript 与 Maven XML 的符号和调用关系检索；
- 已通过 `codegraph explore` 验证 `completeWorkout`、控制器入口、服务方法和对应测试之间的调用路径可以正确返回；
- 新增 `codegraph.json`，明确排除 `src/main/resources/application*.yml`，运行配置不会进入图数据库；数据库凭据仍只允许通过环境变量使用；
- 根目录 `.gitignore` 已忽略 `.codegraph/`，索引数据库只保存在本机，不进入版本控制；
- 后续理解或定位代码时，仓库存在 `.codegraph/` 应优先使用 `codegraph explore`，源码修改后运行 `codegraph sync` 保持索引同步。

## 2026-08-13 胸部动作二级分类

- 新增 Flyway `V7__add_chest_regions.sql`，在动作表中以 JSON 数组保存胸部动作的“上胸／中胸／下胸”二级分类，一级 `category_code=chest` 保持不变；
- 新增可复现分类脚本 `scripts/classify-chest-regions.mjs`，按上斜、水平、下斜、绳索轨迹和俯卧撑身体角度为 163 个胸部条目审计；153 个资料充分的动作获得 1–2 个二级标签；
- 10 个源数据目标肌或轨迹不足的条目保持未分类，不强行推断；它们仍会出现在胸部全部列表，但不会出现在具体二级筛选中；
- `GET /api/v1/exercises` 新增可选 `chestRegion` 参数，使用 `JSON_CONTAINS` 筛选；响应新增结构化 `chestRegions` 数组，双标签动作只返回一条记录；
- 数据库迁移尚未在本机真实数据库执行，未读取或使用任何数据库凭据；待正常启动后端时由 Flyway 自动应用；
- Maven 测试通过（5 个测试、0 失败）；当前修改未提交，需配合前端完成手动联调验收。

## 2026-08-13 胸部二级筛选运行态诊断

- 对当前监听 `8081` 的服务分别请求上胸、中胸、下胸，三次均返回胸部全部 `163` 条且首批数据一致，响应也没有新版 `chestRegions` 字段；
- 已确认当前运行的是修改前的旧后端进程，旧接口会忽略陌生的 `chestRegion` 参数，因此前端页签虽已切换，服务端结果不会变化；
- 当前主任务环境不存在可安全继承的 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`，未读取、复制或输出任何数据库凭据，也未停止现有服务；
- 修复代码和 V7 迁移已就绪且 Maven 测试通过；需在原本设置数据库环境变量的终端安全重启后端，Flyway 应用 V7 后二级筛选才会生效。

## 2026-08-13 背部动作二级分类

- 新增 V8 迁移和 `scripts/classify-back-regions.mjs`，为全部 203 个背部动作增加“背阔肌／上背部／斜方肌／下背部”结构化标签，一级背部分类不变；
- 分类首先依据源数据目标：背阔肌 81、上背部 88、斜方肌 15、竖脊肌 19；再按划船、辅助菱形肌/后束、肩胛控制等信息补充第二标签，每个动作最多两个；
- 203 个动作资料均可判断，待确认动作 0 个；最终标签命中为背阔肌 159、上背部 112、斜方肌 15、下背部 19，其中 102 个动作为双标签；
- 动作接口新增 `backRegion` 参数和 `backRegions` 响应数组；V8 尚未实际连接数据库执行，等待后端安全重启后由 Flyway 应用。
- 2026-08-13 肩部二级分类：一级 `shoulders` 保持不变，新增“前束／中束／后束”标签、`shoulderRegion` 查询参数与 `shoulderRegions` 响应。`scripts/classify-shoulder-regions.mjs` 生成 V9；143 个动作中 130 个已分类（前束 74、中束 88、后束 29，61 个双标签），13 个资料不足动作保留未分类。Maven 5 项测试和 `git diff --check` 通过，CodeGraph 已同步；未读取凭据或重启旧服务，V9 待后续安全启动时应用。
- 2026-08-13 大腿二级分类：一级 `upper legs` 保持不变，新增“股四头肌／腘绳肌／臀肌／内收肌／外展肌”、`thighRegion` 查询参数及 `thighRegions` 响应。`scripts/classify-thigh-regions.mjs` 生成 V10；227 个动作全部完成分类（股四头肌 112、腘绳肌 67、臀肌 179、内收肌 6、外展肌 5，142 个双标签），无待确认动作。Maven 5 项测试和 `git diff --check` 通过；未读取凭据或重启旧服务，V10 待安全启动时应用。
- 2026-08-13 腰腹二级分类：一级 `waist` 保持不变，新增“上腹／下腹／腹斜肌／核心稳定／下背部”、`waistRegion` 查询参数及 `waistRegions` 响应。`scripts/classify-waist-regions.mjs` 生成 V11；169 个动作中 165 个已分类（上腹 78、下腹 37、腹斜肌 55、核心稳定 39、下背部 0，44 个双标签），4 个非腰腹或资料不足动作保留未分类。当前腰腹数据没有真正下背目标动作，不强行误标；下背训练仍在背部分类。Maven 5 项测试和 `git diff --check` 通过；未读取凭据或重启旧服务。
- 2026-08-13 上臂二级分类：一级 `upper arms` 保持不变，新增“二头内侧／二头外侧／三头”、`upperArmRegion` 查询参数及 `upperArmRegions` 响应。`scripts/classify-upper-arm-regions.mjs` 生成 V12；292 个动作中 240 个完成分类（二头内侧 81、二头外侧 60、三头 141，42 个二头双标签）。52 个锤式、反握、佐特曼、腕屈伸或翻举动作主要强调肱肌/肱桡肌，只保留在“全部”。Maven 5 项测试和 `git diff --check` 通过；未读取凭据或重启旧服务。
- 2026-08-13 小腿与小臂分类、手臂命名：新增 V13 小腿标签“腓肠肌／比目鱼肌／胫骨前肌／踝部稳定”和 V14 小臂标签“腕屈肌／腕伸肌／旋前旋后／握力”，接口新增 `calfRegion`、`forearmRegion` 及对应响应数组。59 个小腿动作全部分类（39/28/4/5，17 个双标签）；37 个小臂动作中 35 个分类（15/10/6/5，1 个双标签），2 个资料不足动作仅保留在全部。V14 同时把中文一级分类“上臂/前臂”更新为“大臂/小臂”，英文分类码不变。Maven 5 项测试和 `git diff --check` 通过；未读取凭据或重启旧服务。
