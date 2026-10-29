# 测试说明

公开测试用于回归当前实现的机器接口、资源配置与开采机制。测试源集是 `gensomining/src/gameTest`，生产和源码发行 JAR 均不包含测试代码或测试结构。

根 `build` 会编译这些测试，但不会执行 GameTest。执行游戏测试需显式添加 `-PwithGameTests`。先运行 `:gensomining:runData`，完成资源生成后再执行以下命令。Windows 使用 `./gradlew.bat` 替换 `./gradlew`。

## 专用服务器 GameTest

```sh
./gradlew :gensomining:runGameTestServer -PwithGameTests -PgameTestNamespace=gensomining_lubricants
```

根据修改范围选择命名空间：

| 命名空间 | 对应检查 |
| --- | --- |
| `gensomining` | 采矿机结构、库存与流体接口等机器行为 |
| `gensomining_background` | 岩石背景定义、匹配、共享容量、扫描报告、机器行为与重载，以及顶部物品仓口 |
| `gensomining_explosives` | 炸药点燃、实体状态、开采修正及相关配方 |
| `gensomining_nether` | 下界矿脉定义、群系配置、可选联动、概率产物与 Create 原生配方接入 |
| `gensomining_lubricants` | 植物油标签条件、服务端重载、同步与机器流体输入切换 |

下界联动检查可分别在无 BOP 和有 BOP 环境运行。有 BOP 时追加 `-PwithBop -PbopModsDir=/path/to/bop-development-mods`，所需版本见 [BUILDING.md](BUILDING.md)。

GameTest 在 `gensomining/run/gametest/` 中创建独立测试环境。测试会操作该环境中的机器、库存和临时数据包，勿将个人存档放进此目录。检查 Gradle 退出结果和 GameTest 的必需测试结果；成功编译不代表游戏测试通过。

## 客户端资源检查

```sh
./gradlew :gensomining:runClient -PwithGameTests -PbackgroundClientCheck
```

此入口在客户端主菜单加载完成后检查目录同步、JEI 数据与输出分组、模型资源，并执行资源重载；结束后自动退出。默认开发依赖中的 JEI 应保持启用。日志中的 `BACKGROUND CLIENT PASS` 表示该入口完成，`BACKGROUND CLIENT FAIL` 表示检查失败。不能只根据进程正常退出判断成功。

炸药资源的独立检查入口：

```sh
./gradlew :gensomining:runClient -PwithGameTests -PexplosiveClientCheck
```

这些检查不操作客户端界面，也不完成游戏内人工视觉验收。机器尺寸、遮挡、运动观感等可见结果仍需单独在游戏内确认。

## 保留范围

测试目录保留机器、顶部仓口、润滑条件、炸药、岩石背景、扫描报告、下界机制及客户端资源检查。历史矿脉分布仿真、候选算法比较、统计样本和绘图输出不属于这组回归测试，不以历史分布指标作为当前版本验收要求。

本文件说明测试用途和运行方式，不将任何尚未执行的检查标记为通过。应在具体变更中记录执行命令、结果和验证边界。

## 冶金测试入口

`gensometallurgy/src/gameTest` 保留机器接口、库存/流体边界、Jade 和客户端检查；测试源码及结构不进入发行 JAR。
下列入口用于按改动需要选择运行，本次开源准备没有执行这些游戏测试。

```sh
./gradlew :gensometallurgy:runGameTestServer -PwithGameTests -PgameTestNamespace=gensometallurgy,gensometallurgy_jade
./gradlew :gensometallurgy:runGameTestServer -PwithGameTests -PmetallurgyNoRecipes -PwithoutJei -PwithoutJade -PgameTestNamespace=gensometallurgy_empty
./gradlew :gensometallurgy:runClient -PwithGameTests -PmetallurgyClientCheck -PmetallurgyNoRecipes
```

先单独完成 `:gensometallurgy:runData`。无查看器客户端可追加 `-PwithoutJei -PwithoutJade`。
空配方参数同时排除内置与测试配方，用于储存/展示的历史空数据场景，不是正式版本的默认行为；此模式禁止 Jar 打包。
客户端检查自动创建独立世界、推进思索、绘制并重载，完成后退出，以 `METALLURGY CLIENT PASS` 为结果入口；不是人工验收。
公开副本不包括依赖未公开农业发行包的共存夹具；不改变生产代码和数据。
