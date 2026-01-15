# 开发待办事项 (TODO)

## 正在进行 / 计划中 (Pending)
- [ ] 测试新的 SP 形态机制是否稳定

- [ ] 测试有没有报error

## 已完成 (Completed)
- [x] **SP 狐狸形态 Debuff 调整**
    - [x] 移除持续扣血 (damage_over_time)
    - [x] 改为低蓝量时受到伤害增加 20%
    - [x] 修复 `SscAddonConditions` 确保 `has_mana_percent_safe` 条件工作正常
- [x] **SP 狐狸形态 UI 调整**
    - [x] 隐藏本能值条 (Instinct Bar) (通过修改 Form Phase 为 PHASE_3)
- [x] **使魔火环 (Blue Fire Ring) 调整**
    - [x] 移除所有系统提示文字 (tellraw)
    - [x] 保留音效反馈
    - [x] 优化火环的可见性
- [x] 修复月髓环变的sp形态通过指令切换回普通形态导致的形态重叠的问题
- [x] 修正文本错误