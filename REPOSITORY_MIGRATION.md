# 仓库迁移与后续变更

## 远端已切换

自 2026-08-11 起，本项目使用以下远端分工：

- Gitee：主仓库，日常拉取和推送均以它为准；
- GitHub：备用镜像，用于异地备份和兼容旧电脑；
- 默认分支：`main`；
- 本地约定：`origin` 指向 Gitee，`github` 指向 GitHub。

后端主仓库：`https://gitee.com/zem_wang/Now-server.git`

后端备用仓库：`https://github.com/wangzimin001/Now-server.git`

## 已有 GitHub 克隆如何迁移

执行前先提交或妥善保留当前电脑的本地修改。然后在原后端仓库目录运行：

```powershell
git status --short --branch
git remote rename origin github
git remote add origin https://gitee.com/zem_wang/Now-server.git
git fetch origin
git switch main
git branch --set-upstream-to=origin/main main
git pull --ff-only origin main
git remote -v
```

如果本地已经存在名为 `github` 的远端，不要再次执行 `git remote rename`；先用 `git remote -v` 核对，再确保 `origin` 指向 Gitee。

全新电脑可直接执行：

```powershell
git clone https://gitee.com/zem_wang/Now-server.git
```

## GitHub 停更期间发生了什么

从原 GitHub 基线到本说明发布前，后端已提交以下主要工作：

- 导入中文动作数据集；
- 新增训练模板的创建、更新和停用接口；
- 新增训练记录写入及逐组记录保存；
- 训练模板查询增加使用次数、最近使用时间和动作 GIF 关联信息。

当前开发电脑上还存在尚未提交的动作二级分类、组间歇字段和相关迁移脚本。这些内容不在任何远端，另一台电脑暂时无法通过 `git pull` 获得；应在完成验证并提交推送后再切换电脑继续开发。

完整状态以 `PROJECT_CONTEXT.md` 和 `git status` 为准。

## 重要同步边界

远端只能同步已经提交并推送的内容。换电脑前必须先检查：

```powershell
git status --short --branch
```

不要把数据库密码、访问令牌或其他凭据写入文档、远端地址或提交记录。
