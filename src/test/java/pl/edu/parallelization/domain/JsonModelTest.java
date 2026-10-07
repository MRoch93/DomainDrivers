package pl.edu.parallelization.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JsonModelTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void solutionMatchesRequestedJsonAndCanBeReadBack() throws Exception {
        Solution solution = new Solution(List.of(
                new ParallelGroup(1, List.of(new Stage("A"), new Stage("C"))),
                new ParallelGroup(2, List.of(new Stage("B"), new Stage("D")))));
        String expected = """
                {"groups": [
                  {"groupNumber": 1, "stages": ["A", "C"]},
                  {"groupNumber": 2, "stages": ["B", "D"]}
                ]}
                """;
        String json = mapper.writeValueAsString(solution);
        assertEquals(mapper.readTree(expected), mapper.readTree(json));
        assertEquals(solution, mapper.readValue(expected, Solution.class));
    }

    @Test
    void constraintTypeAndSourceSurviveJsonRoundTrip() throws Exception {
        Stage a = new Stage("A");
        Stage b = new Stage("B");
        List<Constraint> constraints = List.of(
                new BeforeConstraint(a, b, ConstraintSource.LOGICAL),
                new NotWithConstraint(a, b, ConstraintSource.RESOURCE),
                new WithConstraint(a, b, ConstraintSource.FUNDING));
        List<String> types = List.of("BEFORE", "NOT_WITH", "WITH");
        for (int i = 0; i < constraints.size(); i++) {
            Constraint constraint = constraints.get(i);
            String json = mapper.writerFor(Constraint.class).writeValueAsString(constraint);
            assertEquals(types.get(i), mapper.readTree(json).get("type").asText());
            assertEquals(constraint, mapper.readValue(json, Constraint.class));
        }
    }

    @Test
    void constraintJsonMayOmitSource() throws Exception {
        Constraint constraint = mapper.readValue(
                """
                {"type":"WITH", "first":"A", "second":"B"}
                """, Constraint.class);
        assertEquals(new WithConstraint(new Stage("A"), new Stage("B")), constraint);
    }
}
