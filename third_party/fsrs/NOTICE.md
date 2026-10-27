# FSRS-6 数学实现来源

Java 核心 `android/src/cn/local/wenyan/Fsrs.java` 移植自 MIT 许可的 ts-fsrs FSRS-6 模型。
上游仓库：https://github.com/open-spaced-repetition/ts-fsrs
固定提交：28410b2757ca826b5171279412cc899dfab059de
源文件：packages/fsrs/src/models/fsrs-6/{algorithm,constants}.ts，以及 packages/fsrs/src/help.ts。
本目录保存原始源码和许可证，APK 同时包含 fsrs-LICENSE.txt。

21个默认权重、短期公式启用、关闭随机扰动，默认保持率90%，最大模型间隔36500天。
应用保留四点学习门槛与次日毕业复习；连续3次正常到期复习困难会回新学。它们是应用规则，非FSRS公式。
提前复习只练习，不更新模型；旧记录下一次作答才初始化，不伪造DSR历史。
FSRS逐卡状态自适应已接入；个人权重训练器由本项目独立实现有界坐标搜索，遵循相同FSRS-6序列模型与参数边界，不是移植Anki的Rust优化器。详见docs/FSRS-个人训练.md。版本已固定，不随上游变化自动更新。

重建上游对照：node scripts/generate_fsrs_vectors.mjs
核对Java核心：python3 scripts/verify_fsrs.py
