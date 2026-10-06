import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { jwtDecode } from 'jwt-decode';
import { useQueryClient } from '@tanstack/react-query';
import { ONBOARDING_REQUIRED_EVENT, UNAUTHORIZED_EVENT } from '@/lib/apiClient';
import { ROLES, asRole, isManagement } from '@/lib/roles';
import { session } from '@/lib/session';
import { fullName } from '@/lib/text';
import { useProfile } from '@/features/profile/queries';
import { AuthContext } from './authContext';

const NO_PROFILE = { id: null, email: null, firstName: null, lastName: null, photoId: null };

const isExpired = (decoded) => typeof decoded?.exp === 'number' && decoded.exp < Date.now() / 1000;

/** De quién es un token, o null si no hay o no se puede leer. */
const accountOf = (token) => {
    try {
        return jwtDecode(token).sub ?? null;
    } catch {
        return null;
    }
};

/** Cada cuánto se vuelve a pedir /users/me para ver si el rol cambió. */
const PROFILE_REFRESH_MS = 60_000;

/**
 * Sesión de la app. El token trae el correo, el rol y el vencimiento; el perfil
 * (nombre y foto, para la cabecera) y los pasos de primer ingreso se guardan
 * aparte. Todo persiste en localStorage (lib/session.js).
 *
 * El rol del token es el del momento de entrar. Como la gestión puede cambiarlo
 * con la sesión abierta (y la API ya lo lee de la BD en cada petición), mientras
 * hay sesión se consulta /users/me cada minuto y al volver a la pestaña, y manda
 * el rol que devuelva.
 *
 * localStorage es de todas las pestañas: si en otra se cierra la sesión o entra
 * otra cuenta, esta se pone al día (evento `storage`).
 */
