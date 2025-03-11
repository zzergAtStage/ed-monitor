package com.zergatstage.repo;

import java.util.List;

/**
 * A generic repository interface for CRUD operations using Reflection API.
 *
 * @param <T> The type of the entity.
 */
public interface GenericRepository<T> {
    T findById(Object id);
    List<T> findAll();
    void save(T entity);
    void update(T entity);
    void delete(T entity);
}