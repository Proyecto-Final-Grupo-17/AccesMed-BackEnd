package com.accesmed.backend.Services.QueryServices.Filtering;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.metamodel.SingularAttribute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.function.Function;

/**
 * Base para los {@code QueryService} que soportan filtrado dinámico. Da
 * {@link #findByCriteria} gratis (arma la {@link Specification} vía
 * {@link #createSpecification} y delega en el repositorio) y expone los helpers para
 * traducir cada {@link Filter} del criteria a un fragmento de {@code Specification}, usando
 * el metamodelo estático de JPA (ej. {@code Prestacion_.codigo}) para no referenciar
 * campos como string literal.
 *
 * @param <ENTIDAD> entidad JPA sobre la que se filtra
 * @param <CRITERIA> objeto de criteria correspondiente a esa entidad
 */
public abstract class AbstractFiltroQueryService<ENTIDAD, CRITERIA> {

    /**
     * Repositorio de la entidad, usado por {@link #findByCriteria} para ejecutar la
     * {@link Specification} armada. Cada subclase devuelve su propio repositorio ya
     * inyectado.
     *
     * @return {@code JpaSpecificationExecutor<ENTIDAD>} repositorio de la entidad
     */
    protected abstract JpaSpecificationExecutor<ENTIDAD> getRepository();

    /**
     * Traduce el criteria de la entidad a una {@link Specification}, combinando un
     * fragmento por cada campo filtrable que vino con valor.
     *
     * @param criteria {@code CRITERIA} filtros a aplicar, o {@code null} para no filtrar
     * @return {@code Specification<ENTIDAD>} especificación equivalente al criteria
     */
    protected abstract Specification<ENTIDAD> createSpecification(CRITERIA criteria);

    /**
     * Busca entidades que cumplen el criteria, paginadas.
     *
     * @param criteria {@code CRITERIA} filtros a aplicar, o {@code null} para no filtrar
     * @param pageable {@code Pageable} página solicitada
     * @return {@code Page<ENTIDAD>} página de resultados que cumplen el criteria
     */
    public Page<ENTIDAD> findByCriteria(CRITERIA criteria, Pageable pageable) {

        Page<ENTIDAD> paginaResultado = getRepository().findAll(createSpecification(criteria), pageable);
        return paginaResultado;

    }

    /**
     * Busca la única entidad que cumple el criteria (típicamente un criteria armado con
     * igualdad por {@code id}). Base para los {@code find<Entidad>ById} que necesitan
     * pasar por el filtrado dinámico en vez de un {@code findById} directo del repositorio.
     *
     * @param criteria {@code CRITERIA} filtros a aplicar
     * @return {@code Optional<ENTIDAD>} la entidad si existe y cumple el criteria
     */
    public Optional<ENTIDAD> findOneByCriteria(CRITERIA criteria) {

        Optional<ENTIDAD> entidadEncontrada = getRepository().findOne(createSpecification(criteria));
        return entidadEncontrada;

    }

    /**
     * Arma la {@link Specification} de un campo simple del metamodelo a partir de un
     * {@link Filter} genérico ({@code equals}/{@code notEquals}/{@code in}/{@code notIn}/
     * {@code specified}).
     *
     * @param filter {@code Filter<X>} filtro con los operadores a aplicar, o {@code null}
     * @param field {@code SingularAttribute<? super ENTIDAD, X>} campo del metamodelo
     * @param <X> tipo del campo filtrado
     * @return {@code Specification<ENTIDAD>} fragmento resultante, o {@code null} si el filtro es nulo
     */
    protected <X> Specification<ENTIDAD> buildSpecification(Filter<X> filter, SingularAttribute<? super ENTIDAD, X> field) {

        return filter == null ? null : buildSpecification(filter, root -> root.get(field));

    }

    /**
     * Variante de {@link #buildSpecification(Filter, SingularAttribute)} para campos que
     * requieren navegar una relación (ej. el id de una entidad relacionada vía {@code join}).
     *
     * @param filter {@code Filter<X>} filtro con los operadores a aplicar, o {@code null}
     * @param path {@code Function<Root<ENTIDAD>, Expression<X>>} cómo llegar al campo desde el root
     * @param <X> tipo del campo filtrado
     * @return {@code Specification<ENTIDAD>} fragmento resultante, o {@code null} si el filtro es nulo
     */
    protected <X> Specification<ENTIDAD> buildSpecification(Filter<X> filter, Function<Root<ENTIDAD>, Expression<X>> path) {

        if (filter == null) {
            return null;
        }

        Specification<ENTIDAD> specification = null;

        if (filter.getEquals() != null) {
            specification = and(specification, (root, query, cb) -> cb.equal(path.apply(root), filter.getEquals()));
        }
        if (filter.getNotEquals() != null) {
            specification = and(specification, (root, query, cb) -> cb.notEqual(path.apply(root), filter.getNotEquals()));
        }
        if (filter.getIn() != null) {
            specification = and(specification, (root, query, cb) -> path.apply(root).in(filter.getIn()));
        }
        if (filter.getNotIn() != null) {
            specification = and(specification, (root, query, cb) -> path.apply(root).in(filter.getNotIn()).not());
        }
        if (filter.getSpecified() != null) {
            specification = and(specification, filter.getSpecified()
                    ? (root, query, cb) -> cb.isNotNull(path.apply(root))
                    : (root, query, cb) -> cb.isNull(path.apply(root)));
        }

        return specification;

    }

