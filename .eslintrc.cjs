module.exports = {
  root: true,
  env: {
    node: true,
    browser: true,
    es2022: true,
  },
  extends: [
    'plugin:vue/vue3-recommended',
    'eslint:recommended',
  ],
  parser: 'vue-eslint-parser',
  parserOptions: {
    ecmaVersion: 2022,
    sourceType: 'module',
    parser: '@typescript-eslint/parser',
  },
  overrides: [
    {
      files: ['*.ts', '*.tsx'],
      parser: '@typescript-eslint/parser',
      extends: ['plugin:@typescript-eslint/recommended'],
      rules: {
        // 允许在脚本中使用 any 等宽松写法，避免过度约束
        '@typescript-eslint/no-explicit-any': 'off',
        '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
      },
    },
  ],
  rules: {
    // 允许在模板和脚本中使用 any 等宽松写法，避免过度约束
    'vue/multi-word-component-names': 'off',
    'vue/valid-v-slot': ['error', { allowModifiers: true }],
    'no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
    'no-undef': 'off',
    // 关闭与 TS 冲突/冗余的规则，交由 TS 处理
    '@typescript-eslint/no-explicit-any': 'off',
    '@typescript-eslint/no-unused-vars': ['warn', { argsIgnorePattern: '^_' }],
  },
  ignorePatterns: [
    'dist',
    'node_modules',
    'backend/target',
    'public',
    '*.d.ts',
  ],
};
