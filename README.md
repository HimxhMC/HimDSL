# HimDSL

一个为 Minecraft Java 版服务端设计的领域特定语言（DSL）插件。用接近 Java 的语法编写事件监听、定时任务、GUI 界面、数值计算与状态机，无需编写和编译 Java 插件。

A domain-specific language (DSL) plugin for Minecraft Java servers. Write event listeners, scheduled tasks, GUI menus, computations, and state machines in Java-like syntax — no Java plugin required.

---

## 环境要求 / Requirements

- **服务端**：Bukkit / Spigot / Paper 1.21+（推荐 Paper 1.21 以上）
- **Java**：25 或更高
- **可选依赖**（缺省时功能自动降级）：
  - [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) —— `papi(...)` 系列函数
  - HimDungeons —— `$boss(...)$` 与部分玩家占位符
  - WorldEdit —— 部分世界占位符

- **Server**: Bukkit / Spigot / Paper 1.21+ (Paper 1.16.5+ recommended)
- **Java**: 25 or later
- **Optional dependencies** (features degrade gracefully when missing):
  - [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) — `papi(...)` family
  - HimDungeons — `$boss(...)$` and some player placeholders
  - WorldEdit — some world placeholders

---

## 安装 / Installation

1. 下载 `HimDSL-x.y.z.jar`。
2. 放入服务器的 `plugins/` 目录。
3. 重启服务器。
4. 看到控制台输出 `[HimDSL] HimDSL enabled.` 即安装成功。

插件会自动创建数据目录 `plugins/HimDSL/`，所有脚本都放在这里。

1. Download `HimDSL-x.y.z.jar`.
2. Place it in the server's `plugins/` folder.
3. Restart the server.
4. Seeing `[HimDSL] HimDSL enabled.` means success.

The data folder `plugins/HimDSL/` is created automatically; all scripts live here.

---

## 快速开始 / Quick Start

在 `plugins/HimDSL/` 下创建 `hello.himdsl`：

Create `hello.himdsl` inside `plugins/HimDSL/`:

```himdsl
int main() {
    runcmd("CONSOLE", "say Hello, HimDSL!");
}
```

执行：

Run:

```
/himdsl run hello.himdsl
```

所有在线玩家会看到 `Hello, HimDSL!` 广播。

Every online player sees the `Hello, HimDSL!` broadcast.

---

## 命令 / Commands

| 命令 | 说明 | 权限节点 |
|------|------|---------|
| `/himdsl compile <相对路径>` | 语法检查，不执行 | `himdsl.use` |
| `/himdsl run <相对路径> [参数...]` | 执行脚本，参数按顺序传入 `main` | `himdsl.run` |
| `/himdsl debug on\\|off` | 打开 / 关闭详细日志 | `himdsl.debug` |
| `/himdsl debug codeon\\|codeoff` | 打开 / 关闭运行前打印脚本内容 | `himdsl.debug` |

| Command | Description | Permission |
|---------|-------------|-----------|
| `/himdsl compile <path>` | Syntax check only | `himdsl.use` |
| `/himdsl run <path> [args...]` | Execute the script; args flow to `main` | `himdsl.run` |
| `/himdsl debug on\\|off` | Toggle verbose logs | `himdsl.debug` |
| `/himdsl debug codeon\\|codeoff` | Toggle script-text printing before each run | `himdsl.debug` |

**路径相对于 `plugins/HimDSL/`**，且不允许逃逸到插件目录之外。

**Paths are relative to `plugins/HimDSL/`** and cannot escape the plugin directory.

---

## 权限 / Permissions

| 节点 | 默认 | 说明 |
|------|------|------|
| `himdsl.use` | OP | 允许 `compile` 子命令 |
| `himdsl.run` | OP | 允许 `run` 子命令 |
| `himdsl.debug` | OP | 允许 `debug` 子命令 |

| Node | Default | Description |
|------|---------|-------------|
| `himdsl.use` | OP | Allows the `compile` subcommand |
| `himdsl.run` | OP | Allows the `run` subcommand |
| `himdsl.debug` | OP | Allows the `debug` subcommand |

通过权限管理插件（LuckPerms 等）按需授予。

Grant them via a permissions plugin (LuckPerms, etc.) as needed.

---

## 自动执行脚本 / Auto-run Scripts

在 `plugins/HimDSL/` 下创建以下文件，插件会在对应时机**自动执行**，无需任何命令：

Create the following files under `plugins/HimDSL/`; the plugin runs them **automatically** at the matching moments — no command needed:

| 文件 | 触发时机 |
|------|---------|
| `onServerLoad.himdsl` | 服务器加载完成后 |
| `onServerShutdown.himdsl` | 插件卸载或服务器关闭时 |

| File | When |
|------|------|
| `onServerLoad.himdsl` | After the server finishes loading |
| `onServerShutdown.himdsl` | On plugin unload or server shutdown |

两个文件的写法与普通脚本完全一致，以 `main` 作为入口。

Both files use ordinary script syntax with `main` as the entry point.

---

## 语言一览 / Language at a Glance

