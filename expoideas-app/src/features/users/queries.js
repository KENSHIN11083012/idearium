import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { usersApi } from './api';

const usersKey = ['users'];

/**
 * Una página de cuentas con los filtros puestos. Al cambiar de página o de
 * filtro se sigue viendo la anterior hasta que llega la nueva, en vez de un
 * esqueleto a cada tecla.
 *
 * @param {{ search?: string, role?: string, pendingRole?: boolean, page?: number, size?: number }} params
 */
export const useUsers = (params = {}) =>
    useQuery({ queryKey: [...usersKey, params], queryFn: () => usersApi.list(params), placeholderData: keepPreviousData });

/**
 * Tras cambiar una cuenta, se vuelven a pedir las páginas: con filtros y
 * páginas en la API, no se puede saber aquí en cuál queda la cuenta.
 */
const useUsersMutation = (mutationFn) => {
    const queryClient = useQueryClient();
    return useMutation({ mutationFn, onSuccess: () => queryClient.invalidateQueries({ queryKey: usersKey }) });
};

export const useCreateUser = () => useUsersMutation(usersApi.create);

/** Cambia rol o adscripción: `{ id, changes }`. */
export const useUpdateUser = () => useUsersMutation(({ id, changes }) => usersApi.update(id, changes));

/** Suspende o reactiva: `{ id, suspended }`, con el estado en el que debe quedar la cuenta. */
export const useSetSuspended = () =>
    useUsersMutation(({ id, suspended }) => (suspended ? usersApi.suspend(id) : usersApi.reactivate(id)));

export const useResetPassword = () => useMutation({ mutationFn: ({ id, passwords }) => usersApi.resetPassword(id, passwords) });

const rosterKey = ['roster'];

/** El listado de la cátedra. */
export const useRoster = () => useQuery({ queryKey: rosterKey, queryFn: usersApi.listRoster });

const useRosterMutation = (mutationFn) => {
    const queryClient = useQueryClient();
    return useMutation({ mutationFn, onSuccess: () => queryClient.invalidateQueries({ queryKey: rosterKey }) });
};

/** Carga un CSV y devuelve el resumen de la carga. */
export const useImportRoster = () => useRosterMutation(usersApi.importRoster);

export const useRemoveRosterEntry = () => useRosterMutation(usersApi.removeRosterEntry);

export const useClearRoster = () => useRosterMutation(usersApi.clearRoster);

const approvalsKey = (userId) => ['track-approvals', String(userId)];

/** Aprobaciones de cátedra de una persona. */
export const useTrackApprovals = (userId) =>
    useQuery({ queryKey: approvalsKey(userId), queryFn: () => usersApi.listApprovals(userId), enabled: Boolean(userId) });

/** Registra (con track) o quita (con id) una aprobación y recarga las de esa persona. */
export const useSaveTrackApproval = (userId) => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ id, track }) => (id ? usersApi.removeApproval(id) : usersApi.createApproval({ userId, track })),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: approvalsKey(userId) }),
    });
};

export const useDeleteUser = () => useUsersMutation(usersApi.remove);