export const AuthProvider = ({ children }) => {
    const queryClient = useQueryClient();
    const [token, setToken] = useState(session.token);
    const [profile, setProfile] = useState(() => ({ ...NO_PROFILE, ...session.user() }));
    const [pendingSteps, setPendingSteps] = useState(session.pendingSteps);
    // La API rechazó el token de una sesión que aquí seguía abierta (venció, cambió la contraseña, la suspendieron).
    const [closedByServer, setClosedByServer] = useState(false);
    // Cambia cuando otra pestaña entra con otra cuenta: lo que hay en pantalla se monta de nuevo.
    const [accountEpoch, setAccountEpoch] = useState(0);

    const savePendingSteps = useCallback((steps) => {
        setPendingSteps(steps);
        session.save({ pendingSteps: steps });
    }, []);

    const logout = useCallback(() => {
        setToken(null);
        setProfile(NO_PROFILE);
        setPendingSteps([]);
        setClosedByServer(false);
        session.clear();
        // Los datos en caché eran de esta cuenta.
        queryClient.clear();
    }, [queryClient]);

    // La sesión se deriva del token en vez de mantenerse en un estado aparte
    // sincronizado por un effect.
    const decoded = useMemo(() => {
        if (!token) return null;
        try {
            const claims = jwtDecode(token);
            return isExpired(claims) ? null : claims;
        } catch {
            return null;
        }
    }, [token]);

    const { data: me, isError: profileFailed } = useProfile({
        enabled: Boolean(decoded),
        refetchInterval: PROFILE_REFRESH_MS,
        refetchOnWindowFocus: true,
    });

    // Hay token pero no sirve (vencido o ilegible). No hace falta tocar el estado:
    // `decoded` ya es null y la app se comporta como sin sesión. Aquí solo se
    // limpia localStorage, que es el sistema externo.
    useEffect(() => {
        if (token && !decoded) session.clear();
    }, [token, decoded]);

    // Un 401 en cualquier petición cierra la sesión desde un solo sitio, y queda
    // dicho para que el inicio de sesión explique por qué hay que volver a entrar.
    useEffect(() => {
        const onUnauthorized = () => {
            logout();
            setClosedByServer(true);
        };
        window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
        return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    }, [logout]);

    // La cuenta de esta pestaña, para compararla con lo que deje otra en localStorage.
    const account = useRef(null);
    useEffect(() => {
        account.current = decoded?.sub ?? null;
    }, [decoded]);

    // Otra pestaña cambió la sesión: esta la toma de localStorage. Guardar la
    // sesión son varias claves y llega un aviso por cada una; todos leen lo mismo.
    useEffect(() => {
        const onStorage = (event) => {
            if (event.storageArea !== localStorage) return;
            // Sin clave, es que se vació todo el almacenamiento.
            if (event.key !== null && !session.owns(event.key)) return;

            const newToken = session.token();
            const newAccount = accountOf(newToken);
            if (newAccount !== account.current) {
                // Los datos en caché eran de la otra cuenta, y lo que hay a medio llenar en pantalla también.
                queryClient.clear();
                if (newAccount && account.current) setAccountEpoch((epoch) => epoch + 1);
                account.current = newAccount;
            }
            if (newToken) setClosedByServer(false);
            setToken(newToken);
            setProfile({ ...NO_PROFILE, ...session.user() });
            setPendingSteps(session.pendingSteps());
        };
        window.addEventListener('storage', onStorage);
        return () => window.removeEventListener('storage', onStorage);
    }, [queryClient]);

    // Un 403 por primer ingreso pendiente (p. ej. le restablecieron la contraseña
    // con la sesión abierta) actualiza los pasos y las rutas llevan a resolverlos.
    useEffect(() => {
        const onOnboardingRequired = (event) => savePendingSteps(event.detail);
        window.addEventListener(ONBOARDING_REQUIRED_EVENT, onOnboardingRequired);
        return () => window.removeEventListener(ONBOARDING_REQUIRED_EVENT, onOnboardingRequired);
    }, [savePendingSteps]);

    /**
     * @param {string} newToken  JWT del login; de él salen el rol y el vencimiento
     * @param {{ id?: number, email?: string, firstName?: string, lastName?: string, photoId?: string }} user
     * @param {string[]} [steps] pasos de primer ingreso que devolvió el login
     */
    const login = useCallback((newToken, user = {}, steps = []) => {
        const newProfile = { ...NO_PROFILE, ...user };
        session.save({ token: newToken, user: newProfile, pendingSteps: steps });
        setToken(newToken);
        setProfile(newProfile);
        setPendingSteps(steps);
        setClosedByServer(false);
    }, []);

    /**
     * Cambia solo el token. Al cambiar la contraseña la API cierra las sesiones
     * abiertas con la anterior y devuelve el token con el que sigue esta: si no
     * se guarda, la siguiente petición responde 401 y la app cierra la sesión.
     */
    const renewToken = useCallback((newToken) => {
        session.save({ token: newToken });
        setToken(newToken);
    }, []);

    const completeStep = useCallback(
        (step) => savePendingSteps(pendingSteps.filter((pending) => pending !== step)),
        [pendingSteps, savePendingSteps],
    );

    /** Actualiza nombre o foto (photoId null la quita). */
    const updateUser = useCallback(
        (changes) => {
            const updated = { ...profile, ...changes };
            session.save({ user: updated });
            setProfile(updated);
        },
        [profile],
    );

    const value = useMemo(() => {
        const role = decoded ? asRole(me?.role ?? decoded.role) : null;
        return {
            user: decoded
                ? { ...profile, email: profile.email ?? decoded.sub, fullName: fullName(profile.firstName, profile.lastName) }
                : null,
            token: decoded ? token : null,
            role,
            /**
             * Si `role` ya es el de la BD. Hasta que /users/me responda, es el del
             * token, que puede estar viejo: las rutas por rol esperan antes de negar
             * el acceso. Si la consulta falla, vale el del token.
             */
            roleReady: !decoded || me !== undefined || profileFailed,
            /** Pasos de primer ingreso sin completar; mientras haya, las rutas protegidas llevan al primer ingreso. */
            pendingSteps: decoded ? pendingSteps : [],
            /**
             * Si la sesión se cerró sin que la persona lo pidiera: la API rechazó el
             * token o ya estaba vencido al abrir la página.
             */
            sessionExpired: !decoded && (closedByServer || Boolean(token)),
            /** El rol del listado de la cátedra que la gestión aún no confirma, o null. */
            pendingRole: decoded ? asRole(me?.pendingRole) : null,
            isAdmin: role === ROLES.ADMIN,
            /** MacondoLab o administrador: acceso a Usuarios y Catálogos. */
            isManagement: isManagement(role),
            login,
            logout,
            renewToken,
            updateUser,
            completeStep,
        };
    }, [
        decoded,
        me,
        profileFailed,
        profile,
        token,
        pendingSteps,
        closedByServer,
        login,
        logout,
        renewToken,
        updateUser,
        completeStep,
    ]);

    return (
        <AuthContext.Provider value={value}>
            <Fragment key={accountEpoch}>{children}</Fragment>
        </AuthContext.Provider>
    );
};
