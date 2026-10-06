import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// Sin globals de Vitest, Testing Library no desmonta solo entre pruebas.
afterEach(() => {
    cleanup();
    vi.clearAllMocks();
});

// jsdom no implementa APIs del navegador que usan los menús y diálogos de Radix.
globalThis.ResizeObserver ??= class {
    observe() {}
    unobserve() {}
    disconnect() {}
};
Element.prototype.hasPointerCapture ??= () => false;
Element.prototype.setPointerCapture ??= () => {};
Element.prototype.releasePointerCapture ??= () => {};
Element.prototype.scrollIntoView ??= () => {};
