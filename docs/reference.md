# Referenz

## Absätze

Normaler Text wird als Absatz interpretiert. Ein doppelter Zeilenabstand beginnt
einen neuen Absatz.

## Formatierung

* `**fett**` wird zu **fett**
* `_kursiv_` wird zu _kursiv_
* `~~durchgestrichen~~` wird zu ~~durchgestrichen~~
* `[Linktext](https://nextjournal.com/)` wird zu [Linktext](https://nextjournal.com/)

## Überschriften

Überschriften beginnen mit `#`. Mehrere aufeinanderfolgende `#`
definieren das Level der Überschrift.

    # Überschrift 1
    ## Überschrift 2
    ### Überschrift 3
    #### Überschrift 4

## Listen

### Aufzählungen

Normale Aufzählungen beginnen mit einem `*` und können verschachtelt sein.

    * Kühlschrank
        * Butter
        * Eier
        * Milch
    * Vorratsschrank
        * Brot
        * Backpapier
        * Alufolie

wird zu

* Kühlschrank
    * Butter
    * Eier
    * Milch
* Vorratsschrank
    * Brot
    * Backpapier
    * Alufolie

### Nummerierte Aufzählungen

Nummerierte Aufzählungen beginnen mit `1.` und können ebenfalls verschachtelt sein.
Verschachtelte Aufzählungen beginnen wieder mit `1.` (anstelle z.B. `1.1.`) und nehmen automatisch die
übergeordneten Indizes mit.

    1. Grünpflanzen
        1. Charophyten
        2. Chlorophyten
    2. Landpflanzen
        1. Lebermoose
        2. Laubmoose
        3. Hornmoose

wird zu

1. Grünpflanzen
   1. Charophyten
   2. Chlorophyten
2. Landpflanzen
   1. Lebermoose
   2. Laubmoose
   3. Hornmoose

### Todo Listen

Todo Listen beginnen mit `* [ ]` oder `* [x]` wobei das `x` markiert ob
das Todo erledigt ist. Todo Listen können ebenfalls verschachtelt sein. 

    * [ ] Lebensmittel
        * [x] Butter
        * [ ] Eier
        * [ ] Milch
    * [x] Werkstatt
        * [x] Schrauben Torx M6
        * [x] Torx Bitsatz

wird zu

* [ ] Lebensmittel
    * [x] Butter
    * [ ] Eier
    * [ ] Milch
* [x] Werkstatt
    * [x] Schrauben Torx M6
    * [x] Torx Bitsatz

## Tabellen

Doppelpunkte können verwendet werden um den Text in Spalten links,
rechts oder zentriert auszurichten.


    | Spalte 1     | Spalte 2            | Spalte 3 |
    | ------------ |:-------------------:| --------:|
    | Spalte 1 ist | links ausgerichtet  |   1600 € |
    | Spalte 2 ist | zentriert           |     12 € |
    | Spalte 3 ist | rechts ausgerichtet |      1 € |

wird zu

| Spalte 1     | Spalte 2            | Spalte 3 |
| ------------ |:-------------------:| --------:|
| Spalte 1 ist | links ausgerichtet  |   1600 € |
| Spalte 2 ist | zentriert           |     12 € |
| Spalte 3 ist | rechts ausgerichtet |      1 € |

## Bilder

    ![ARS Altmann Waggon](https://www.ars-altmann.de/wp-content/uploads/2017/12/Schiene2.jpg)

wird zu

![ARS Altmann Waggon](https://www.ars-altmann.de/wp-content/uploads/2017/12/Schiene2.jpg)

## Zitate

    > “The purpose of computation is insight, not numbers.”
    >
    > ― Richard Hamming

wird zu

> “The purpose of computation is insight, not numbers.”
>
> ― Richard Hamming

## Trennlinien

Verschiedene Sektionen können durch Trennlinien verdeutlicht werden.
`---` produziert eine Linie über die volle Breite des Dokuments.

    #### Sektion 1

    Hier ist ein Absatz zur Sektion 1.

    ---

    #### Sektion 2

    Hier ist ein Absatz zur Sektion 2.

wird zu

#### Sektion 1

Hier ist ein Absatz zur Sektion 1.

---

#### Sektion 2

Hier ist ein Absatz zur Sektion 2.

## Inhaltsverzeichnis

Ein Inhaltsverzeichnis über alle Überschriften kann an jeder beliebigen
Stelle mit `[[toc]]` eingefügt werden.

    [[toc]]

wird zu

[[toc]]