import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { useAuth } from '@/features/auth/useAuth';
import { PendingRoleNotice } from './PendingRoleNotice';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));

describe('Aviso del rol por confirmar', () => {
    it('le explica a quien figura como profesor por qué su cuenta es de estudiante', () => {
        useAuth.mockReturnValue({ pendingRole: 'TEACHER' });

        render(<PendingRoleNotice />);

        const notice = screen.getByRole('status');
        expect(notice).toHaveTextContent('Tu rol de profesor está por confirmar');
        expect(notice).toHaveTextContent('tu cuenta funciona como la de un estudiante');
    });

    it('sin un rol pendiente no ocupa lugar', () => {
        useAuth.mockReturnValue({ pendingRole: null });

        const { container } = render(<PendingRoleNotice />);

        expect(container).toBeEmptyDOMElement();
    });
});
