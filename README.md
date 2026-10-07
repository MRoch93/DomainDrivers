# Grupowanie etapów projektu

Edukacyjny projekt Maven dla Java 21. Zawiera dane wejściowe managera,
ich tłumaczenie na model domenowy, silnik CSP z backtrackingiem oraz zapis wyniku
do JSON. Bez Springa, REST API, bazy danych i zewnętrznego solvera.

## Uruchomienie aplikacji

Przykładowe dane managera znajdują się w `input.json`. W PowerShell uruchom:

```powershell
.\run.cmd
```

Skrypt buduje aplikację i zapisuje `output.json`. Działa także uruchomiony
z innego katalogu: bazą wszystkich względnych ścieżek jest katalog skryptu.
Własne pliki można wskazać dwoma argumentami:

```powershell
.\run.cmd examples\shared-resources.json output.json
```

Ten przykład daje dwa optymalne podziały: `A,C | B,D` oraz `A,D | B,C`.
Skrypt wymaga JDK 21, PowerShell i przy pierwszym budowaniu dostępu do internetu.
Podczas uruchamiania pomija testy; do sprawdzenia ich użyj komendy poniżej.

Można też zbudować i uruchomić pojedynczy plik JAR zawierający Jackson:

```powershell
.\mvnw.cmd package
java -jar target/parallelization-engine-1.0-SNAPSHOT.jar input.json output.json
```

Bez argumentów Java używa `input.json` i `output.json` w bieżącym katalogu.
`Main` przyjmuje zero albo dwa argumenty. Niepoprawne dane lub błąd plikowy
dają komunikat `Błąd: ...` i kod zakończenia 1. Poprawnie odczytany, lecz sprzeczny
problem daje raport `NO_SOLUTION` i kod 0 — to wynik obliczeń.

Przed odczytem wejścia aplikacja usuwa poprzedni raport; przy błędzie zapisu
usuwa także częściowy wynik. `run.cmd` usuwa poprzedni raport jeszcze przed
budowaniem, więc błąd kompilacji nie zostawia starego wyniku. Wejście i wyjście
muszą być różnymi plikami. Domyślny `output.json` jest pomijany przez Git.

## Dane managera i mapowanie

Przepływ:

```text
input.json -> ManagerInput -> walidacja -> ManagerInputMapper
           -> ParallelizationProblem -> ParallelizationEngine
           -> List<Solution> -> ParallelizationOutput -> output.json
```

Manager podaje:

- `stages`: listę etapów z niepustymi `id`, `name` i wymaganym `funded` (true/false).
- `resources`: nazwy zasobów; pusta lub pominięta lista oznacza brak zasobów.
- `logicalDependencies` i `legalDependencies`: listy par `before`/`after`;
  listy można pominąć.

Identyfikatory muszą być unikalne. Zależność musi wskazywać istniejące, różne
etapy. Nazwy zasobów muszą być niepuste. Identyfikatory i nazwy zasobów porównuje
się dosłownie, z rozróżnieniem wielkości liter. Nieznane pola JSON są odrzucane;
manager nie przekazuje bezpośrednio ograniczeń solvera. Puste `stages: []` jest
dozwolone: daje jeden pusty wynik z zerem grup.

| Informacja managera | Ograniczenie Deep Model |
| --- | --- |
| Zależność logiczna A przed C | `BeforeConstraint(A,C,LOGICAL)` |
| Zależność prawna C przed D | `BeforeConstraint(C,D,LEGAL)` |
| Co najmniej jeden wspólny zasób A i B | `NotWithConstraint(A,B,RESOURCE)` |
| A funded=true, D funded=false | `BeforeConstraint(A,D,FUNDING)` |

Finansowanie jest twardą regułą: **każdy finansowany etap musi poprzedzać każdy
niefinansowany**. Taką decyzję podejmuje mapper; silnik nadal interpretuje samo
BEFORE, bez sprawdzania źródła. Wspólny zasób zabrania wspólnej grupy, ale nie
narzuca kolejności.

Nazwa etapu zostaje w danych managera. Do solvera trafia `Stage` z samym `id`.
Model managera nie ma reguły tworzącej WITH; silnik nadal obsługuje ten typ.

