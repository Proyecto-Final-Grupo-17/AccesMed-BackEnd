package com.accesmed.backend.Records.Usuario.Criteria;

/**
 * Estado de baja lógica a filtrar en el listado de {@code Usuario}. A diferencia del
 * resto de los listados del sistema (que siempre excluyen los dados de baja sin
 * exponerlo como filtro), acá el SuperAdmin necesita poder ver también los inactivos.
 */
public enum EstadoUsuarioFiltro {

    ACTIVO,
    INACTIVO,
    TODOS

}
