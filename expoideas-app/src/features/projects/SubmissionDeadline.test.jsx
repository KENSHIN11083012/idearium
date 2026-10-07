import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { formatDay } from '@/features/editions/status';
import { project } from '@/test/fixtures';
import { SubmissionDeadline } from './SubmissionDeadline';

/** El día que cae a esa distancia de hoy, como lo entrega la API ("2026-11-28"). */
const inDays = (days) => {
    const day = new Date();
    day.setDate(day.getDate() + days);
    return day.toLocaleDateString('en-CA');
};

const renderDeadline = (changes) => render(<SubmissionDeadline project={{ ...project, ...changes }} />);

describe('Plazo de entregas del proyecto', () => {
    it('dice cuántos días quedan y qué día cierra', () => {
        const closesOn = inDays(12);
        renderDeadline({ submissionClosesOn: closesOn });

        expect(screen.getByText('Quedan 12 días para entregar')).toBeInTheDocument();
        expect(screen.getByText(new RegExp(`cierra el ${formatDay(closesOn)}`))).toBeInTheDocument();
    });

    it('el último día lo dice así, no como «quedan 0 días»', () => {
        renderDeadline({ submissionClosesOn: inDays(0) });

        expect(screen.getByText('Hoy es el último día para entregar')).toBeInTheDocument();
    });

    it('con un solo día usa el singular', () => {
        renderDeadline({ submissionClosesOn: inDays(1) });

        expect(screen.getByText('Queda 1 día para entregar')).toBeInTheDocument();
    });

    it('con las entregas cerradas dice cuándo cerraron y ya no cuenta días', () => {
        const closesOn = inDays(-2);
        renderDeadline({ submissionOpen: false, submissionClosesOn: closesOn });

        expect(screen.getByText(`Las entregas cerraron el ${formatDay(closesOn)}.`)).toBeInTheDocument();
        expect(screen.queryByText(/para entregar/)).not.toBeInTheDocument();
    });

    it('sin la fecha no pinta nada', () => {
        const { container } = renderDeadline({ submissionClosesOn: undefined });

        expect(container).toBeEmptyDOMElement();
    });
});