Powtórzona relacja tego samego typu i tych samych etapów jest zapisywana raz.
Zachowywane jest pierwsze źródło w kolejności LOGICAL, LEGAL, RESOURCE, FUNDING.
To wybór opisu, a nie priorytet reguły. Obecny `Constraint` ma pojedyncze źródło,
więc raport nie pokazuje dodatkowych źródeł identycznej relacji. Różne typy
ograniczeń dla tej samej pary pozostają osobnymi wpisami.

W głównym przykładzie powstaje sześć ograniczeń: BEFORE(A,C,LOGICAL),
BEFORE(C,D,LEGAL), NOT_WITH(A,B,RESOURCE), NOT_WITH(C,D,RESOURCE),
BEFORE(A,D,FUNDING), BEFORE(B,D,FUNDING). Finansowe BEFORE(C,D) jest duplikatem
reguły prawnej. Wynik ma trzy grupy: `A | B,C | D`.

## Raport output.json

`ParallelizationOutput` zawiera `status`, `normalizedConstraints`,
`optimalGroupCount`, `solutionCount` i `solutions`. Ograniczenia w raporcie są
dokładnie tymi, które otrzymał solver. Numery grup są ciągłe i zaczynają się od 1.

- `OK`: znaleziono wszystkie różne optymalne podziały.
- `NO_SOLUTION`: ograniczenia są sprzeczne; liczba grup wynosi `null`, liczba
  rozwiązań 0, a lista rozwiązań jest pusta. Znormalizowane ograniczenia pozostają
  w raporcie, aby można było zbadać przyczynę.

Błędy formatu i walidacji wejścia nie są `NO_SOLUTION`: aplikacja zgłasza błąd
i nie tworzy raportu.

## Uruchomienie testów

Potrzebujesz JDK 21 (np. Temurin). Maven nie musi być zainstalowany.
W PowerShell, w katalogu projektu, uruchom:

```powershell
.\mvnw.cmd test
```

Na Linux/macOS: `sh mvnw test`. Wrapper przy pierwszym uruchomieniu pobierze
Maven 3.9.11, a Maven pobierze biblioteki, więc potrzebne jest połączenie z internetem.
Jeśli ustawiasz `JAVA_HOME`, wskaż katalog JDK 21.

## Struktura

- `pom.xml` — ustawienia Java 21, Jackson (JSON) i JUnit 5 (testy).
- `mvnw`, `mvnw.cmd`, `.mvn/wrapper/` — oficjalny Maven Wrapper 3.3.4.
- `src/main/java/pl/edu/parallelization/domain/` — model domenowy.
- `src/main/java/pl/edu/parallelization/engine/` — silnik CSP.
- `src/main/java/pl/edu/parallelization/input/` — rekordy managera, walidator i mapper.
- `src/main/java/pl/edu/parallelization/application/` — obsługa przepływu plikowego.
- `src/main/java/pl/edu/parallelization/output/` — format raportu.
- `src/main/java/pl/edu/parallelization/Main.java` — punkt startowy aplikacji.
- `src/test/java/pl/edu/parallelization/domain/` — testy modelu i JSON.
- `src/test/java/pl/edu/parallelization/engine/` — testy silnika.
- `src/test/java/pl/edu/parallelization/input/` — testy mapowania i walidacji.
- `src/test/java/pl/edu/parallelization/application/` — testy E2E, błędów JSON i raportów.
- `input.json`, `examples/shared-resources.json` — przykładowe wejścia.
- `run.cmd` — budowanie i uruchamianie w Windows.
- `target/` — generowane pliki kompilacji i raporty testów; pomijane przez Git.

W `mvnw.cmd` dodano małą poprawkę obsługi katalogów bez dowiązania,
aby skrypt działał również wtedy, gdy PowerShell zwraca pustą właściwość `Target`.

## Model danych

Klasy są rekordami Java: krótkimi, niemutowalnymi obiektami danych.
Ich pola odczytuje się metodami, np. `stage.id()` lub `solution.groups()`.

| Typ | Znaczenie |
| --- | --- |
| `Stage` | Etap z niepustym identyfikatorem, np. `A`. Nie ma numeru grupy. |
| `Constraint` | Wspólny interfejs trzech typów ograniczeń. |
| `BeforeConstraint` | BEFORE(A,B): `group(A) < group(B)`. Kolejność ma znaczenie. |
| `NotWithConstraint` | NOT_WITH(A,B): `group(A) != group(B)`. |
| `WithConstraint` | WITH(A,B): `group(A) == group(B)`. |
| `ConstraintSource` | `LOGICAL`, `LEGAL`, `RESOURCE`, `FUNDING`. |
| `ParallelGroup` | Dodatni numer grupy i niepusta lista etapów. |
| `Solution` | Lista grup przyszłego wyniku; może być pusta. |
| `ParallelizationProblem` | Niemutowalne listy etapów i ograniczeń przekazywane solverowi. |

