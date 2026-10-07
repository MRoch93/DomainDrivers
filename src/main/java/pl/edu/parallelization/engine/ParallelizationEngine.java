package pl.edu.parallelization.engine;

import pl.edu.parallelization.domain.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mały, edukacyjny solver CSP. Każde wywołanie ma własny stan przeszukiwania. */
public final class ParallelizationEngine {
    /**
     * Zwraca wszystkie optymalne podziały, po jednym poprawnym numerowaniu każdego.
     * Sprzeczny problem daje pustą listę; brak etapów daje jedno puste rozwiązanie.
     * Powtórzone etapy i ograniczenia wskazujące nieznane etapy są błędami wejścia.
     */
    public List<Solution> solve(List<Stage> stages, List<Constraint> constraints) {
        stages = List.copyOf(stages);
        constraints = List.copyOf(constraints);
        Map<Stage, Integer> indices = new HashMap<>();
        for (int i = 0; i < stages.size(); i++) {
            if (indices.put(stages.get(i), i) != null) {
                throw new IllegalArgumentException("Powtórzony etap: " + stages.get(i).id());
            }
        }
        for (Constraint constraint : constraints) {
            if (!indices.containsKey(constraint.first()) || !indices.containsKey(constraint.second())) {
                throw new IllegalArgumentException("Ograniczenie wskazuje etap spoza listy etapów");
            }
        }
        if (stages.isEmpty()) {
            return List.of(new Solution(List.of()));
        }

        // Domeny początkowe to 1..N. Sprawdzamy kolejno mniejsze domeny 1..k:
        // pierwsze k mające rozwiązania jest minimum liczby użytych grup.
        for (int groupCount = 1; groupCount <= stages.size(); groupCount++) {
            Search search = new Search(stages, constraints, indices, groupCount);
            search.assign(0, 0);
            if (!search.solutions.isEmpty()) {
                return List.copyOf(search.solutions.values());
            }
        }
        return List.of();
    }

    private static final class Search {
        private final List<Stage> stages;
        private final List<Constraint> constraints;
        private final Map<Stage, Integer> indices;
        private final int groupCount;
        private final int[] assignment; // 0 oznacza jeszcze nieprzypisany etap.
        private final int[] membersPerGroup;
        private final boolean hasBefore;
        private final Map<List<Integer>, Solution> solutions = new LinkedHashMap<>();

        private Search(List<Stage> stages, List<Constraint> constraints,
                       Map<Stage, Integer> indices, int groupCount) {
            this.stages = stages;
            this.constraints = constraints;
            this.indices = indices;
            this.groupCount = groupCount;
            assignment = new int[stages.size()];
            membersPerGroup = new int[groupCount + 1];
            hasBefore = constraints.stream().anyMatch(c -> c instanceof BeforeConstraint);
        }

        private void assign(int stageIndex, int usedGroups) {
            // Każda grupa 1..k musi być użyta. Jeśli zabraknie etapów, cofamy się.
            if (groupCount - usedGroups > stages.size() - stageIndex) {
                return;
            }
            if (stageIndex == stages.size()) {
                saveSolution();
                return;
            }

            // Bez BEFORE nazwy grup są dowolne: nowa grupa ma następny numer.
            // Z BEFORE nie wolno narzucać kolejności według listy wejściowej.
            int limit = hasBefore ? groupCount : Math.min(groupCount, usedGroups + 1);
            for (int group = 1; group <= limit; group++) {
                boolean newGroup = membersPerGroup[group] == 0;
                assignment[stageIndex] = group;
                membersPerGroup[group]++;
                if (satisfiesAssignedConstraints()) {
                    assign(stageIndex + 1, usedGroups + (newGroup ? 1 : 0));
                }
                // Backtracking: usuwamy próbne przypisanie przed kolejną próbą.
                membersPerGroup[group]--;
                assignment[stageIndex] = 0;
            }
        }

        private boolean satisfiesAssignedConstraints() {
            for (Constraint constraint : constraints) {
                int first = assignment[indices.get(constraint.first())];
                int second = assignment[indices.get(constraint.second())];
                if (first == 0 || second == 0) {
                    continue;
                }
                boolean satisfied = switch (constraint) {
                    case BeforeConstraint ignored -> first < second;
                    case NotWithConstraint ignored -> first != second;
                    case WithConstraint ignored -> first == second;
                };
                if (!satisfied) {
                    return false;
                }
            }
            return true;
        }

        private void saveSolution() {
            // Klucz opisuje wyłącznie podział, niezależnie od numerów grup.
            Map<Integer, Integer> canonicalNumbers = new HashMap<>();
            List<Integer> partitionKey = new ArrayList<>();
            for (int group : assignment) {
                canonicalNumbers.putIfAbsent(group, canonicalNumbers.size() + 1);
                partitionKey.add(canonicalNumbers.get(group));
            }
            if (solutions.containsKey(partitionKey)) {
                return;
            }
            List<ParallelGroup> groups = new ArrayList<>();
            for (int group = 1; group <= groupCount; group++) {
                List<Stage> members = new ArrayList<>();
                for (int i = 0; i < stages.size(); i++) {
                    if (assignment[i] == group) {
                        members.add(stages.get(i));
                    }
                }
                groups.add(new ParallelGroup(group, members));
            }
            // Zachowujemy znalezioną numerację spełniającą BEFORE, a nie klucz.
            solutions.put(List.copyOf(partitionKey), new Solution(groups));
        }
    }
}
