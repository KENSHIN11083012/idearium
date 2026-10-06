# Rúbricas de evaluación

Las rúbricas con las que los jurados califican, tal como las entregó MacondoLab el 30 de septiembre
de 2026. Están cargadas en la base de datos por la migración `V12__rubrics_and_evaluations.sql`, como
versión 1 de cada cátedra; este documento es la referencia para leerlas y para revisar cambios.

## Reglas de cálculo

- La nota de un jurado es el **promedio simple** de sus criterios, con un decimal.
- La nota del proyecto es el **promedio de las notas de sus jurados**, con un decimal.
- La observación de un criterio es opcional, y **obligatoria si ese criterio queda por debajo de 3.0**.
- Si el equipo **no asistió**, el jurado lo marca y su evaluación vale 0.0, sin niveles ni observaciones.
- Califican los jurados asignados al proyecto, y cada uno puede corregir lo que guardó. Si la gestión
  quita a un jurado, su evaluación se conserva pero deja de contar.
- Las notas las ven el profesor del grupo y la gestión; cada jurado, solo la suya. El equipo ve su nota
  final y las observaciones (sin nombres de jurados) solo cuando la gestión **publica las notas** de la
  cátedra en «Ediciones»; se pueden volver a ocultar. Publicar no congela nada: una corrección posterior
  de un jurado se ve al instante, y queda en el rastro de auditoría con la nota que había y la nueva.
- Cada criterio calificado guarda **lo que valía su nivel en ese momento**. Si después se corrige un
  valor de la rúbrica (con una migración), las notas ya puestas no cambian solas: la corrección vale
  para lo que se califique desde entonces, y una evaluación anterior solo toma los valores nuevos si su
  jurado la vuelve a guardar.
- La gestión puede recordar por correo, desde «Sustentaciones», a los jurados de una cátedra lo que les
  falta por calificar. No hay envíos automáticos: no está definido cuándo.
- Escala institucional (Art. 62 del Estatuto Estudiantil): Deficiente 0,0–2,9 · Aceptable 3,0–3,9 ·
  Bueno 4,0–4,4 · Muy bueno 4,5–4,9 · Excelente 5,0.

## Versiones: cada edición califica con su rúbrica

Una cátedra puede tener varias versiones de su rúbrica (`rubrics.version`), y cada cátedra de cada
edición guarda con cuál se califica (`edition_tracks.rubric_id`):

- Una **edición nueva** nace con la versión más reciente de cada cátedra.
- Una edición que ya tiene la suya **no pasa sola** a una versión nueva: sus proyectos se siguen
  calificando, y sus notas se siguen leyendo, con los criterios con los que se evaluaron.
- `GET /api/v1/projects/{id}/rubric` devuelve la del proyecto; `GET /api/v1/rubrics/{cátedra}`, la
  vigente (la que estrenará la próxima edición). Cuál usa cada edición sale en `rubricId`, en
  `GET /api/v1/editions`.

**Cuando MacondoLab cambia una rúbrica** (criterios, niveles o valores), no se edita la que hay: se
carga otra versión con una migración. La misma migración decide a qué ediciones llega; lo normal es
que la tomen las de esa cátedra en las que todavía nadie ha calificado:

```sql
INSERT INTO rubrics (track, version, name, description)
VALUES ('INNPRENDE_I', 2, 'Póster de proyecto de innovación', '…');
SET @rubric = LAST_INSERT_ID();
-- … sus rubric_criteria y rubric_levels, como en V12 …

UPDATE edition_tracks et
   SET et.rubric_id = @rubric
 WHERE et.track = 'INNPRENDE_I'
   AND NOT EXISTS (SELECT 1
                     FROM evaluations e
                     JOIN projects p ON p.id = e.project_id
                    WHERE p.edition_id = et.edition_id AND p.track = et.track);
```

Pasar a otra versión una edición que **ya tiene evaluaciones** las dejaría apuntando a criterios que
no son los de su rúbrica: no se hace. Corregir en su sitio un valor o una errata de una versión
(`UPDATE rubric_levels …`) sigue siendo posible y no reescribe lo ya calificado, por la regla de
arriba; se reserva para arreglos que no cambian con qué se evalúa.