    /**
     * Arma la {@link Specification} de un campo comparable del metamodelo, agregando los
     * operadores de rango ({@code greaterThan}/{@code lessThan}/{@code greaterThanOrEqual}/
     * {@code lessThanOrEqual}) a los heredados de {@link Filter}.
     *
     * @param filter {@code RangeFilter<X>} filtro con los operadores a aplicar, o {@code null}
     * @param field {@code SingularAttribute<? super ENTIDAD, X>} campo del metamodelo
     * @param <X> tipo del campo filtrado, debe ser {@link Comparable}
     * @return {@code Specification<ENTIDAD>} fragmento resultante, o {@code null} si el filtro es nulo
     */
    protected <X extends Comparable<? super X>> Specification<ENTIDAD> buildRangeSpecification(
            RangeFilter<X> filter, SingularAttribute<? super ENTIDAD, X> field) {

        return filter == null ? null : buildRangeSpecification(filter, root -> root.get(field));

    }

    /**
     * Variante de {@link #buildRangeSpecification(RangeFilter, SingularAttribute)} para
     * campos que requieren navegar una relación.
     *
     * @param filter {@code RangeFilter<X>} filtro con los operadores a aplicar, o {@code null}
     * @param path {@code Function<Root<ENTIDAD>, Expression<X>>} cómo llegar al campo desde el root
     * @param <X> tipo del campo filtrado, debe ser {@link Comparable}
     * @return {@code Specification<ENTIDAD>} fragmento resultante, o {@code null} si el filtro es nulo
     */
    protected <X extends Comparable<? super X>> Specification<ENTIDAD> buildRangeSpecification(
            RangeFilter<X> filter, Function<Root<ENTIDAD>, Expression<X>> path) {

        if (filter == null) {
            return null;
        }

        Specification<ENTIDAD> specification = buildSpecification(filter, path);

        if (filter.getGreaterThan() != null) {
            specification = and(specification, (root, query, cb) -> cb.greaterThan(path.apply(root), filter.getGreaterThan()));
        }
        if (filter.getLessThan() != null) {
            specification = and(specification, (root, query, cb) -> cb.lessThan(path.apply(root), filter.getLessThan()));
        }
        if (filter.getGreaterThanOrEqual() != null) {
            specification = and(specification, (root, query, cb) -> cb.greaterThanOrEqualTo(path.apply(root), filter.getGreaterThanOrEqual()));
        }
        if (filter.getLessThanOrEqual() != null) {
            specification = and(specification, (root, query, cb) -> cb.lessThanOrEqualTo(path.apply(root), filter.getLessThanOrEqual()));
        }

        return specification;

    }

    /**
     * Arma la {@link Specification} de un campo de texto, agregando {@code contains}
     * (contención, sin distinguir mayúsculas/minúsculas) a los operadores heredados de
     * {@link Filter}.
     *
     * @param filter {@code StringFilter} filtro con los operadores a aplicar, o {@code null}
     * @param field {@code SingularAttribute<? super ENTIDAD, String>} campo del metamodelo
     * @return {@code Specification<ENTIDAD>} fragmento resultante, o {@code null} si el filtro es nulo
     */
    protected Specification<ENTIDAD> buildStringSpecification(StringFilter filter, SingularAttribute<? super ENTIDAD, String> field) {

        if (filter == null) {
            return null;
        }

        Function<Root<ENTIDAD>, Expression<String>> path = root -> root.get(field);
        Specification<ENTIDAD> specification = buildSpecification(filter, path);

        if (filter.getContains() != null) {
            specification = and(specification, (root, query, cb) ->
                    cb.like(cb.lower(path.apply(root)), "%" + filter.getContains().toLowerCase() + "%"));
        }

        return specification;

    }

    private Specification<ENTIDAD> and(Specification<ENTIDAD> base, Specification<ENTIDAD> extra) {

        return base == null ? Specification.where(extra) : base.and(extra);

    }

}
