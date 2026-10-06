import { post, put } from '@/lib/apiClient';

export const authApi = {
    login: (credentials) => post('/auth/login', credentials, { auth: false }),
    register: (body) => post('/auth/register', body, { auth: false }),
    /** Canjea el enlace de verificación que llegó al correo. */
    verifyEmail: (token) => post('/auth/email-verification', { token }, { auth: false }),
    /** Pide el enlace para poner una contraseña nueva; responde igual exista o no la cuenta. */
    requestPasswordRecovery: (email) => post('/auth/password-recovery', { email }, { auth: false }),
    /** Pone la contraseña nueva con el token del enlace: `{ token, newPassword, confirmPassword }`. */
    resetPassword: (body) => post('/auth/password-reset', body, { auth: false }),
};

/** Pasos del primer ingreso; también los usa la página de Seguridad. */
export const accountApi = {
    /** Devuelve `{ token }`: el cambio cierra las sesiones anteriores y esta sigue con ese token. */
    changePassword: (body) => put('/users/me/password', body),
    giveDataConsent: () => put('/users/me/data-consent', { dataConsent: true }),
    /** Vuelve a enviar el enlace de verificación a la cuenta de la sesión. */
    resendVerification: () => post('/auth/email-verification/resend'),
};