```himdsl
// 变量与类型 / Variables and types
int n = 5;
double hp = 20.0;
string name = "Steve";
var list = vector();

// 选择器 / Selectors
player p = @p;
var all = @a;

// 占位符 / Placeholders（软依赖场景）
double hunger = $player(p, hungry)$;

// 引用字段 / Reference fields
string pname = p.name;
double px = p.x;

// 控制流 / Control flow
for (int i = 0; i < 10; i++) {
    if (i % 2 == 0) continue;
    runcmd("CONSOLE", "say " + i);
}

// 结构体 / Structs
struct Point {
    int x;
    int y;
};

Point pt;
pt.x = 3;
pt.y = 4;

// 事件 / Events
@EventHandler
void onJoin(event(PlayerJoinEvent)) {
    player who = @s;
    runcmd("CONSOLE", "say 欢迎 " + who.name);
}

// 定时任务 / Scheduled tasks
@BukkitRunnable
void tick() sche = 10s {
    runcmd("CONSOLE", "say 心跳");
}

int main() {
    start("onJoin");
    start("tick");
}
```

---

## 内置函数概览 / Built-ins at a Glance

- **数学**：`abs`、`max`、`min`、`sqrt`、`pow`、`sin`、`rand` 等
- **高精度**：`bigAdd`、`bigSub`、`bigMul`、`bigDiv`、`bigDivExact`
- **字符串**：`length`、`replace`、`split`、`contains`、`startsWith` 等（方法形式优先）
- **容器**：`vector`、`set`、`map`、`stack`、`queue`
- **文件 I/O**：`fopen`、`fread`、`fwrite`、`fclose`、`fexists`、`fdelete`、`freadlines`、`fwritelines`
- **命令**：`runcmd(executor, command)`
- **JSON**：`toJson`、`fromJson`
- **PlaceholderAPI**：`papi`、`papiSet`、`papiRegister`、`papiUnregister`、`papiHas`

- **Math**: `abs`, `max`, `min`, `sqrt`, `pow`, `sin`, `rand`, etc.
- **BigDecimal**: `bigAdd`, `bigSub`, `bigMul`, `bigDiv`, `bigDivExact`
- **Strings**: `length`, `replace`, `split`, `contains`, `startsWith`, etc. (method form preferred)
- **Containers**: `vector`, `set`, `map`, `stack`, `queue`
- **File I/O**: `fopen`, `fread`, `fwrite`, `fclose`, `fexists`, `fdelete`, `freadlines`, `fwritelines`
- **Commands**: `runcmd(executor, command)`
- **JSON**: `toJson`, `fromJson`
- **PlaceholderAPI**: `papi`, `papiSet`, `papiRegister`, `papiUnregister`, `papiHas`

---

## 扩展 API / Extension API

其他 Java 插件可以通过 Bukkit 的 ServicesManager 调用 HimDSL：

Other Java plugins can call HimDSL through Bukkit's ServicesManager:

```java
HimDSLAPI api = Bukkit.getServicesManager().load(HimDSLAPI.class);
if (api != null) {
    api.run(new File(himdslFolder, "script.himdsl"));
}
```

在 `plugin.yml` 中声明软依赖：

Declare a soft dependency in `plugin.yml`:

```yaml
softdepend: [HimDSL]
```

**先想清楚方向**：脚本要调其他插件，用脚本自身的 Java 互操作（全限定名 + `new` + 静态方法），**不需要 API**。只有"其他插件要调脚本"才轮到 `HimDSLAPI`。

**Ask direction first**: script → plugin uses the script's own Java interop (FQN + `new` + statics), **no API needed**. Only "plugin → script" calls for `HimDSLAPI`.

---

## 文档 / Documentation

完整文档见仓库 Wiki：

Full documentation is in the repository Wiki:

- **入门 / Getting Started** —— 安装、第一个脚本、命令、自动执行脚本
- **语言基础 / Language Basics** —— 词法、变量与类型、运算符、字符串与字符
- **控制流 / Control Flow** —— if / else、for、while、do-while、break / continue / return
- **函数 / Functions** —— 定义、重载、Lambda、函数式接口
- **数据结构 / Data Structures** —— 数组、List / Set / Map、Stack / Queue、JSON
- **自定义类型 / Custom Types** —— struct、方法绑定、enum、Java 互操作
- **Minecraft 集成 / Minecraft Integration** —— 选择器、EntityRef、占位符、PAPI
- **事件与调度 / Events & Scheduling** —— `@EventHandler`、`@BukkitRunnable`、`start` / `stop`
- **GUI 系统 / GUI System** —— 创建、GUISlot、点击回调、onClose、刷新
- **内置函数参考 / Built-ins Reference** —— 数学、高精度、字符串、集合、文件、命令
- **进阶 / Advanced** —— 反射、枚举解析、作用域与闭包、调试、陷阱
- **扩展 API / Extension API** —— 是否需要 API、`HimDSLAPI`、自定义占位符、嵌入式调用
- **示例脚本 / Example Scripts** —— 递归、循环、GUI 商店、登录奖励、定时广播、状态机、小游戏

---

## 许可 / License

见仓库根目录的 `LICENSE` 文件。

See the `LICENSE` file at the repository root.

---

## 反馈 / Feedback

Issue 与 Pull Request 都欢迎。使用文档见 Wiki；遇到具体问题时，先查 **[常见陷阱 / Pitfalls]** 一章。

Issues and pull requests are welcome. See the Wiki for usage; when stuck, check the **[常见陷阱 / Pitfalls]** chapter first.
