# Grupowanie etapów projektu

Edukacyjny projekt Maven dla Java 21. Zawiera dane wejściowe managera,
ich tłumaczenie na model domenowy, silnik CSP z backtrackingiem oraz zapis wyniku
do JSON oraz lokalne HTTP API. Bez Springa, bazy danych i zewnętrznego solvera.

## Lokalne HTTP API

Wymagane JDK 21. Uruchom w PowerShell:

```powershell
.\run-api.cmd
```

Skrypt buduje aplikację i uruchamia serwer JDK `HttpServer` na porcie 8080,
wyłącznie na adresie loopback (localhost). Zakończ przez Ctrl+C.
Interfejs plikowy `run.cmd` pozostaje dostępny i może działać równolegle.

W drugim oknie PowerShell:

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/health'
$manager = Get-Content -Raw -Encoding UTF8 .\input.json | ConvertFrom-Json
$body = @{ returnAllSolutions = $false; managerInput = $manager } | ConvertTo-Json -Depth 20
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/parallelization' -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($body)) | ConvertTo-Json -Depth 20
```

`GET /api/health` zwraca HTTP 200 i `{"status":"UP"}`.
`POST /api/parallelization` przyjmuje wrapper API:

```json
{
  "returnAllSolutions": false,
  "managerInput": {
    "stages": [{"id": "A", "name": "Analiza", "funded": true, "resources": []}],
    "logicalDependencies": [],
    "legalDependencies": []
  }
}
```

Przykładowy gotowy request: `examples/api-request.json`.

Pominięcie `returnAllSolutions` oznacza false. `managerInput` zachowuje format
plikowego input.json; flaga nie należy do ManagerInput.
Odpowiedź zawiera zawsze `status`, `normalizedConstraints`, `optimalGroupCount`,
`totalSolutionCount`, `returnedSolutionCount`, `selectedSolution`, `allSolutions`
i `selectionPolicy` (DETERMINISTIC_DEFAULT).
Dla false `allSolutions` zawiera tylko wybrany wynik, dla true wszystkich kandydatów.
`selectedSolution` pozostaje identyczne. Jeden kandydat daje obie liczby równe 1;
brak kandydatów daje null jako selectedSolution, pustą listę oraz obie liczby równe 0.

Sprzeczne ograniczenia również dają HTTP 200, z wynikiem `NO_SOLUTION`.
Błędy JSON lub danych managera dają HTTP 400 i `{"error":"opis problemu"}`.
Nieznany endpoint daje 404, nieobsługiwana metoda 405 z nagłówkiem `Allow`.
Nieoczekiwany błąd aplikacji daje 500 z ogólnym komunikatem, bez stack trace.

CSP kończy pracę zwróceniem listy wszystkich optymalnych podziałów (jak dotąd,
jedna poprawna numeracja każdego podziału). Application wybiera istniejącego kandydata:

```text
ManagerInput -> validation / mapper -> ParallelizationProblem
 -> ParallelizationEngine -> List<Solution> CSP candidates
 -> SolutionSelectionContext + DeterministicSolutionSelector -> selectedSolution
 -> ParallelizationApiResponse (returnAllSolutions filtruje tylko allSolutions)
```

Reguły w `application/selection` działają kolejno jako tie-breakery:
`ResourceCountRule` minimalizuje sumę `resources.size() * groupNumber` dla wszystkich
etapów. Brak resources oznacza zero. `StageIdRule` porównuje leksykograficznie
reprezentacje takie jak `A,C|B,D`: grupy według numerów, ID w grupie alfabetycznie.
To techniczny fallback. `SolutionSelectionRule` porównuje poprawnych kandydatów,
a Constraint określa ich dopuszczalność. Nowa reguła (np. PriorityRule) implementuje
interfejs i trafia na właściwe miejsce uporządkowanej listy selectora, bez zmian CSP.
Zasoby pozostają poza Stage, w niemutowalnym SolutionSelectionContext.

Interfejs plikowy nadal używa `process()` i starego ParallelizationOutput,
zwracając wszystkich kandydatów. HTTP używa `processSelected()` i DTO API.

## Formularz webowy do testów E2E

Po uruchomieniu `run-api.cmd` otwórz **http://localhost:8080/**.
Strona korzysta wyłącznie z HTML, CSS i JavaScript, bez frameworków.

1. Kliknij `LOAD EXAMPLE`, aby wprowadzić cztery przykładowe etapy i zależności.
2. Kliknij `OBLICZ RÓWNOLEGŁOŚĆ`.
3. Porównaj trzy sekcje: wysłane dane managera, `normalizedConstraints`
   otrzymane z backendu oraz osobną sekcję WYBRANE ROZWIĄZANIE.
4. Zaznacz "Zwróć wszystkie rozwiązania", aby wyświetlić dodatkową sekcję
   WSZYSTKIE OPTYMALNE ROZWIĄZANIA. Checkbox jest domyślnie odznaczony.
   Wynik pokazuje liczbę znalezionych i zwróconych rozwiązań oraz politykę
   RESOURCE_COUNT_EARLIER → STAGE_ID.

Przykład daje sześć ograniczeń, trzy grupy i jedno rozwiązanie: `A | B,C | D`.
Możesz dodawać i usuwać etapy oraz zależności. Selecty zależności pokazują
aktualne etapy; po usunięciu wybranego etapu zależność należy poprawić lub usunąć.
`RESET` czyści formularz oraz wyniki. Zasoby wpisuje się po przecinku.
Błędy danych i brak połączenia z API są wyświetlane na stronie.

Pliki w `src/main/resources/static/`:

- `index.html` — formularz i trzy sekcje prezentacji przepływu.
- `styles.css` — prosty układ, wizualne oddzielenie sekcji i widok mobilny.
- `app.js` — dynamiczny formularz, `buildManagerInput()`, POST do API
  oraz `renderOutput()` wyświetlający ograniczenia i rozwiązania.

JavaScript zbiera dane, rozdziela tekst zasobów po przecinku i wysyła JSON
do `/api/parallelization` z `Content-Type: application/json`. Nie wyprowadza
BEFORE, NOT_WITH ani reguł finansowania. Ograniczenia i wyniki pochodzą z API.
Nazwy etapów w rozwiązaniach są odczytywane z wysłanego requestu.
Zmiana formularza czyści poprzednie wyniki, aby nie pokazywać ich dla innych danych.

Serwer Java udostępnia `/`, `/index.html`, `/styles.css` i `/app.js`
z zasobów wewnątrz JAR. Endpointy `/api/...` i interfejs plikowy pozostają dostępne.

Testy API używają rzeczywistego serwera na wolnym lokalnym porcie oraz klienta
HTTP JDK. Porównują cały raport HTTP z raportem plikowym i sprawdzają błędy.

```powershell
.\mvnw.cmd verify
node src/test/frontend/app.test.cjs
.\mvnw.cmd '-Dtest=ParallelizationHttpServerTest' test
```

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
