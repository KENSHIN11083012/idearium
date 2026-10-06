import { useId, useState } from 'react';
import { useController } from 'react-hook-form';
import {
    DndContext,
    DragOverlay,
    MouseSensor,
    TouchSensor,
    pointerWithin,
    useDraggable,
    useDroppable,
    useSensor,
    useSensors,
} from '@dnd-kit/core';
import { CircleAlert, GripVertical } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Textarea } from '@/components/ui/textarea';
import { formatGrade, isFailing } from './grades';
import { fieldOf } from './schemas';

/*
 * El tablero con el que el jurado califica: la rúbrica tal como está en el
 * papel, un criterio por fila y un nivel por celda. Cada criterio tiene una
 * ficha que se arrastra hasta su nivel.
 *
 * Arrastrar es un atajo, no el único camino: cada celda es también un botón de
 * opción, así que se puede tocar, hacer clic o moverse con el teclado, y un
 * lector de pantalla lee un grupo de opciones por criterio.
 */

const tokenClasses = cn(
    'inline-flex touch-manipulation select-none items-center gap-1 self-start rounded border border-primary bg-primary px-2 py-1',
    'text-xs font-semibold text-on-primary shadow-hard-sm [&_svg]:size-3.5',
);

/** La ficha del criterio. Decorativa para el lector de pantalla: la elección se anuncia en las opciones. */
function Token({ id, children }) {
    const { listeners, setNodeRef, isDragging } = useDraggable({ id });
    return (
        <span
            ref={setNodeRef}
            {...listeners}
            aria-hidden="true"
            className={cn(tokenClasses, 'cursor-grab active:cursor-grabbing', isDragging && 'opacity-40')}
        >
            <GripVertical />
            {children}
        </span>
    );
}

/** Un nivel del criterio: opción elegible y destino donde soltar la ficha. */
function LevelCell({ name, level, selected, onChoose, tokenId }) {
    const descriptionId = useId();
    const { setNodeRef, isOver } = useDroppable({ id: level.id });
    const grade = formatGrade(level.score);

    return (
        <label
            ref={setNodeRef}
            className={cn(
                // Sin selección de texto: al arrastrar la ficha y fallar por poco se marcaba media rúbrica.
                'flex cursor-pointer select-none flex-col gap-2 rounded border border-outline-variant bg-surface-container-lowest p-3',
                'transition-[border-color,background-color,box-shadow] duration-150 hover:border-primary',
                'has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-primary',
                selected && 'border-primary bg-primary/5 ring-1 ring-primary',
                isOver && 'border-dashed border-primary bg-primary/10',
            )}
        >
            <input
                type="radio"
                name={name}
                className="sr-only"
                checked={selected}
                onChange={() => onChoose(level.id)}
                aria-label={`${level.label}, ${grade}`}
                aria-describedby={descriptionId}
            />
            <span className="flex flex-wrap items-baseline justify-between gap-x-2">
                <span className="font-heading text-2xl font-bold leading-none text-on-surface">{grade}</span>
                <span className="label-mono text-on-surface-variant">{level.label}</span>
            </span>
            <span id={descriptionId} className="text-sm leading-snug text-on-surface-variant">
                {level.description}
            </span>
            {selected && <Token id={tokenId}>Tu calificación</Token>}
        </label>
    );
}

