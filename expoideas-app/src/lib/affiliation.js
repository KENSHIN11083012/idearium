import { z } from 'zod';

/**
 * Adscripción académica (sede, facultad y programa opcional), compartida por el
 * registro, el perfil y la gestión de cuentas. Los <select> entregan los ids
 * como texto.
 */
export const affiliationShape = {
    campusId: z.string().min(1, 'Selecciona la sede'),
    facultyId: z.string().min(1, 'Selecciona la facultad'),
    academicProgramId: z.string(),
};

/** Valores del formulario a partir de una cuenta de la API. */
export const affiliationFromUser = (user) => ({
    campusId: user?.campusId != null ? String(user.campusId) : '',
    facultyId: user?.facultyId != null ? String(user.facultyId) : '',
    academicProgramId: user?.academicProgramId != null ? String(user.academicProgramId) : '',
});

/** Cuerpo para la API: ids numéricos y programa vacío como null ("sin programa"). */
export const affiliationToApi = ({ campusId, facultyId, academicProgramId }) => ({
    campusId: Number(campusId),
    facultyId: Number(facultyId),
    academicProgramId: academicProgramId ? Number(academicProgramId) : null,
});
