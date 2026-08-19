# DESIGN.md — Stripe 风格设计规范

> 来源：`awesome-design-md` 技能 `references/stripe.md`
> 用途：fake_mianshi 项目 Stripe 风格 UI 设计参考

## 视觉主题

Stripe 风格 = 金融级精密 + 奢华克制。白色画布（#ffffff）+ 深海军蓝标题（#061b31）+ 品牌紫（#533afd）。签名特征：**weight 300 细体大标题**（反常规的"轻量即权威"）、负字距、蓝色调多层阴影。

## 色板

| 角色 | 色值 |
|---|---|
| 品牌紫（CTA/链接） | `#533afd` |
| CTA Hover | `#4434d4` |
| 标题深海军蓝 | `#061b31` |
| 正文石板灰 | `#64748d` |
| 标签深灰 | `#273951` |
| 边框 | `#e5edf5` |
| 暗色区块（品牌靛蓝） | `#1c1e54` |
| 装饰 Ruby / Magenta | `#ea2261` / `#f96bee` |
| 成功绿 | `#15be53`（文字 `#108c3d`） |
| 紫色边框 | `#b9b9f9` / `#d6d9fc` |

## 阴影（签名：蓝色调多层）

```css
--shadow-blue: rgba(50,50,93,0.25);   /* 远层·品牌蓝 */
--shadow-black: rgba(0,0,0,0.1);      /* 近层·中性 */
/* 标准抬高卡片 */
box-shadow: var(--shadow-blue) 0px 30px 45px -30px,
            var(--shadow-black) 0px 18px 36px -18px;
```

## 字体

- 主字体：sohne-var（Web 用 Inter/SF Pro Display 替代），全局 `font-feature-settings:"ss01"`
- 等宽：SourceCodePro（代码/技术标签）
- 大标题：56px/48px/32px，**weight 300**，字距 -1.4px/-0.96px/-0.64px 逐级放松
- 正文 16-18px weight 300，按钮/链接 14-16px weight 400
- 数字用 `font-variant-numeric:tabular-nums`

## 组件

- 按钮：primary（紫底白字）/ ghost（透明紫字+1px #b9b9f9 边框），圆角 **4px**，padding 8px 16px
- 卡片：白底 + 1px #e5edf5 边框 + 圆角 5-6px + 蓝色调阴影
- 徽章：成功徽章 = rgba(21,190,83,0.2) 底 + #108c3d 字 + rgba(21,190,83,0.4) 边框
- 导航：sticky 白底 + backdrop-filter blur(12px)，14px 链接，紫色 CTA 右对齐

## 布局

- 最大内容宽 1080px；区块纵向节奏 96px padding
- 白区 ↔ 暗色品牌区（#1c1e54）交替形成明暗韵律
- 圆角范围 4-8px，**禁止** 12px+ 大圆角和胶囊形
- 响应式：768px 以下单列，hero 56px→32px

## 禁忌

- ❌ 标题不用 weight 600-700（300 是品牌声线）
- ❌ 不用中性灰阴影（必须带蓝色调）
- ❌ 不用纯黑标题（用 #061b31）
- ❌ 不用橙/黄做交互色（紫是主色）
- ❌ 不用正字距大标题（要负字距）
- ❌ ruby/magenta 仅装饰渐变，不用于按钮/链接

## 速查（Agent 提示）

> Hero：白底，48px weight 300 标题（字距 -0.96px，色 #061b31，ss01），18px 副标题（#64748d），紫色主 CTA（#533afd，4px 圆角）+ ghost 次 CTA（1px #b9b9f9 边框）。卡片阴影用 `rgba(50,50,93,0.25) 0 30px 45px -30px, rgba(0,0,0,0.1) 0 18px 36px -18px`。暗色区块背景 #1c1e54，白字，卡片边框 rgba(255,255,255,0.1)。
