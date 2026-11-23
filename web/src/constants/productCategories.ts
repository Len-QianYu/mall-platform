export const PLUSH_CATEGORIES = [
  {
    key: 'claude',
    name: 'claude玩偶',
    title: 'Claude 玩偶',
    description: '温柔陪伴，灵感常在',
  },
  {
    key: 'deepseek',
    name: 'deepseek玩偶',
    title: 'DeepSeek 玩偶',
    description: '探索好奇，发现新意',
  },
  {
    key: 'gpt',
    name: 'gpt玩偶',
    title: 'GPT 玩偶',
    description: '随时相伴，分享日常',
  },
  {
    key: 'grok',
    name: 'grok玩偶',
    title: 'Grok 玩偶',
    description: '自在有趣，自有个性',
  },
] as const

export const mergeProductCategories = (existing: readonly string[] = []): string[] => [
  ...new Set([...PLUSH_CATEGORIES.map((category) => category.name), ...existing]),
]
