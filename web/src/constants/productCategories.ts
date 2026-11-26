export const PLUSH_CATEGORIES = [
  {
    key: 'claude',
    name: 'claude玩偶',
    title: 'Claude 玩偶',
    description: '温柔陪伴，灵感常在',
    chartColors: { light: '#d8673c', dark: '#ef9b75' },
  },
  {
    key: 'deepseek',
    name: 'deepseek玩偶',
    title: 'DeepSeek 玩偶',
    description: '探索好奇，发现新意',
    chartColors: { light: '#496090', dark: '#7b94c2' },
  },
  {
    key: 'gpt',
    name: 'gpt玩偶',
    title: 'GPT 玩偶',
    description: '随时相伴，分享日常',
    chartColors: { light: '#8f84a2', dark: '#c1b5d2' },
  },
  {
    key: 'gemini',
    name: 'gemini玩偶',
    title: 'Gemini 玩偶',
    description: '多彩灵感，创意相伴',
    chartColors: { light: '#9a718b', dark: '#c49fb9' },
  },
] as const

export const mergeProductCategories = (existing: readonly string[] = []): string[] => [
  ...new Set([...PLUSH_CATEGORIES.map((category) => category.name), ...existing]),
]
