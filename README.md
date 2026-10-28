# Genso Mechanic

面向 Minecraft 1.21.1、NeoForge 与 Create 的模块化附属模组。本仓库包含两个独立构建的模块：

| 模块 | 版本 | 内容 |
| --- | --- | --- |
| Genso Core | 0.4.1 | 公共模组入口与模块依赖基础 |
| Genso Mining | 0.1.6 | 动力采矿机、地域矿脉、地质勘探、采矿炸药及数据驱动的开采配方 |

Genso Mining 依赖 Genso Core，Genso Core 依赖 Create。安装采矿模块时，需要同时安装两份模组 JAR 及 Create 的必要依赖。

## 采矿模块

- 使用 Create 动力驱动采矿机，支持物品与流体自动化接口。
- 主世界与下界矿脉具有群系偏好；可通过数据包配置矿脉、岩石背景及开采配方。
- 地质扫描器用于勘探；可选接入 Xaero 地图，显示发现的矿脉。
- 土制炸药与硝化炸药提供不同开采修正，润滑剂用于降低开采时间与应力。
- 提供 JEI 配方展示与 Jade 信息联动；Biomes O' Plenty 可扩展下界优势群系，并启用玫瑰石英晶簇。

更多内容见 [采矿模块说明](gensomining/README.md)。

## 开发环境与构建

| 组件 | 固定构建版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.250 |
| Create | 6.0.10，Maven 版本 `6.0.10-281` |
| Java | JDK 21 |
| Gradle Wrapper | 9.2.1 |
| ModDevGradle | 2.0.147 |

安装 JDK 21 并设置 `JAVA_HOME` 后，在仓库根目录依次执行：

```sh
./gradlew :gensomining:runData
./gradlew build
```

Windows PowerShell 使用 `./gradlew.bat` 替换 `./gradlew`。先等待资源生成完成，再运行构建。独立模组及源码 JAR 分别位于各模块的 `build/libs/`，根构建将它们汇总到 `build/distributions/`。

详细启动参数见 [构建与开发](docs/BUILDING.md)，回归测试入口见 [测试说明](docs/TESTING.md)。

## AI 辅助开发说明

本项目使用了 AI 编程工具（包括 OpenAI Codex）辅助方案讨论，以及部分代码、文档和测试的编写与整理。Sukhoi（GitHub：[@SukhoiOfficial](https://github.com/SukhoiOfficial)）负责项目需求、设计决策与维护。正式使用的原创模型和贴图由 Sukhoi 制作。

具体说明见 [AI_DISCLOSURE.md](AI_DISCLOSURE.md)。

## 许可

- 代码、数据配置与文本：采用 [MIT](LICENSE)，第三方自带许可的文件除外。
- 项目的原创模型和贴图：采用 [CC BY 4.0](LICENSE-ASSETS.md)，具体文件范围及署名方式见资源许可说明。
- 第三方依赖及所引用的资源仍适用其各自许可，见 [CREDITS.md](CREDITS.md)。

## English summary

Genso Mechanic provides Genso Core and Genso Mining for Minecraft 1.21.1, NeoForge and Create. The mining module adds a kinetic miner, configurable regional veins and rock backgrounds, geological exploration, mining explosives and optional integrations.

This project uses AI-assisted development for design discussions and parts of its code, documentation and tests. Sukhoi is the author and maintainer; the project's original models and textures are created by Sukhoi. Code is licensed under MIT, and the original models and textures under CC BY 4.0. See the linked license and disclosure documents for their scope.
