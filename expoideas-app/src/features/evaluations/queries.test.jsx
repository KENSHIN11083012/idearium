import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { get } from '@/lib/apiClient';
import { createTestQueryClient } from '@/test/utils';
import { useProjectRubric } from './queries';

vi.mock('@/lib/apiClient', () => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }));

const wrapper = ({ children }) => <QueryClientProvider client={createTestQueryClient()}>{children}</QueryClientProvider>;

beforeEach(() => {
    get.mockReset();
});

describe('La rúbrica de un proyecto', () => {
    it('se pide por proyecto, no por cátedra: es la de su edición', async () => {
        get.mockResolvedValue({ id: 1, version: 1, track: 'INNPRENDE_I', criteria: [] });

        const { result } = renderHook(() => useProjectRubric('10'), { wrapper });

        await waitFor(() => expect(result.current.data).toMatchObject({ id: 1, version: 1 }));
        expect(get).toHaveBeenCalledWith('/projects/10/rubric');
    });

    it('sin proyecto no pide nada', () => {
        renderHook(() => useProjectRubric(undefined), { wrapper });

        expect(get).not.toHaveBeenCalled();
    });
});
