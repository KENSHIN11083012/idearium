import { describe, expect, it, vi } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ROLES, isManagement } from '@/lib/roles';
import { useAuth } from '@/features/auth/useAuth';
import { useJuryProjects } from '@/features/jury/queries';
import { SiteHeader } from './SiteHeader';

vi.mock('@/features/auth/useAuth', () => ({ useAuth: vi.fn() }));
vi.mock('@/features/jury/queries', () => ({ useJuryProjects: vi.fn(() => ({ data: [] })) }));

const sessionAs = (role, user = {}) =>
    useAuth.mockReturnValue({
        token: 'token',
        pendingSteps: [],
        user: {
            firstName: 'Marta',
            lastName: 'Ríos',
            fullName: 'Marta Ríos',
            email: 'marta@empresa.com',
            photoId: null,
            ...user,
        },
        role,
        isManagement: isManagement(role),
        logout: vi.fn(),
    });

const menuLinks = () =>
    within(screen.getByRole('navigation', { name: 'Principal' }))
        .getAllByRole('link')
        .map((link) => link.textContent);

describe('Marca', () => {
    it('el enlace al inicio lleva el nombre de la plataforma', () => {
        sessionAs(ROLES.STUDENT);
        render(<SiteHeader />, { wrapper: MemoryRouter });

        const home = screen.getByRole('link', { name: 'Idearium, ir al inicio' });
        expect(home).toHaveTextContent('Idearium');
    });
});

describe('Menú principal por rol', () => {
    it('el jurado ve Evaluar', () => {
        sessionAs(ROLES.JUDGE);
        render(<SiteHeader />, { wrapper: MemoryRouter });

        expect(menuLinks()).toEqual(['Inicio', 'Evaluar', 'Mi perfil']);
    });

    it('el docente ve los proyectos de sus grupos y, solo si le asignaron alguno, Evaluar', () => {
        sessionAs(ROLES.TEACHER);
        const { unmount } = render(<SiteHeader />, { wrapper: MemoryRouter });
        expect(menuLinks()).toEqual(['Inicio', 'Proyectos', 'Mi perfil']);
        unmount();

        useJuryProjects.mockReturnValue({ data: [{ id: 10 }] });
        render(<SiteHeader />, { wrapper: MemoryRouter });
        expect(menuLinks()).toEqual(['Inicio', 'Proyectos', 'Evaluar', 'Mi perfil']);
        useJuryProjects.mockReturnValue({ data: [] });
    });

    it('el estudiante ve Mis proyectos', () => {
        sessionAs(ROLES.STUDENT);
        render(<SiteHeader />, { wrapper: MemoryRouter });

        expect(menuLinks()).toEqual(['Inicio', 'Mis proyectos', 'Mi perfil']);
    });

    it.each([ROLES.MACONDOLAB, ROLES.ADMIN])('%s ve la gestión completa', (role) => {
        sessionAs(role);
        render(<SiteHeader />, { wrapper: MemoryRouter });

        expect(menuLinks()).toEqual(['Inicio', 'Proyectos', 'Mi perfil', 'Usuarios', 'Catálogos', 'Ediciones', 'Sustentaciones']);
    });
});

describe('Cuenta en la cabecera', () => {
    it('las iniciales toman el primer nombre y el primer apellido, aunque sean compuestos', () => {
        sessionAs(ROLES.STUDENT, { firstName: 'Ana María', lastName: 'Pérez Gómez', fullName: 'Ana María Pérez Gómez' });
        render(<SiteHeader />, { wrapper: MemoryRouter });

        expect(screen.getAllByText('AP').length).toBeGreaterThan(0);
        expect(screen.queryByText('AM')).not.toBeInTheDocument();
        expect(screen.getByText('Ana María Pérez Gómez')).toBeInTheDocument();
    });
});