## Pendiente de confirmar con MacondoLab

- **Póster:** el encabezado del documento anuncia los valores 0.0 · 3.0 · 4.0 · 4.5 · 5.0, pero la
  tabla trae otros en algunos criterios (1.5 en los criterios 1 y 2, 3.5 en el 2, y los criterios 4 y 6
  empiezan en 1.5). Se cargó lo que dice la tabla. Los nombres de los niveles son los del encabezado
  de la tabla, aunque no coinciden con los de la escala del mismo documento.
- **Pitch:** el encabezado llama «No asistió» al primer nivel; en la plataforma se llama «Muy
  deficiente», como en la escala del documento, porque la inasistencia se marca con un botón aparte.
- **Desempate:** los documentos dicen qué criterios priorizar si dos proyectos empatan. No está
  construido: el ranking quedó fuera del piloto.

## INNPRENDE I · Despegue: rúbrica del póster

Póster de proyecto de innovación. Evaluación en stand ante jurado · Innovación y Emprendimiento I.

### 1. Aspectos formales — Nombre del proyecto, autores y referencias

| Nivel | Valor | Descripción |
|---|---|---|
| Insuficiente | 0.0 | Falta el nombre del proyecto y/o los autores, y no hay referencias o las que hay no son verificables. |
| Deficiente | 1.5 | Hay nombre y autores, pero el nombre es genérico; referencias incompletas o sin formato consistente. |
| Aceptable | 4.0 | Nombre y autores completos; referencias completas, pero con formato de citación inconsistente. |
| Bueno | 4.5 | Nombre coherente con la propuesta, autores completos y bien ubicados; referencias pertinentes en formato consistente (ej. APA). |
| Excelente | 5.0 | Nombre distintivo con identidad propia coherente con el proyecto; referencias variadas, pertinentes y correctamente citadas, respaldando datos clave del póster. |

### 2. Planteamiento del problema/necesidad

| Nivel | Valor | Descripción |
|---|---|---|
| Insuficiente | 0.0 | No se identifica un problema o necesidad; se salta directo a la solución. |
| Deficiente | 1.5 | El problema se menciona de forma genérica ("mucha gente tiene este problema"), sin población afectada ni consecuencias claras. |
| Aceptable | 3.5 | Se identifica la población afectada y una consecuencia del problema, pero sin datos ni evidencia que lo respalden. |
| Bueno | 4.5 | Problema bien delimitado: población afectada, consecuencias y contexto claros, con al menos un dato o evidencia de respaldo. |
| Excelente | 5.0 | Problema delimitado con precisión, evidencia/datos concretos (cifras, fuente, estudio propio) y una lectura crítica de por qué el problema persiste o se ha desatendido. |

### 3. Objetivos del proyecto

| Nivel | Valor | Descripción |
|---|---|---|
| Insuficiente | 0.0 | No hay objetivos o están mezclados con la descripción del problema/solución. |
| Deficiente | 3.0 | Hay objetivos, pero son vagos, no están en infinitivo o son en realidad actividades ("hacer una encuesta"). |
| Aceptable | 4.0 | 2–3 objetivos en infinitivo, claros individualmente, pero sin relación evidente entre sí o con el problema planteado. |
| Bueno | 4.5 | Objetivos claros, en infinitivo, coherentes entre sí y alineados directamente con el problema y la solución propuesta. |
| Excelente | 5.0 | Objetivos claros, medibles/verificables y jerarquizados (uno general y específicos, o priorizados), que dejan ver una ruta lógica de trabajo. |

### 4. Contextualización — Segmentación, propuesta de valor, fuentes de ingreso y comparativo de competidores

