# 作者与第三方说明 / Credits

## 项目作者

- **Sukhoi / SukhoiOfficial**：项目需求、玩法与设计决策、维护，以及本项目正式使用的原创模型和贴图。
- GitHub：<https://github.com/SukhoiOfficial>
- 项目地址：<https://github.com/SukhoiOfficial/GensoMechanic>

开发中使用 AI 辅助，具体范围见 [AI_DISCLOSURE.md](AI_DISCLOSURE.md)。

## 运行依赖与联动

项目基于 Minecraft、NeoForge 和 Create 的开发接口，使用 Create 生态中的 Registrate、Flywheel 与 Ponder。可选联动包括 JEI、Jade、Xaero 地图，以及 Biomes O' Plenty 与其必要依赖。

第三方项目的名称、代码、模型和贴图仍属于各自的权利人，并适用各自的许可。本仓库的 MIT 与 CC BY 4.0 声明不重新许可第三方内容。对 `minecraft:`、`create:` 等资源标识的引用，不表示相应外部资源由本项目创作或在本仓库中重新授权。

构建脚本通过依赖管理获取第三方库；第三方模组 JAR 不属于本项目的源码或美术资源。Gradle Wrapper 等自带许可声明的工具文件继续适用其原有许可。

## Create: Metallurgy 流体材质

- 作者/版权：**Luc_Creeper74**，Copyright (c) 2024 Luc_Creeper74。
- 来源：[Lucreeper74/Create-Metallurgy](https://github.com/Lucreeper74/Create-Metallurgy)。
- 许可：[MIT 全文](LICENSES/Create-Metallurgy-MIT.txt)，[上游固定版本许可](https://github.com/Lucreeper74/Create-Metallurgy/blob/5fb32edce2753d72150dcf798bebb6d5940d29d0/LICENSE)。
- 上游路径：`src/main/resources/assets/createmetallurgy/textures/fluid/{metal}/`。
- 本项目路径：`gensometallurgy/src/main/resources/assets/gensometallurgy/textures/fluid/molten_metals/{metal}/`。
- 金属：`brass`、`copper`、`gold`、`iron`、`netherite`、`zinc`；每种的 `still.png`、`flowing.png` 及两份 `.png.mcmeta`，共 24 文件。
- 2026-10-29 核对上游提交 `5fb32edce2753d72150dcf798bebb6d5940d29d0`：24 文件逐字节一致；只调整存放路径和引用命名空间。完整许可与专门来源说明同时包含在冶金 JAR 中。

这些文件保留上游 MIT，不纳入本项目原创美术的 CC BY 4.0。引用材质不表示 Create: Metallurgy 是本模块的运行依赖或为本项目背书。

## 原创美术资源署名示例

> Models and textures from Genso Mechanic by Sukhoi / SukhoiOfficial, licensed under CC BY 4.0. Source: https://github.com/SukhoiOfficial/GensoMechanic. License: https://creativecommons.org/licenses/by/4.0/. Changes: describe your modifications here, or state that the assets are unchanged.

使用者应根据实际情况说明修改内容，并保留适用的署名和许可信息。完整范围见 [LICENSE-ASSETS.md](LICENSE-ASSETS.md)。
