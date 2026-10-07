# Grupowanie etapów projektu

Edukacyjny projekt Maven dla Java 21. Zawiera model danych, prosty silnik CSP
z backtrackingiem i testy. Nie ma jeszcze obsługi pliku wejściowego ani interfejsu
uruchomieniowego.

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
- `src/test/java/pl/edu/parallelization/domain/` — testy modelu i JSON.
- `src/test/java/pl/edu/parallelization/engine/` — testy silnika.
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
