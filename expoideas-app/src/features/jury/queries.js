import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { juryApi } from './api';

const jurorsKey = (projectId) => ['jurors', String(projectId)];
const myProjectsKey = ['jury-projects'];

/** Los jurados de un proyecto (gestión). */
export const useJurors = (projectId) =>
    useQuery({ queryKey: jurorsKey(projectId), queryFn: () => juryApi.listJurors(projectId), enabled: Boolean(projectId) });

/** Asigna (con email) o quita (con userId) un jurado y recarga la lista. */
export const useSaveJuror = (projectId) => {
    const queryClient = useQueryClient();
    return useMutation({
        mutationFn: ({ email, userId }) => (userId ? juryApi.remove(projectId, userId) : juryApi.assign(projectId, email)),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: jurorsKey(projectId) }),
    });
};

/**
 * Los proyectos que quien tiene la sesión tiene por evaluar. También decide si
 * el menú muestra «Evaluar»: se pide solo para los roles que pueden ser jurado.
 */
export const useJuryProjects = (options = {}) => useQuery({ queryKey: myProjectsKey, queryFn: juryApi.myProjects, ...options });
