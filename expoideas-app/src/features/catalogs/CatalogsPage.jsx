import { Box, Building2, GraduationCap, Landmark, Shapes, Tags } from 'lucide-react';
import { useAuth } from '@/features/auth/useAuth';
import { PageContainer } from '@/components/layout/AppShell';
import { PageHeader } from '@/components/ui/page-header';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { CATALOG_PATHS } from './api';
import { CatalogPanel } from './CatalogPanel';

/**
 * Catálogos de la plataforma. Para agregar uno basta con sumarlo aquí (y su ruta
 * en la API). `adminOnly` marca la estructura institucional, que la API solo
 * deja escribir al administrador; MacondoLab gestiona la clasificación.
 */
const CATALOGS = [
    {
        key: 'campuses',
        path: CATALOG_PATHS.campuses,
        adminOnly: true,
        label: 'Sedes',
        singular: 'sede',
        newLabel: 'Nueva sede',
        article: 'una sede',
        icon: Landmark,
    },
    {
        key: 'faculties',
        path: CATALOG_PATHS.faculties,
        adminOnly: true,
        label: 'Facultades',
        singular: 'facultad',
        newLabel: 'Nueva facultad',
        article: 'una facultad',
        icon: Building2,
    },
    {
        key: 'academicPrograms',
        path: CATALOG_PATHS.academicPrograms,
        adminOnly: true,
        label: 'Programas académicos',
        singular: 'programa académico',
        newLabel: 'Nuevo programa académico',
        article: 'un programa académico',
        icon: GraduationCap,
        hasFaculty: true,
    },
    {
        key: 'sectors',
        path: CATALOG_PATHS.sectors,
        label: 'Sectores',
        singular: 'sector',
        newLabel: 'Nuevo sector',
        article: 'un sector',
        icon: Shapes,
    },
    {
        // INNPRENDE II: según el tipo se piden unos entregables u otros. Nace vacío; los define MacondoLab.
        key: 'prototypeTypes',
        path: CATALOG_PATHS.prototypeTypes,
        label: 'Tipos de prototipo',
        singular: 'tipo de prototipo',
        newLabel: 'Nuevo tipo de prototipo',
        article: 'un tipo de prototipo',
        icon: Box,
    },
    {
        key: 'keywords',
        path: CATALOG_PATHS.keywords,
        label: 'Palabras clave',
        singular: 'palabra clave',
        newLabel: 'Nueva palabra clave',
        article: 'una palabra clave',
        icon: Tags,
    },
];

export default function CatalogsPage() {
    const { isAdmin } = useAuth();
    const visible = CATALOGS.filter((catalog) => !catalog.adminOnly || isAdmin);

    return (
        <PageContainer>
            <PageHeader
                eyebrow="Gestión"
                title="Catálogos"
                description={
                    isAdmin
                        ? 'Datos de referencia que usan los formularios de la plataforma: estructura académica y clasificación de proyectos.'
                        : 'Clasificación de los proyectos que usan los formularios de la plataforma.'
                }
            />

            <Tabs defaultValue={visible[0].key}>
                <TabsList aria-label="Catálogos">
                    {visible.map(({ key, label, icon: Icon }) => (
                        <TabsTrigger key={key} value={key}>
                            <Icon className="size-4" aria-hidden="true" />
                            {label}
                        </TabsTrigger>
                    ))}
                </TabsList>
                {visible.map((catalog) => (
                    <TabsContent key={catalog.key} value={catalog.key}>
                        <CatalogPanel catalog={catalog} />
                    </TabsContent>
                ))}
            </Tabs>
        </PageContainer>
    );
}
