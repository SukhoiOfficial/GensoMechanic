# 构建与开发

本仓库使用 Java 21、Minecraft 1.21.1、NeoForge 21.1.250、Create 6.0.10（Maven `6.0.10-281`）、Gradle Wrapper 9.2.1 与 ModDevGradle 2.0.147。版本参数位于根目录 `gradle.properties`。

## 构建

安装 JDK 21，将 `JAVA_HOME` 指向该 JDK。在仓库根目录依次执行，等待第一条命令成功结束后再运行第二条：

```sh
./gradlew :gensomining:runData
./gradlew build
```

Windows PowerShell：

```powershell
./gradlew.bat :gensomining:runData
./gradlew.bat build
```

首次构建需要联网下载 Gradle 和声明的依赖。使用仓库附带的 Wrapper，无需另行安装 Gradle。资源生成写入 `gensomining/src/generated/resources/`；其中的正式资源应随源码提交，`.cache` 不提交。

构建输出包括：

- `gensocore/build/libs/GensoCore-0.4.1.jar` 及其源码 JAR。
- `gensomining/build/libs/GensoMining-0.1.6.jar` 及其源码 JAR。
- 根构建汇总目录 `build/distributions/`。

Genso Mining 需要 Genso Core，Genso Core 需要 Create。测试代码与测试结构位于独立源集，不进入上述生产 JAR 或源码 JAR。`build` 会编译测试源码，但不会执行 GameTest，执行入口见 [TESTING.md](TESTING.md)。

## 开发启动

```sh
./gradlew :gensomining:runClient
./gradlew :gensomining:runServer
```

采矿开发启动同时加载核心与采矿模块。客户端与服务端运行数据分别保存在 `gensomining/run/client/`、`gensomining/run/server/`，这些本地目录不提交。

JEI 与 Jade 默认加入开发运行环境。可通过 `-PwithoutJei`、`-PwithoutJade` 分别关闭。若启动测试客户端，需按 [TESTING.md](TESTING.md) 选择与依赖组合匹配的检查入口。

## Xaero 地图

```sh
./gradlew :gensomining:runClient -PwithXaero
```

开发依赖固定为 Xaero's Minimap 26.5.0、Xaero's World Map 1.46.0（NeoForge / Minecraft 1.21.1）及其携带的 XaeroLib 1.7.3。添加 `-PwithoutXaeroMinimap` 或 `-PwithoutXaeroWorldmap` 可检查只安装其中一种地图的情况。

编译所需的 XaeroLib 从依赖中提取到构建目录，仅用于开发；它与地图模组均不会被打包进 Genso 模组 JAR。

## Biomes O' Plenty

从相应模组的官方发行渠道下载以下 NeoForge / Minecraft 1.21.1 开发依赖，放入仓库之外的本地目录：

| 模组 | 版本 |
| --- | --- |
| Biomes O' Plenty | 21.1.0.14 |
| TerraBlender | 4.1.0.8 |
| GlitchCore | 2.1.0.2 |

保留原始发行文件名，然后显式传入目录路径：

```sh
./gradlew :gensomining:runClient -PwithBop -PbopModsDir=/path/to/bop-development-mods
```

将示例路径替换为自己的目录；包含空格时按所用终端的规则为参数加引号。`-PwithBop` 必须与 `-PbopModsDir` 配合使用。构建脚本只接受上述固定版本，并检查三个 JAR 是否齐全。这些 JAR 仅加入本地开发运行环境，不上传到仓库，也不进入本项目的发行包。

普通构建及无 BOP 的开发启动不需要这些本地文件。没有安装 BOP 时，玫瑰石英晶簇及其联动配置不会启用。
