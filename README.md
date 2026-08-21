HimDSL 教程
HimDSL 是一个为 Minecraft 服务器设计的轻量级脚本语言插件，语法类似 Java/C，专为快速编写游戏逻辑而设计。您可以用它来：

处理玩家事件（加入、退出、破坏方块等）
创建定时任务（延迟、循环）
操作玩家、世界、服务器数据
与 HimDungeons 等插件联动（软依赖）

快速开始
1. 安装
将 HimDSL.jar 放入服务器的 plugins/ 文件夹，重启服务器。插件会创建 plugins/HimDSL/ 目录，所有脚本存放于此。

2. 安装代码高亮及第一个脚本。
查看代码高亮配置.zip。
在 plugins/HimDSL/ 下创建 hello.dsl：
int main() {
    runcmd("CONSOLE", "say Hello,World!");
}
运行脚本：/himdsl run hello.dsl

语言语法
注释
// 单行注释
/* 多行
   注释 */

变量声明
支持类型推断（var）或显式类型：
var a = 10;           // int
var b = 3.14;         // double
var c = true;         // bool
var d = "hello";      // string
int e = 5;
double f = 2.5;
bool g = false;
string h = "world";
玩家声明（选择器）
player p = @a[limit=1];      // 获取所有在线玩家中的第一个
player nearest = @p;         // 最近的玩家（需要上下文原点）
player random = @r;          // 随机在线玩家
player self = @s;            // 触发事件的玩家（仅事件函数内有效）
player all = @a;             // 全部在线玩家（返回 List<Player>）
选择器支持过滤器（逗号分隔）：
limit – 返回数量（为 1 时返回单个 Player，否则返回 List）
distance=..10 – 距离原点（在事件或占位符中设置）的范围内
level=5..10 – 玩家等级范围
name=Steve – 玩家名
gamemode=creative – 游戏模式
world="world" – 所在世界
x=100, y=64, z=0 – 精确坐标

数组
var arr = [1, 2, 3];          // 动态数组（ArrayList）
int[] nums = [10, 20];        // 显式类型
arr[0] = 100;                 // 赋值
var val = arr[1];             // 访问

运算符
算术：+ - * / %
比较：== != < > <= >=
逻辑：&& || !
赋值：= += -= *= /= %=
位运算：~（按位取反）

控制结构
if (条件) {
    // ...
} else if {
    // ...
} else{
    // ...
}
for (var i = 0; i < 10; i = i + 1) { //注意，没有提供i++,i--等语法。
    // ...
}
while (条件) {
    // ...
}
do {
    // ...
} while (条件);

函数定义
返回类型 函数名(参数列表) [sche = 时间表达式]? 块
参数可以是普通参数（int x, string name）或事件参数（event(事件类型)）。
支持 @EventHandler（事件监听）和 @BukkitRunnable（定时任务）注解。
若带 sche =，则自动按周期执行（仅对 @BukkitRunnable 有效）。

事件函数
参数中必须包含 event(事件类型)，例如 event(PlayerJoinEvent)。
在函数体内可通过 event 变量访问事件对象，例如 event.getPlayer()。
支持所有 Bukkit 事件（如 BlockBreakEvent, PlayerCommandPreprocessEvent 等）。

定时任务
使用 @BukkitRunnable 注解，并指定 sche = 时间表达式。
时间格式：5s（秒）、10t（刻，或直接数字（以刻为单位）。
函数会在后台异步执行（避免阻塞主线程），自动考虑线程安全。

可通过 start("函数名") 和 stop("函数名") 内置函数手动启停。

占位符（Placeholder）
占位符用于获取游戏状态信息，格式：$类型(参数)$。

类型	说明
$player(变量名, 属性, ...)$	获取玩家属性。
变量名需为已定义的 player 变量或事件中的玩家。属性示例：x, y, z, yaw, pitch, hp, hungry, name, uuid, opengui, handlejoin, handleleave, breaktype, breakpos, buildtype, buildpos, runcommand, slot, mainhand, offhand, bossdistance, atroom, issneaking, issprinting, gamemode, explevel, world
$world(属性, ...)$	世界信息。
属性：name, id, block(x,y,z), iscontainer(x,y,z), contain(x,y,z,slot), hasplayer(世界名)，以及 WorldEdit 相关（getx, gety, getz，需软依赖）。支持可选世界名参数。
$server(属性, ...)$	服务器信息。
属性：tps, mspt, ping(玩家名), serverloadevent, servercloseevent, onpluginenable, onplugindisable, ondungeonfail, ondungeonwin。
$boss(属性)$	
获取 HimDungeons 插件中的 Boss 属性：x, y, z, yaw, pitch, world。需软依赖。
$haveVar(变量名)$	
检查变量是否存在，返回布尔值。

