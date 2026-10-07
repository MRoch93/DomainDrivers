package pl.edu.parallelization.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Relacja między etapami. Źródło jest opcjonalną informacją opisową. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = BeforeConstraint.class, name = "BEFORE"),
    @JsonSubTypes.Type(value = NotWithConstraint.class, name = "NOT_WITH"),
    @JsonSubTypes.Type(value = WithConstraint.class, name = "WITH")
})
public sealed interface Constraint permits BeforeConstraint, NotWithConstraint, WithConstraint {
    Stage first();
    Stage second();
    ConstraintSource source();
}
