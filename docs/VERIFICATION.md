# 公开源码副本验证

2026-10-28，Genso Core 0.4.1 / Genso Mining 0.1.6，Windows、JDK 21。

| 检查 | 结果与范围 |
| --- | --- |
| `:gensomining:runData` | 通过；重新生成的正式资源与整理前一致 |
| 根 `build` | 通过；独立构建核心与采矿的生产 JAR 和源码 JAR，编译筛选后保留的测试 |
| `:gensomining:runGameTestServer -PwithGameTests -PgameTestNamespace=gensomining_lubricants` | 无 BOP 环境下 2 项必需测试通过；覆盖条件解析、网络往返、17 份生效开采配方、流体标签重载和机器输入切换 |
| `:gensomining:runClient -PwithGameTests -PbackgroundClientCheck` | `BACKGROUND CLIENT PASS`；主菜单自动检查、JEI 数据和输出分组、资源重载及原生模型对照通过 |
| 上传范围 | 正式源码与资源共 241 个文件与整理前逐字节一致；测试代码与结构不进入生产和源码 JAR；许可证、署名及 AI 辅助说明进入打包产物 |

本次整理调整构建入口、仓库文件范围、许可与文档，没有修改游戏功能。没有运行历史分布仿真，也没有进行游戏内人工视觉验收。其他保留的测试已编译，本次未全部重新执行；运行入口见 [TESTING.md](TESTING.md)。

GitHub 工作流配置负责资源生成、生成资源一致性检查和构建；本地通过不代表远端工作流已经通过，远端结果以仓库 Actions 页面为准。