内置函数
HimDSL 提供了丰富的内置函数，可直接调用。

函数名	说明
rand()	返回 0~1 随机 double。rand(a,b) 返回 a~b 随机数（整数或浮点）
randChoose(a, b, ...)	从参数中随机选择一个
toInt(x), toDouble(x), toLong(x), toString(x), toBool(x)	类型转换
toUpperCase(s), toLowerCase(s), trim(s)	字符串处理
replace(s, old, new)	替换字符串
split(s, regex)	分割字符串，返回字符串数组
contains(s, sub)	判断是否包含子串
length(s)	字符串长度
startsWith(s, prefix), endsWith(s, suffix)	字符串前缀/后缀判断
runcmd(执行者, 命令)	执行命令，执行者可为 "CONSOLE" 或玩家名
sleep(毫秒)	休眠（慎用，可能阻塞）
stack(), push(stack, item), pop(stack), peek(stack), isEmpty(stack)	栈操作
queue(), offer(queue, item), poll(queue), peek(queue), isEmpty(queue)	队列操作
map(), put(map, key, value), get(map, key), remove(map, key), containsKey(map, key)	Map 操作
start(函数名), stop(函数名)	手动启动/停止已定义的注解函数（@EventHandler 或 @BukkitRunnable）
isOp(玩家对象或名字)	判断玩家是否为 OP
数学函数：abs, fabs, fmod, max, min, pow, sqrt, cbrt, hypot, log, log10, log2, ceil, floor, round, sin, cos, tan, asin, acos, atan	对应数学运算
详细说明
变量作用域
全局变量在脚本顶层声明，所有函数共享（通过 globalEnv）。

函数内的变量为局部，仅在函数内有效。

块级作用域（{ }）会创建新环境。

选择器过滤详解
@a 返回所有在线玩家（List），@p 返回最近的单个玩家（若 limit=1 则仍为 List）
若 limit 未指定，@a 返回 List；若 limit=1 返回单个 Player。

占位符参数说明
$player 的第一个参数是变量名（字符串），第二个是属性，后续参数为附加参数（如 slot 需要提供槽位编号）。
$world 中，block/iscontainer/contain 需要坐标，可先指定世界名（字符串）作为第二个参数，否则默认使用最后记录的世界。
$server 的 ping 需要玩家名参数。
$boss 需要软依赖 HimDungeons。

事件监听函数
使用 @EventHandler 注解。
参数列表必须包含 event(具体事件类型)，例如 event(PlayerJoinEvent)。
事件对象可通过 event 变量访问，并调用其方法（如 getPlayer()）。
监听会在脚本加载后需要手动启动,可使用 start("函数名"); 手动启用

定时异步任务
使用 @BukkitRunnable 注解，并指定 sche=时间。
任务默认异步执行（runTaskAsynchronously）。
重复任务会在每次执行完后重新调度（基于周期）。
可通过 stop("函数名") 停止。

启动/停止函数
内置函数 start("函数名") 和 stop("函数名") 可控制带有注解的函数。
对于 @BukkitRunnable，start 会开始调度，stop 取消任务。
对于 @EventHandler，start 注册监听，stop 取消注册。

注意
**不要在runcmd的第二个参数尝试拼接字符串，请在外面拼好直接放进去！**

扩展 API（供插件开发者）
HimDSL 提供了 API，允许其他插件注册自定义占位符。

获取 API 实例
java
HimDSLAPI api = Bukkit.getServicesManager().getRegistration(HimDSLAPI.class).getProvider();
注册自定义占位符
java
api.registerPlaceholder("myplugin", args -> {
    // args 为占位符参数列表（已求值）
    return "自定义结果";
});
之后可在脚本中使用 $myplugin(参数)$。

保存脚本
java
api.saveScript("subfolder/script.hdsl", "var x = 10;");
故障排除
脚本不执行：检查文件路径是否正确，使用 /himdsl compile 查看语法错误。
事件不触发：确认函数有 @EventHandler 注解且参数正确。若未自动注册，可调用 start("函数名")。
定时任务不运行：需在 main 或其他地方调用 start("函数名") 启动。
选择器返回 null：确认有符合条件的玩家，或检查 origin 是否设置（如 @p 需要距离参考）。
占位符报错：检查参数个数和类型，参考本教程。

贡献与许可
欢迎提交 Issue 和 Pull Request。插件采用 GPL v3许可证。

Happy Scripting!
