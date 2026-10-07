package co.edu.unisimon.expoideas.users;

import java.util.List;
import java.util.Map;

/**
 * Lo que un módulo guarda de una persona, para el archivo que ella misma puede
 * descargar con sus datos (Ley 1581 de 2012: derecho a conocerlos).
 *
 * <p>La cuenta no sabe de equipos, entregables ni evaluaciones: cada módulo
 * responde por lo suyo. Un módulo nuevo que guarde algo de una persona
 * implementa esta interfaz, y su parte aparece en el archivo sin tocar nada más.
 */
public interface PersonalDataSource {

    /**
     * Las secciones de este módulo: el nombre de cada una (en inglés, como todo
     * campo JSON) y sus filas. Se llama dentro de una transacción de solo lectura.
     * Las secciones sin filas también van: dicen que de eso no hay nada guardado.
     */
    Map<String, List<?>> dataOf(User user);
}
