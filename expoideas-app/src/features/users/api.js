import { del, get, post, put } from '@/lib/apiClient';
import { FILE_PART } from '@/lib/files';
import { toQuery } from '@/lib/query';

/** Gestión de cuentas (MacondoLab y administradores). */
export const usersApi = {
    /** Una página de cuentas: { search, role, pendingRole, page, size }. */
    list: (params) => get(`/admin/users${toQuery(params)}`),
    create: (body) => post('/admin/users', body),
    /** Rol y adscripción. */
    update: (id, body) => put(`/admin/users/${id}`, body),
    resetPassword: (id, body) => post(`/admin/users/${id}/password-reset`, body),
    /** Corta el acceso sin eliminar la cuenta, o se lo devuelve. */
    suspend: (id) => post(`/admin/users/${id}/suspension`),
    reactivate: (id) => post(`/admin/users/${id}/reactivation`),
    remove: (id) => del(`/admin/users/${id}`),
    /** Aprobaciones de cátedra: las que nacen de un proyecto y las manuales. */
    listApprovals: (userId) => get(`/admin/track-approvals?userId=${userId}`),
    createApproval: (body) => post('/admin/track-approvals', body),
    removeApproval: (id) => del(`/admin/track-approvals/${id}`),
    /** Listado de la cátedra: quién se registra con qué rol. */
    listRoster: () => get('/admin/roster'),
    importRoster: (file) => {
        const body = new FormData();
        body.append(FILE_PART, file);
        return post('/admin/roster', body);
    },
    removeRosterEntry: (id) => del(`/admin/roster/${id}`),
    clearRoster: () => del('/admin/roster'),
};