| Nivel | Valor | Descripción |
|---|---|---|
| Insuficiente | 1.5 | Falta uno o más de los cuatro elementos (segmento, propuesta de valor, fuente de ingreso, comparativo de competidores), o se presentan de forma confusa o inexistente. |
| Deficiente | 3.0 | Los cuatro elementos están mencionados, pero de forma genérica: segmento "para todos", propuesta de valor sin diferenciación, ingreso sin justificar, competidores nombrados sin análisis. |
| Aceptable | 4.0 | Segmento de cliente específico y al menos un beneficio diferenciador claro; fuente de ingreso identificada; comparativo con fortalezas/debilidades de al menos un competidor, pero el diferencial propio no es contundente. |
| Bueno | 4.5 | Segmento bien definido, propuesta de valor clara y diferenciada, fuente(s) de ingreso coherente(s) con el modelo de negocio, y comparativo de 2 o más competidores con diferencial propio explícito y creíble. |
| Excelente | 5.0 | Los cuatro elementos están integrados como un modelo de negocio coherente: segmento, propuesta de valor, ingreso y diferencial frente a competidores se explican entre sí, con evidencia y justificación de viabilidad. |

### 5. Impacto y sostenibilidad

| Nivel | Valor | Descripción |
|---|---|---|
| Insuficiente | 0.0 | No se menciona impacto ni cómo se sostendría el proyecto en el tiempo. |
| Deficiente | 3.0 | Se menciona impacto de forma aspiracional ("va a ayudar a mucha gente") sin plazo ni mecanismo de sostenibilidad. |
| Aceptable | 4.0 | Se identifica un impacto a mediano/largo plazo, pero el mecanismo de sostenibilidad (financiera, operativa o social) es vago. |
| Bueno | 4.5 | Impacto a mediano/largo plazo bien argumentado, con al menos un mecanismo concreto de sostenibilidad en el tiempo. |
| Excelente | 5.0 | Impacto y sostenibilidad conectados explícitamente con el modelo de negocio y el problema inicial, mostrando pensamiento de largo plazo y posibles riesgos/mitigaciones. |

### 6. Presentación personal, herramientas de apoyo, sinergia de equipo y sustentación oral

| Nivel | Valor | Descripción |
|---|---|---|
| Insuficiente | 1.5 | No hay cuidado en la presentación personal del equipo; no usan herramientas de apoyo más allá del póster; el equipo se ve desorganizado (uno solo habla o hay fricciones); además, leen el póster palabra por palabra o no logran explicar la idea con claridad en el tiempo disponible. |
| Deficiente | 3.0 | Presentación personal descuidada o dispareja; no hay herramientas de apoyo adicionales; participación desigual (uno lidera todo, los demás casi no intervienen); explican la idea de forma entrecortada, apoyándose casi todo el tiempo en la lectura del póster. |
| Aceptable | 4.0 | Presentación personal aceptable y coherente entre el equipo; usan al menos una herramienta de apoyo simple (muestra física, folleto, tarjeta) aunque poco integrada al discurso; la mayoría participa, pero sin fluidez entre ellos; explican la idea con claridad básica en el tiempo dado, pero dudan o se bloquean ante preguntas espontáneas. |
| Bueno | 4.5 | Presentación personal cuidada y coherente con la identidad del proyecto; usan herramientas de apoyo pertinentes (prototipo, muestra, demo digital, video corto) que refuerzan el mensaje; todos los integrantes participan con roles claros y se complementan; explican la idea con claridad y fluidez sin depender del póster, y responden con seguridad la mayoría de las preguntas del jurado. |
| Excelente | 5.0 | Presentación personal impecable y alineada con la marca/proyecto; las herramientas de apoyo están integradas de forma natural y elevan la sustentación; el equipo muestra sinergia total (se turnan sin fricciones, se complementan al responder); explican la idea de forma clara, segura y memorable, y manejan con dominio cualquier pregunta espontánea, incluidas las incómodas. |

## INNPRENDE II · Aterrizaje: rúbrica del pitch

Pitch de proyecto de innovación. Pitch de 5 minutos ante jurado · Innovación y Emprendimiento II.

### 1. Propuesta de valor

| Nivel | Valor | Descripción |
|---|---|---|
| Muy deficiente | 0.0 | No presenta propuesta de valor, o el equipo no asistió al pitch. |
| Deficiente | 1.5 | La propuesta de valor es confusa, genérica o no resuelve ninguna necesidad identificable del cliente. |
| Aceptable | 3.0 | Propuesta de valor genérica ("ofrecemos calidad", "buen servicio"), sin diferenciación clara ni evidencia de validación con clientes. |
| Bueno | 4.0 | Propuesta de valor clara, con al menos un beneficio diferenciador, aunque sin evidencia de validación directa con clientes reales. |
| Muy bueno | 4.5 | Propuesta de valor clara y diferenciada, respaldada con evidencia de validación (encuestas, entrevistas) realizada con clientes reales. |
| Excelente | 5.0 | Propuesta de valor sólida y validada con datos de clientes reales; el equipo argumenta con criterio por qué es mejor que las alternativas existentes en el mercado. |

