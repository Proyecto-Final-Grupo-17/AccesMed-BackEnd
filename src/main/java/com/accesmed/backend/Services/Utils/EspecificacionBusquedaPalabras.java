package com.accesmed.backend.Services.Utils;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * Arma la {@link Specification} de una búsqueda por palabras (canal chatbot,
 * {@code Docs/Diseno-Chatbot.md §3.4.4}): cada palabra tiene que aparecer, como parte de una
 * palabra, en alguno de los campos indicados. Compara contra {@code lower(unaccent(campo))}
 * (extensión {@code unaccent} creada por Liquibase), así que las palabras tienen que venir ya
 * normalizadas por {@link NormalizadorBusqueda}.
 */
public final class EspecificacionBusquedaPalabras {

    private EspecificacionBusquedaPalabras() {
    }

    //region ========== Métodos ==========

    /**
     * Construye la especificación: {@code AND} entre palabras, {@code OR} entre campos.
     *
     * @param palabras {@code List<String>} palabras normalizadas (minúsculas, sin tildes)
     * @param campos {@code List<SingularAttribute<? super T, String>>} campos de texto del metamodelo donde buscar
     * @param <T> tipo de la entidad
     * @return {@code Specification<T>} la especificación de la búsqueda (sin restricción si no hay palabras)
     */
    public static <T> Specification<T> buildSpecificationPalabras(List<String> palabras,
            List<SingularAttribute<? super T, String>> campos) {

        Specification<T> specificationPalabras = (root, query, cb) -> {

            List<Predicate> predicadosPalabras = palabras.stream()
                    .map(palabra -> cb.or(campos.stream()
                            .map(campo -> cb.like(
                                    cb.lower(cb.function("unaccent", String.class, root.get(campo))),
                                    "%" + palabra + "%"))
                            .toArray(Predicate[]::new)))
                    .toList();

            return cb.and(predicadosPalabras.toArray(Predicate[]::new));

        };
        return specificationPalabras;

    }

    //endregion

}
