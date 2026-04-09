package com.orion.horizon;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

/**
 * Example JPA entity defined as a Panache Entity.
 *
 * <p>An ID field of Long type is provided. If you want to define your own ID
 * field, extend {@code PanacheEntityBase} instead.
 *
 * <p>This uses the active record pattern. You can also use the repository
 * pattern instead. See
 * {@see https://quarkus.io/guides/hibernate-orm-panache#solution-2-using-the-repository-pattern}.
 *
 * Usage:
 *
 * {@code
 *     public void doSomething() {
 *         MyEntity entity1 = new MyEntity();
 *         entity1.field = "field-1";
 *         entity1.persist();
 *
 *         List<MyEntity> entities = MyEntity.listAll();
 *     }
 * }
 */
@Entity
public class MyEntity extends PanacheEntity {
    /** Example field. */
    public String field;
}