Ograniczenia przechowują `first`, `second` i opcjonalne `source`.
Konstruktor z dwoma etapami ustawia źródło na `null` (brak informacji).
Źródło jest wyłącznie informacyjne i nie zmienia znaczenia relacji.
Identyczne identyfikatory oznaczają ten sam etap. Identyfikatory są zachowywane
dosłownie, z rozróżnieniem wielkości liter.

Listy w wyniku są kopiowane i nie można ich modyfikować. Model sprawdza podstawową
poprawność pól, ale nie sprawdza spełnienia ograniczeń, sprzeczności ani optymalności
grupowania. Spełnienie ograniczeń sprawdza silnik.

## Silnik CSP

```java
ParallelizationEngine engine = new ParallelizationEngine();
List<Solution> results = engine.solve(
    List.of(new Stage("A"), new Stage("B")),
    List.of(new NotWithConstraint(new Stage("A"), new Stage("B")))
);
```

Silnik znajduje przypisania `Stage -> GroupNumber`. Początkowy zakres numerów
to `1..N`, gdzie N jest liczbą etapów. Sprawdza kolejno zakresy `1..k`, zaczynając
od jednej grupy. Backtracking próbuje numerów dla kolejnych etapów, odrzuca
częściowe przypisania łamiące ograniczenia i cofa przypisanie przed kolejną próbą.
Wymaga użycia każdej grupy `1..k`, więc wynik nie ma luk.

Pierwszy zakres mający rozwiązania daje minimalną liczbę grup. Silnik zwraca
wszystkie różne podziały dla tego minimum. Numerowania tego samego podziału
uznaje za równoważne; zachowuje jeden wariant spełniający BEFORE. Bez BEFORE
nowe grupy otrzymują numery w kolejności pojawiania się, co pomija zamiany numerów
już podczas przeszukiwania. Z BEFORE sprawdzane są wszystkie numery, a powtórzenia
podziału są usuwane przy zapisie wyniku. Źródło ograniczenia nie wpływa na wynik.

Przykład: `A BEFORE C` i niezależny B dają dwa optymalne podziały:
`A,B | C` oraz `A | B,C`.

Sprzeczne ograniczenia dają pustą listę wyników. Pusta lista etapów daje jedno
puste rozwiązanie. Powtórzone identyfikatory etapów lub ograniczenia dotyczące
etapów spoza wejścia powodują `IllegalArgumentException`. Dane `null` są odrzucane.
Silnik nie zmienia wejścia ani klas domenowych. Każde wywołanie ma osobny stan.

To wersja edukacyjna: liczba prób może rosnąć wykładniczo, a zebranie wszystkich
optymalnych podziałów może wymagać dużo czasu i pamięci.

## Przykład użycia

```java
Stage a = new Stage("A");
Stage b = new Stage("B");
Constraint constraint = new BeforeConstraint(a, b, ConstraintSource.LOGICAL);

// Ręcznie utworzony przykład wyniku, bez obliczeń:
Solution solution = new Solution(List.of(
    new ParallelGroup(1, List.of(a, new Stage("C"))),
    new ParallelGroup(2, List.of(b, new Stage("D")))
));
ObjectMapper mapper = new ObjectMapper();
String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(solution);
Solution restored = mapper.readValue(json, Solution.class);
```

Powyższy fragment wymaga importów `java.util.List`,
`com.fasterxml.jackson.databind.ObjectMapper` i klas z pakietu
`pl.edu.parallelization.domain`; operacje JSON mogą zgłaszać wyjątki.

Jackson zapisuje etapy jako identyfikatory tekstowe:

```json
{
  "groups": [
    {"groupNumber": 1, "stages": ["A", "C"]},
    {"groupNumber": 2, "stages": ["B", "D"]}
  ]
}
```

Ograniczenie w JSON ma jawny typ, aby można było odtworzyć odpowiednią klasę:

```json
{"type": "BEFORE", "first": "A", "second": "B", "source": "LOGICAL"}
```

Przy odczycie ograniczenia użyj `mapper.readValue(json, Constraint.class)`.
Pole `source` można pominąć lub przekazać jako `null`.
