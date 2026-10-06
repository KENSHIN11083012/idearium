import { useContext } from 'react';
import { AuthContext } from './authContext';

/** La sesión actual: ver el valor que expone AuthProvider. */
export const useAuth = () => useContext(AuthContext);
