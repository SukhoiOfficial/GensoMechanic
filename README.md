# Genso Mechanic

面向 Minecraft 1.21.1、NeoForge 与 Create 的模块化附属模组。本仓库包含三个独立构建的模块：

| 模块 | 版本 | 内容 |
| --- | --- | --- |
| [Genso Core](gensocore/) | 0.4.1 | 公共模组入口与模块依赖基础 |
| [Genso Mining](gensomining/README.md) | 0.1.6 | 动力采矿机、地域矿脉、地质勘探、采矿炸药及数据驱动的开采配方；[查看内容展示](gensomining/README.md#内容展示) |
| [Genso Metallurgy](gensometallurgy/README.md) | 0.1.2 | 工业高炉、铸造台、熔融金属与冶金加工；[工业高炉的思索教程](gensometallurgy/README.md#工业高炉的思索教程) |

Genso Mining 与 Genso Metallurgy 各自只依赖 Genso Core，Genso Core 依赖 Create。两项业务模块可分别安装，也可组合使用；安装时同时提供核心及 Create 的必要依赖。

各模块的功能说明、截图与使用规则收录在对应模块目录中：[采矿介绍](gensomining/README.md) · [冶金介绍](gensometallurgy/README.md)。展示图放在各模块介绍末尾。

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
./gradlew :gensomining:runData :gensometallurgy:runData
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
- 冶金模块的熔融金属流体贴图及动画元数据来自 Create: Metallurgy，保留其 [MIT 许可](LICENSES/Create-Metallurgy-MIT.txt)。第三方资源与依赖见 [CREDITS.md](CREDITS.md)。

## English summary

Genso Mechanic provides Genso Core, Genso Mining and Genso Metallurgy for Minecraft 1.21.1, NeoForge and Create. The mining module adds a kinetic miner, configurable regional veins and rock backgrounds, geological exploration, mining explosives and optional integrations. Metallurgy adds an industrial blast furnace, casting table, molten metals and processing recipes.

This project uses AI-assisted development for design discussions and parts of its code, documentation and tests. Sukhoi is the author and maintainer; the project's original models and textures are created by Sukhoi. Code is licensed under MIT, and the original models and textures under CC BY 4.0. See the linked license and disclosure documents for their scope.