/** Un criterio: sus niveles, la ficha y la observación. */
function CriterionRow({ criterion, control, register, clearErrors, errors }) {
    const titleId = useId();
    const key = fieldOf(criterion.id);
    const { field } = useController({ control, name: `scores.${key}.levelId` });
    const [dragging, setDragging] = useState(false);
    const tokenId = `ficha-${criterion.id}`;
    const levelErrorId = `criterio-${criterion.id}-error`;

    // El ratón arrastra tras unos píxeles, para que un clic siga siendo un clic; el dedo,
    // tras mantenerlo un instante, para que deslizar siga siendo desplazar la página.
    const sensors = useSensors(
        useSensor(MouseSensor, { activationConstraint: { distance: 6 } }),
        useSensor(TouchSensor, { activationConstraint: { delay: 150, tolerance: 8 } }),
    );

    const selected = criterion.levels.find((level) => level.id === field.value);
    const failing = Boolean(selected) && isFailing(selected.score);

    const choose = (levelId) => {
        field.onChange(levelId);
        // La observación pudo dejar de ser obligatoria: su error se vuelve a decidir al guardar.
        clearErrors(`scores.${key}.comment`);
    };

    return (
        <Card id={`criterio-${criterion.id}`} className="flex scroll-mt-24 flex-col gap-4 p-4 sm:p-6">
            <div className="flex flex-wrap items-start justify-between gap-2">
                <h2 id={titleId} className="font-heading text-lg font-bold text-on-surface">
                    <span className="text-primary">{criterion.position}.</span> {criterion.name}
                </h2>
                {selected ? (
                    <Badge variant={failing ? 'outline' : 'primary'} mono>
                        {formatGrade(selected.score)} · {selected.label}
                    </Badge>
                ) : (
                    <Badge variant="outline" mono>
                        Sin calificar
                    </Badge>
                )}
            </div>

            <DndContext
                sensors={sensors}
                collisionDetection={pointerWithin}
                onDragStart={() => setDragging(true)}
                onDragCancel={() => setDragging(false)}
                onDragEnd={({ over }) => {
                    setDragging(false);
                    if (over) choose(over.id);
                }}
            >
                <div
                    role="radiogroup"
                    aria-labelledby={titleId}
                    aria-required="true"
                    aria-invalid={errors?.levelId ? true : undefined}
                    aria-describedby={errors?.levelId ? levelErrorId : undefined}
                    style={{ '--levels': criterion.levels.length }}
                    className="grid gap-2 lg:grid-cols-[7.5rem_repeat(var(--levels),minmax(0,1fr))]"
                >
                    {/* Donde espera la ficha hasta que se califica el criterio. */}
                    <div className="flex items-center gap-3 rounded border border-dashed border-outline-variant p-3 lg:flex-col lg:items-start">
                        {!selected && <Token id={tokenId}>Ficha</Token>}
                        <p className="text-xs text-on-surface-variant">
                            {selected ? 'Puedes mover la ficha a otro nivel.' : 'Arrastra la ficha a un nivel, o tócalo.'}
                        </p>
                    </div>
                    {criterion.levels.map((level) => (
                        <LevelCell
                            key={level.id}
                            name={`nivel-${criterion.id}`}
                            level={level}
                            selected={level.id === field.value}
                            onChoose={choose}
                            tokenId={tokenId}
                        />
                    ))}
                </div>
                <DragOverlay dropAnimation={null}>
                    {dragging && (
                        <span className={cn(tokenClasses, 'cursor-grabbing')}>
                            <GripVertical />
                            {selected ? 'Tu calificación' : 'Ficha'}
                        </span>
                    )}
                </DragOverlay>
            </DndContext>

            {errors?.levelId && (
                <p id={levelErrorId} role="alert" className="flex items-start gap-1.5 text-xs font-medium text-error">
                    <CircleAlert className="mt-px size-3.5 shrink-0" aria-hidden="true" />
                    {errors.levelId.message}
                </p>
            )}

            <Field
                label={`Observación de «${criterion.shortName}»`}
                required={failing}
                error={errors?.comment?.message}
                hint={
                    failing
                        ? 'Obligatoria: el nivel está por debajo de 3.0.'
                        : 'Opcional. Una o dos líneas que justifiquen la calificación.'
                }
            >
                <Textarea rows={2} maxLength={500} className="min-h-16" {...register(`scores.${key}.comment`)} />
            </Field>
        </Card>
    );
}

/**
 * @param {object} props
 * @param {object} props.rubric  la rúbrica de la cátedra, como la devuelve la API
 * @param {object} props.form    el formulario de react-hook-form (control, register, clearErrors y errors)
 */
export function RubricBoard({ rubric, form }) {
    const { control, register, clearErrors, errors } = form;
    return (
        <div className="flex flex-col gap-4">
            {rubric.criteria.map((criterion) => (
                <CriterionRow
                    key={criterion.id}
                    criterion={criterion}
                    control={control}
                    register={register}
                    clearErrors={clearErrors}
                    errors={errors.scores?.[fieldOf(criterion.id)]}
                />
            ))}
        </div>
    );
}
