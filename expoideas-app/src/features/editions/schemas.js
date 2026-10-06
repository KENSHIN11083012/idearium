import { z } from 'zod';
import { TRACK_COURSES, TRACK_LIST } from '@/lib/tracks';

/**
 * Edición de la Expo (EditionRequest en la API). Los inputs entregan texto: las
 * fechas como "2026-11-03" y los límites del grupo como números escritos.
 *
 * Las reglas que cruzan campos se repiten aquí y en EditionService: aquí para
 * avisar antes de enviar, allá porque es quien decide de verdad.
 */

const date = (missing) => z.string().min(1, missing);

/** El input entrega texto; la API espera un número entre 1 y 20. */
const members = (missing) =>
    z.string().trim().min(1, missing).pipe(z.coerce.number().int('Usa un número entero').min(1, 'Mínimo 1').max(20, 'Máximo 20'));

/** Un objeto por cátedra: { INNPRENDE_I: { minMembers, maxMembers }, ... }. */
const tracksShape = Object.fromEntries(
    TRACK_LIST.map((track) => [
        track,
        z.object({
            minMembers: members('Ingresa el mínimo de integrantes'),
            maxMembers: members('Ingresa el máximo de integrantes'),
        }),
    ]),
);

export const editionSchema = z
    .object({
        name: z.string().trim().min(1, 'Ingresa un nombre').max(100, 'Máximo 100 caracteres'),
        registrationOpensOn: date('Ingresa la fecha de apertura'),
        registrationClosesOn: date('Ingresa la fecha de cierre de inscripciones'),
        submissionClosesOn: date('Ingresa la fecha de cierre de entregas'),
        tracks: z.object(tracksShape),
    })
    .refine((values) => values.registrationClosesOn >= values.registrationOpensOn, {
        message: 'Las inscripciones no pueden cerrar antes de abrir',
        path: ['registrationClosesOn'],
    })
    .refine((values) => values.submissionClosesOn >= values.registrationClosesOn, {
        message: 'Las entregas no pueden cerrar antes que las inscripciones',
        path: ['submissionClosesOn'],
    })
    .superRefine((values, context) => {
        TRACK_LIST.forEach((track) => {
            const { minMembers, maxMembers } = values.tracks[track];
            if (maxMembers < minMembers) {
                context.addIssue({
                    code: 'custom',
                    message: `En ${TRACK_COURSES[track]}, el máximo no puede ser menor que el mínimo`,
                    path: ['tracks', track, 'maxMembers'],
                });
            }
        });
    });

/** Del formulario al cuerpo que espera la API: las cátedras van como lista. */
export const toEditionRequest = (values) => ({
    name: values.name,
    registrationOpensOn: values.registrationOpensOn,
    registrationClosesOn: values.registrationClosesOn,
    submissionClosesOn: values.submissionClosesOn,
    tracks: TRACK_LIST.map((track) => ({
        track,
        minMembers: values.tracks[track].minMembers,
        maxMembers: values.tracks[track].maxMembers,
    })),
});

/** De la edición que devuelve la API a los valores del formulario. */
export const toFormValues = (edition) => ({
    name: edition?.name ?? '',
    registrationOpensOn: edition?.registrationOpensOn ?? '',
    registrationClosesOn: edition?.registrationClosesOn ?? '',
    submissionClosesOn: edition?.submissionClosesOn ?? '',
    tracks: Object.fromEntries(
        TRACK_LIST.map((track) => {
            // Sin valores por defecto: los límites los define MacondoLab, no la plataforma.
            const settings = edition?.tracks?.find((item) => item.track === track);
            return [
                track,
                {
                    minMembers: settings ? String(settings.minMembers) : '',
                    maxMembers: settings ? String(settings.maxMembers) : '',
                },
            ];
        }),
    ),
});
