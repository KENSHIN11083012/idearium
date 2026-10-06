import { beforeEach, describe, expect, it, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClientProvider } from '@tanstack/react-query';
import { get } from '@/lib/apiClient';
import { createTestQueryClient } from '@/test/utils';
import { useProjectDirectory, useWholeProjectDirectory } from './directory';

vi.mock('@/lib/apiClient', () => ({ get: vi.fn(), API_BASE_URL: '/api/v1' }));

const wrapper = ({ children }) => <QueryClientProvider client={createTestQueryClient()}>{children}</QueryClientProvider>;

const pageOf = (items, totalPages) => ({ items, page: 0, size: 200, totalItems: items.length, totalPages });

beforeEach(() => {
    get.mockReset();
});

describe('El listado de proyectos', () => {
    it('pide una página con los filtros que tienen valor', async () => {
        get.mockResolvedValue(pageOf([{ id: 1 }], 1));

        const { result } = renderHook(() => useProjectDirectory({ track: 'INNPRENDE_I', search: '' }, { page: 2, size: 25 }), {
            wrapper,
        });

        await waitFor(() => expect(result.current.data.items).toEqual([{ id: 1 }]));
        expect(get).toHaveBeenCalledWith('/projects?track=INNPRENDE_I&page=2&size=25');
    });

    it('entero, recorre todas las páginas y las junta en orden', async () => {
        get.mockResolvedValueOnce(pageOf([{ id: 1 }, { id: 2 }], 3))
            .mockResolvedValueOnce(pageOf([{ id: 3 }, { id: 4 }], 3))
            .mockResolvedValueOnce(pageOf([{ id: 5 }], 3));

        const { result } = renderHook(() => useWholeProjectDirectory({ editionId: '1', track: 'INNPRENDE_I' }), { wrapper });

        await waitFor(() => expect(result.current.data).toHaveLength(5));
        expect(result.current.data.map((project) => project.id)).toEqual([1, 2, 3, 4, 5]);
        expect(get.mock.calls.map(([path]) => path)).toEqual([
            '/projects?editionId=1&track=INNPRENDE_I&page=0&size=200',
            '/projects?editionId=1&track=INNPRENDE_I&page=1&size=200',
            '/projects?editionId=1&track=INNPRENDE_I&page=2&size=200',
        ]);
    });

    it('entero y sin proyectos, pide una sola página', async () => {
        get.mockResolvedValue(pageOf([], 0));

        const { result } = renderHook(() => useWholeProjectDirectory({ editionId: '1' }), { wrapper });

        await waitFor(() => expect(result.current.data).toEqual([]));
        expect(get).toHaveBeenCalledTimes(1);
    });
});
