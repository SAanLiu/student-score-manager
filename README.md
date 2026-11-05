# 学生成绩管理系统

使用 Java 和 Maven 开发的命令行项目，采用 model、repository、service、app、exception 分层结构，支持学生管理、成绩统计和 CSV 自动保存。

## 功能

- 添加学生：id 不能重复，姓名和班级不能为空，成绩必须是 0 到 100 之间的有限数值。
- 按 id 查询、删除学生，修改学生成绩。
- 按成绩从高到低列出学生，同分时按 id 的字典顺序升序排列。
- 使用 Stream 计算平均分、最高分、最低分、及格率和各班平均分。
- 启动时读取 CSV，添加、修改、删除成功后自动保存。
- 处理错误菜单编号、非数字输入、重复 id 和不存在的 id，操作失败后仍可继续使用菜单。

id 使用字符串，保留 `001` 这样的前导零，且区分大小写。菜单输入的文本会去掉首尾空白。
60 分及以上视为及格。统计过程中不提前四舍五入，显示时保留两位小数；没有学生时显示暂无数据。

## 环境要求

- JDK 25。
- Apache Maven 3.6.3 或更高版本，或者使用 IntelliJ IDEA 自带的 Maven。
- 首次构建需要联网下载依赖和构建插件。

使用终端运行 Maven 时，需要配置 `JAVA_HOME`，并将 Maven 的 `bin` 目录加入 `PATH`。
在 Git Bash 或其他终端检查环境：

```bash
java -version
mvn -version
```

确认两者使用的都是 JDK 25。本项目暂未提供 Maven Wrapper。
如果终端提示找不到 `mvn`，可以先使用 IntelliJ 的 Maven 面板构建项目。

## 获取、构建与运行

### 克隆项目

```bash
git clone https://github.com/SAanLiu/student-score-manager.git
cd student-score-manager
```

如果已经有本地项目，直接进入现有项目根目录即可，不需要再次克隆。

### 命令行运行

在项目根目录执行构建，编译代码、运行测试并生成可执行 JAR：

```bash
mvn clean verify
```

运行程序：

```bash
java -jar target/student-score-manager-1.0-SNAPSHOT.jar
```

该 JAR 已包含运行依赖，运行时只需要 JDK 25，不需要额外配置依赖路径或安装 Maven。
`target/` 中的 `original-student-score-manager-1.0-SNAPSHOT.jar` 是不包含依赖的中间产物，请使用上面命令指定的文件。

只运行测试、不打包：

```bash
mvn test
```

### IntelliJ IDEA 运行

1. 打开项目文件夹或 `pom.xml`，将其导入为 Maven 项目。
2. 将项目 SDK 和 Maven Runner 使用的 JDK 都设为 25。
3. 刷新 Maven，等待依赖下载完成。
4. 打开 `src/main/java/io/github/saanliu/student/app/Main.java`，运行 `Main.main()`。
5. 将运行配置中的 Working directory 设为项目根目录，保证与命令行运行时使用相同的数据文件。

也可以在 Maven 面板中执行 Lifecycle 下的 `verify`，然后在终端运行生成的 JAR。

## 菜单与操作示例

```text
=== Student Score Manager ===
1. Add student
2. Delete student
3. Update score
4. Find student by ID
5. List all students by score (highest first)
6. Show statistics
0. Exit
```

输入菜单编号，再按提示填写信息。数字格式错误时重新输入；业务校验失败时显示错误并返回菜单。
输入流结束时，程序正常退出。

可以添加以下数据进行手动验证：

| id | name | className | score |
| --- | --- | --- | --- |
| 001 | Alice | Class A | 90 |
| 002 | Bob | Class A | 60 |
| 003 | Carol | Class B | 30 |
| 004 | Dave | Class B | 100 |

选择 `5`，预期 id 顺序为 `004`、`001`、`002`、`003`。

选择 `6`，预期结果如下：

| 统计项 | 结果 |
| --- | --- |
| 平均分 | 70.00 |
| 最高分 | 100.00 |
| 最低分 | 30.00 |
| 及格率 | 75.00% |
| Class A 平均分 | 75.00 |
| Class B 平均分 | 65.00 |

退出后，从相同的工作目录重新启动程序，再查询 `001`，可以验证数据是否成功保存。

## CSV 存储规则

默认数据文件为 `data/students.csv`，路径相对于程序的工作目录。
成功启动时会显示文件的绝对路径。首次成功修改数据时创建目录和文件，仅启动程序不会创建数据文件。

文件使用 UTF-8 编码，不包含表头，每条 CSV 记录依次包含四个字段：

```text
id,name,className,score
```

示例数据：

```csv
001,Alice,Class A,90.0
002,Bob Jr.,Class B,85.5
```