### 2. PitchDeck

| Nivel | Valor | Descripción |
|---|---|---|
| Muy deficiente | 0.0 | No presenta PitchDeck, o el equipo no asistió al pitch. |
| Deficiente | 1.5 | El PitchDeck tiene errores graves de estructura (faltan secciones clave) o es ilegible y desorganizado. |
| Aceptable | 3.0 | El PitchDeck cumple con la estructura básica esperada, pero el diseño es genérico y con exceso de texto en las diapositivas. |
| Bueno | 4.0 | PitchDeck bien estructurado y visualmente claro, con información suficiente para seguir la narrativa del negocio en los 5 minutos. |
| Muy bueno | 4.5 | PitchDeck profesional y visualmente atractivo, con jerarquía clara de información y una narrativa (storytelling) coherente de principio a fin. |
| Excelente | 5.0 | PitchDeck de calidad profesional: diseño impecable, narrativa persuasiva y cada diapositiva refuerza un mensaje clave del negocio sin saturar de texto. |

### 3. Modelo de negocio

| Nivel | Valor | Descripción |
|---|---|---|
| Muy deficiente | 0.0 | No presenta modelo de negocio, o el equipo no asistió al pitch. |
| Deficiente | 1.5 | El modelo de negocio es incoherente o no permite entender cómo el negocio genera ingresos. |
| Aceptable | 3.0 | Modelo de negocio identificado (ej. Canvas) pero con bloques incompletos o poco desarrollados. |
| Bueno | 4.0 | Modelo de negocio completo y coherente, con los bloques clave desarrollados (segmento, canales, fuentes de ingreso, estructura de costos). |
| Muy bueno | 4.5 | Modelo de negocio coherente y viable, con relación clara entre sus bloques y evidencia de un análisis financiero básico. |
| Excelente | 5.0 | Modelo de negocio robusto y validado con datos reales, con análisis de viabilidad financiera y potencial de escalabilidad. |

### 4. Prototipo

| Nivel | Valor | Descripción |
|---|---|---|
| Muy deficiente | 0.0 | No presenta prototipo ni evidencia de desarrollo, o el equipo no asistió al pitch. |
| Deficiente | 1.5 | El prototipo mencionado es solo una idea sin ningún desarrollo tangible. |
| Aceptable | 3.0 | Prototipo básico o boceto de baja fidelidad que ilustra la idea, pero no es funcional ni ha sido testeado. |
| Bueno | 4.0 | Prototipo de fidelidad media (mockup, maqueta o versión mínima) que permite visualizar claramente el producto o servicio. |
| Muy bueno | 4.5 | Prototipo funcional o de alta fidelidad, testeado con al menos un usuario o cliente real. |
| Excelente | 5.0 | Prototipo funcional, testeado con clientes reales, con evidencia de iteración y mejoras a partir de la retroalimentación recibida. |

### 5. Competidores

| Nivel | Valor | Descripción |
|---|---|---|
| Muy deficiente | 0.0 | No se menciona ningún competidor, o el equipo no asistió al pitch. |
| Deficiente | 1.5 | Se afirma "no tenemos competencia" sin ningún sustento, o se nombran competidores sin ningún análisis. |
| Aceptable | 3.0 | Se identifican competidores, pero sin describir sus fortalezas/debilidades ni un diferencial propio claro. |
| Bueno | 4.0 | Análisis de 2 o más competidores (directos e indirectos) con fortalezas y debilidades, y un diferencial propio explícito. |
| Muy bueno | 4.5 | Análisis comparativo riguroso de competidores con evidencia (precios, reseñas, participación de mercado) y un diferencial propio defendible. |
| Excelente | 5.0 | Análisis competitivo exhaustivo, con matriz comparativa, y una estrategia clara de cómo el negocio se posicionará frente a la competencia. |
