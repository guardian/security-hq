import guardian from '@guardian/eslint-config';

export default [
  ...guardian.configs.recommended,
	...guardian.configs.jest,
  {
    ignores: ['dist']
  },
];
