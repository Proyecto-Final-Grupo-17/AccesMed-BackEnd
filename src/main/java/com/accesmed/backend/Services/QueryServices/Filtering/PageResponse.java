package com.accesmed.backend.Services.QueryServices.Filtering;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope de paginación para los endpoints de listado con filtrado dinámico. Se prefiere
 * a serializar {@link Page} directo (formato inestable, Spring lo desaconseja) o a mandar
 * la paginación por headers HTTP (más incómodo de consumir para el bot de WhatsApp/Flowise
 * que para el panel).
 *
 * @param content {@code List<T>} contenido de la página actual
 * @param page {@code int} número de página, base 0
 * @param size {@code int} tamaño de página solicitado
 * @param totalElements {@code long} cantidad total de elementos que cumplen el criteria
 * @param totalPages {@code int} cantidad total de páginas
 * @param <T> tipo del contenido de la página
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    /**
     * Arma un {@code PageResponse} a partir de una {@link Page} de entidades, mapeando su
     * contenido al tipo de response de cada endpoint.
     *
     * @param pagina {@code Page<E>} página de entidades devuelta por el {@code QueryService}
     * @param mapeador {@code Function<E, T>} conversión de cada entidad a su record de respuesta
     * @return {@code PageResponse<T>} envelope de paginación con el contenido ya mapeado
     * @param <E> tipo de la entidad de origen
     * @param <T> tipo del record de respuesta
     */
    public static <E, T> PageResponse<T> from(Page<E> pagina, Function<E, T> mapeador) {

        PageResponse<T> pageResponse = new PageResponse<>(
                pagina.getContent().stream().map(mapeador).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages()
        );
        return pageResponse;

    }

}
