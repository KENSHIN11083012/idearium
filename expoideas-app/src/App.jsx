import { lazy, Suspense } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { MANAGEMENT_ROLES, ROLES } from '@/lib/roles';
import { ROUTES } from '@/lib/routes';
import { AppShell } from '@/components/layout/AppShell';
import { RouteFocus } from '@/components/layout/RouteFocus';
import { Spinner } from '@/components/ui/feedback';
import { GuestRoute } from '@/features/auth/GuestRoute';
import { ProtectedRoute } from '@/features/auth/ProtectedRoute';

// Cada página se descarga cuando se visita por primera vez.
const HomePage = lazy(() => import('@/features/home/HomePage'));
const LoginPage = lazy(() => import('@/features/auth/LoginPage'));
const RegisterPage = lazy(() => import('@/features/auth/RegisterPage'));
const OnboardingPage = lazy(() => import('@/features/auth/OnboardingPage'));
const VerifyEmailPage = lazy(() => import('@/features/auth/VerifyEmailPage'));
const RecoverPasswordPage = lazy(() => import('@/features/auth/RecoverPasswordPage'));
const ResetPasswordPage = lazy(() => import('@/features/auth/ResetPasswordPage'));
const MyProjectsPage = lazy(() => import('@/features/projects/MyProjectsPage'));
const ProjectPage = lazy(() => import('@/features/projects/ProjectPage'));
const ProjectsPage = lazy(() => import('@/features/projects/ProjectsPage'));
const ProfilePage = lazy(() => import('@/features/profile/ProfilePage'));
const SecurityPage = lazy(() => import('@/features/profile/SecurityPage'));
const UserAdminPage = lazy(() => import('@/features/users/UserAdminPage'));
const CatalogsPage = lazy(() => import('@/features/catalogs/CatalogsPage'));
const EditionsPage = lazy(() => import('@/features/editions/EditionsPage'));
const PresentationsPage = lazy(() => import('@/features/presentations/PresentationsPage'));
const JuryProjectsPage = lazy(() => import('@/features/jury/JuryProjectsPage'));
const EvaluationPage = lazy(() => import('@/features/evaluations/EvaluationPage'));
const UnauthorizedPage = lazy(() => import('@/features/errors/UnauthorizedPage'));
const NotFoundPage = lazy(() => import('@/features/errors/NotFoundPage'));

// El subdirectorio donde se sirve la app sale de la config de Vite (base).
const basename = import.meta.env.BASE_URL.replace(/\/$/, '');

export default function App() {
    return (
        <BrowserRouter basename={basename}>
            <RouteFocus />
            <Suspense fallback={<Spinner className="min-h-dvh" />}>
                <Routes>
                    {/* Acceso: pantalla completa, sin navegación */}
                    <Route
                        path={ROUTES.LOGIN}
                        element={
                            <GuestRoute>
                                <LoginPage />
                            </GuestRoute>
                        }
                    />
                    <Route
                        path={ROUTES.REGISTER}
                        element={
                            <GuestRoute>
                                <RegisterPage />
                            </GuestRoute>
                        }
                    />
                    {/* La página misma decide: sin sesión va al inicio de sesión y sin pendientes, al inicio. */}
                    <Route path={ROUTES.ONBOARDING} element={<OnboardingPage />} />
                    {/* Adonde llevan los enlaces de los correos: valen con sesión o sin ella. */}
                    <Route path={ROUTES.VERIFY_EMAIL} element={<VerifyEmailPage />} />
                    <Route path={ROUTES.RESET_PASSWORD} element={<ResetPasswordPage />} />
                    <Route
                        path={ROUTES.RECOVER_PASSWORD}
                        element={
                            <GuestRoute>
                                <RecoverPasswordPage />
                            </GuestRoute>
                        }
                    />

                    <Route element={<AppShell />}>
                        {/* Públicas */}
                        <Route index element={<HomePage />} />
                        <Route path={ROUTES.UNAUTHORIZED} element={<UnauthorizedPage />} />

                        {/* Con sesión */}
                        <Route
                            path={ROUTES.MY_PROJECTS}
                            element={
                                <ProtectedRoute allowedRoles={[ROLES.STUDENT]}>
                                    <MyProjectsPage />
                                </ProtectedRoute>
                            }
                        />
                        {/* La ficha la ven el equipo, el docente y la gestión: decide la API */}
                        <Route
                            path={`${ROUTES.PROJECTS}/:id`}
                            element={
                                <ProtectedRoute>
                                    <ProjectPage />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path={ROUTES.PROJECTS}
                            element={
                                <ProtectedRoute allowedRoles={[...MANAGEMENT_ROLES, ROLES.TEACHER]}>
                                    <ProjectsPage />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path={ROUTES.PROFILE}
                            element={
                                <ProtectedRoute>
                                    <ProfilePage />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path={ROUTES.SECURITY}
                            element={
                                <ProtectedRoute>
                                    <SecurityPage />
                                </ProtectedRoute>
                            }
                        />

                        {/* Jurado: cualquier cuenta a la que la gestión le asigne proyectos */}
                        <Route
                            path={ROUTES.JURY_PROJECTS}
                            element={
                                <ProtectedRoute allowedRoles={[...MANAGEMENT_ROLES, ROLES.TEACHER, ROLES.JUDGE]}>
                                    <JuryProjectsPage />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path={`${ROUTES.JURY_PROJECTS}/:id/calificar`}
                            element={
                                <ProtectedRoute allowedRoles={[...MANAGEMENT_ROLES, ROLES.TEACHER, ROLES.JUDGE]}>
                                    <EvaluationPage />
                                </ProtectedRoute>
                            }
                        />

                        {/* Gestión: MacondoLab y administradores */}
                        <Route
                            path={ROUTES.USERS}
                            element={
                                <ProtectedRoute allowedRoles={MANAGEMENT_ROLES}>
                                    <UserAdminPage />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path={ROUTES.CATALOGS}
                            element={
                                <ProtectedRoute allowedRoles={MANAGEMENT_ROLES}>
                                    <CatalogsPage />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path={ROUTES.EDITIONS}
                            element={
                                <ProtectedRoute allowedRoles={MANAGEMENT_ROLES}>
                                    <EditionsPage />
                                </ProtectedRoute>
                            }
                        />
                        <Route
                            path={ROUTES.PRESENTATIONS}
                            element={
                                <ProtectedRoute allowedRoles={MANAGEMENT_ROLES}>
                                    <PresentationsPage />
                                </ProtectedRoute>
                            }
                        />

                        <Route path="*" element={<NotFoundPage />} />
                    </Route>
                </Routes>
            </Suspense>
        </BrowserRouter>
    );
}
