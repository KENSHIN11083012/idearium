import js from '@eslint/js';
import globals from 'globals';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import prettier from 'eslint-config-prettier';
import { defineConfig, globalIgnores } from 'eslint/config';

export default defineConfig([
    globalIgnores(['dist', '.vite', 'coverage']),
    {
        files: ['**/*.{js,jsx}'],
        extends: [
            js.configs.recommended,
            reactHooks.configs.flat.recommended,
            reactRefresh.configs.vite,
            // Accesibilidad: etiquetas, roles y teclado en el JSX.
            jsxA11y.flatConfigs.recommended,
            // Al final: apaga las reglas de estilo que Prettier ya decide (formato, no las revisa ESLint).
            prettier,
        ],
        plugins: { react },
        languageOptions: {
            ecmaVersion: 'latest',
            globals: globals.browser,
            parserOptions: {
                ecmaVersion: 'latest',
                ecmaFeatures: { jsx: true },
                sourceType: 'module',
            },
        },
        rules: {
            // Sin esta regla, no-unused-vars no ve las variables usadas solo en JSX.
            'react/jsx-uses-vars': 'error',
            // autoFocus solo se usa en el primer campo de un diálogo o de un formulario que
            // la persona acaba de abrir: ahí es justo donde el foco debe ir. En los campos
            // sueltos de una página (elementos del DOM) sigue prohibido.
            'jsx-a11y/no-autofocus': ['error', { ignoreNonDOM: true }],
            'no-unused-vars': [
                'error',
                {
                    // Solo se ignoran las variables marcadas a propósito con _.
                    varsIgnorePattern: '^_',
                    // Permite el idioma `const { campo, ...resto } = obj` para omitir campos.
                    ignoreRestSiblings: true,
                    // Un catch puede no necesitar el error.
                    caughtErrors: 'none',
                },
            ],
        },
    },
    {
        // Los archivos de configuración corren en Node, no en el navegador.
        files: ['vite.config.js', 'eslint.config.js'],
        languageOptions: { globals: globals.node },
    },
]);