当前 `Main` 选择 `SimpleFileStudentRepository`，用 `Files.readAllLines` 和 `Files.write` 读写，
写入前拒绝 id、姓名或班级中的逗号和换行符，不处理 CSV 转义或 UTF-8 BOM。

`FileStudentRepository` 则使用 Apache Commons CSV 处理逗号、双引号和换行转义，并兼容 UTF-8 BOM。
它允许 `Bob, Jr.` 这样的姓名；含换行的字段可能占多个物理行，但仍是一条 CSV 记录。
两种实现都支持 Windows 换行符，保存时保留成绩的数值精度，不使用界面上显示的两位小数格式。

### 自动保存与异常处理

- 文件不存在时，以空数据启动；空文件也是合法的。
- CSV 格式损坏、文件无法读取或已有学生信息不合法时，启动失败并返回退出码 `1`，保留原文件。
- 已有数据中的重复 id、空字段、非法成绩和文本首尾空白都会被报告，不会被静默忽略或覆盖。
- repository 负责 CSV 结构和数字解析，service 负责学生业务规则，包括对启动数据的校验。
- 两种文件实现共用父类规定的顺序：先保存文件，成功后才更新内存快照；失败时显示错误，保留原内存数据。
- `FileStudentRepository` 先写入同目录的临时文件，关闭后原子替换正式文件；不支持原子替换时会明确失败。
- `SimpleFileStudentRepository` 直接覆盖正式文件，未提供原子替换保护；实际写入期间的 IO 故障可能使文件不完整。
- 删除最后一名学生后，保存为空文件。

同一数据文件只应由一个程序实例使用，目前没有实现多进程文件锁或并发修改合并。
如需手动编辑 CSV，请先退出程序。`data/` 已被 Git 忽略，需要保留的数据应另行备份。

## 项目结构

```text
student-score-manager/
|-- pom.xml
|-- README.md
|-- .gitignore
|-- src/main/java/io/github/saanliu/student/
|   |-- app/Main.java
|   |-- model/
|   |   |-- Student.java
|   |   `-- ScoreStatistics.java
|   |-- repository/
|   |   |-- StudentRepository.java
|   |   |-- AbstractFileStudentRepository.java
|   |   |-- InMemoryStudentRepository.java
|   |   |-- SimpleFileStudentRepository.java
|   |   `-- FileStudentRepository.java
|   |-- service/StudentService.java
|   `-- exception/
|       |-- StudentException.java
|       |-- StudentNotFoundException.java
|       |-- DuplicateStudentException.java
|       |-- InvalidStudentException.java
|       `-- StudentStorageException.java
|-- src/test/java/io/github/saanliu/student/
|   |-- app/MainTest.java
|   |-- service/StudentServiceTest.java
|   `-- repository/
|       |-- FileStudentRepositoryTest.java
|       `-- SimpleFileStudentRepositoryTest.java
|-- data/
`-- target/
```

`data/` 和 `target/` 在本地运行或构建时生成，不提交到 Git。

| 分层 | 职责 |
| --- | --- |
| `model` | 用 record 描述学生和统计结果。 |
| `repository` | 定义数据存取接口，提供内存和 CSV 文件实现。 |
| `service` | 业务校验、增删改查、成绩排序与统计。 |
| `app` | 组装应用对象、读取菜单输入、显示结果和错误。 |
| `exception` | 表达学生不存在、id 重复、信息不合法、存储失败等错误。 |

调用关系为 `Main -> StudentService -> StudentRepository`。

`StudentService` 通过构造方法接收 repository 接口。以后可以新增 `JdbcStudentRepository` 实现相同接口，
再在启动时选择该实现，service 和菜单逻辑无需改写。

内存实现保留用于独立测试，正式启动时选择文件实现。

两种文件实现继承 `AbstractFileStudentRepository`。父类负责路径初始化、学生快照、增删改查和保存流程，
子类只负责 `readStudents()`、`writeStudents()` 以及各自的格式处理。
子类构造方法先调用 `super(file)`，再调用父类的 `loadStudents()` 加载数据，避免在父类构造期间调用可重写的方法。

## 依赖与学习内容

- Apache Commons CSV：解析和生成 CSV，处理特殊字符转义。
- JUnit Jupiter：验证业务规则、菜单输入恢复、临时目录中的文件读写与保存失败行为。
- Maven Shade Plugin：生成包含运行依赖的可执行 JAR。
- Java record、接口、构造方法注入、自定义异常、集合、Stream 和文件 IO。

代码标识符和程序输出使用英文，源代码注释使用中文。

## 开发阶段

1. 核心逻辑：使用内存 repository 实现学生增删改查和校验。
2. 命令行菜单：读取输入，处理输入错误和业务异常。
3. 统计功能：使用 Stream 完成排序和成绩统计。
4. 文件存储：启动加载 CSV，修改后自动保存。
5. 项目文档：完善使用说明和可执行 JAR 打包。
