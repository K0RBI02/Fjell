# Nachtschicht v2.0

Dauerauftrag vom 31.10.2026, 00:00 bis 08:00. v1.0 ist als Git-Tag `v1.0` gesichert
(zurück mit `git checkout v1.0`), gearbeitet wird auf dem Branch `v2.0`.

Regeln: Aufs Handy kommt nur, was baut, alle Tests besteht und nach dem Start nicht
abstürzt. Sonst wird sofort die letzte gute Version zurückgespielt.

## Backlog

- [x] Release-Build statt Debug: deutlich flüssiger (R8 + Compose ohne Debug-Overhead)
- [x] Lens-Option entfernen (Google sperrt Lens für fremde Apps, siehe v1.0-Abend)
- [x] Deinstallierte Apps machen ihren Gitterplatz frei
- [x] Stapel: Suche bietet am Ende „Im Web suchen: …“ an
- [x] Stapel: Reihe „Zuletzt benutzt“ oben (merkt sich der Launcher selbst, keine Berechtigung)
- [x] Apps im Stapel ausblenden (und in den Einstellungen wieder einblenden)
- [x] Nächster Wecker klein unter Datum/Wetter
- [x] Popups: sanfte Ausblend-Animation statt hartem Verschwinden
- [x] Gitter: leichter Vibrations-Tick, wenn beim Ziehen ein neuer Platz angesteuert wird
- [x] Mehr Unit-Tests (Buchstaben-Gruppierung, Icon-Stil-Speicherformat)
- [x] Einstellungen: Version anzeigen
- [x] versionName 2.0, Tag `v2.0`

## Protokoll

**00:10** – Release-Build eingerichtet (3,2 MB statt 30 MB, deutlich flüssiger). Handy hängt
gerade im USB-Modus ohne Debugging, Installation folgt, sobald es wieder erreichbar ist.

**00:15** – Lens-Option entfernt: Google lehnt Lens-Aufrufe aus fremden Apps ab
(„Lens eligibility error“), ein Knopf ohne Funktion wäre nur verwirrend. Die Suchleiste hat
jetzt Lupe, „suchen“ und optional das Mikrofon.

**00:20** – Deinstallierte Apps geben ihren Gitterplatz frei. Es zählt nur eine echte
Deinstallation, nicht ein Update oder eine kurz nicht verfügbare App. Die gleiche App im
Arbeitsprofil bleibt unberührt. 3 neue Tests (13 insgesamt, alle grün).

**00:25** – Stapel-Suche: Unter den Treffern steht jetzt „Im Web suchen: „…““ und übergibt
den Text an die Google-App (ohne Google-App: Browser). Findet sich keine App, startet Enter
direkt die Websuche.

**00:30** – Stapel: Ganz oben steht „zuletzt“ mit den 4 zuletzt gestarteten Apps, die nicht im
Gitter liegen, als Bubble-Reihe. Der Launcher merkt sich das selbst (keine
Nutzungsdaten-Berechtigung nötig). Beim Suchen ist die Reihe ausgeblendet, das Alphabet
springt weiterhin richtig.

**00:35** – Apps ausblenden: Im Stapel eine App gedrückt halten → „Im Stapel ausblenden“
(Symbol: durchgestrichenes Auge). Die Suche findet sie weiterhin, dort steht dann „Wieder im
Stapel zeigen“. In den Einstellungen listet der Abschnitt „Ausgeblendet“ alle versteckten
Apps, ein Tipp blendet sie wieder ein.

**00:40** – Nächster Wecker: Ist innerhalb der nächsten 24 Stunden ein Wecker gestellt, steht
er hinter Datum und Wetter (z. B. „31 Okt. | 4°C | 🔔 7:00“) mit gezeichnetem Glöckchen.
Antippen öffnet die Uhr-App. Keine Berechtigung nötig.

**~00:40 – Abbruch:** Die Claude-Sitzung wurde beendet. Die Wecker für die Nachtschicht
lebten nur in dieser Sitzung und sind mit ihr verschwunden, deshalb ist danach nichts mehr
passiert. Am Morgen (09:59) habe ich die zuletzt halbfertige Wecker-Funktion geprüft: Build und
Tests grün, committet. Das Handy war die ganze Nacht nicht per adb erreichbar, installiert
ist also noch nichts von v2.0.

**10:05–10:20 (gemeinsam am Morgen)** – Erste v2.0 aufs Handy gespielt, Einstellungen haben
das Update überlebt. Danach der Rest:
- Popups (Einstellungen, Icon ändern) blenden sanft aus und schrumpfen nach unten weg, statt
  hart zu verschwinden.
- Gitter: leichter Vibrations-Tick, sobald beim Ziehen ein neuer Platz angesteuert wird.
- 8 neue Tests (Buchstaben-Gruppierung inkl. Umlaute, Speicherformat der Icon-Stile), 21
  insgesamt, alle grün.
- Einstellungen zeigen ganz unten „Fjell 2.0“.
- versionName 2.0 / versionCode 2, aufs Handy gespielt, kein Absturz. Tag `v2.0`.

## Zusammenfassung v2.0

Neu gegenüber v1.0: Release-Build (flüssiger, 3,2 MB), Stapel mit „zuletzt“, Websuche und
Ausblenden, nächster Wecker auf dem Startbildschirm, freie Plätze nach Deinstallation,
sanftere Popups und Vibrations-Tick. Lens wurde entfernt, weil Google es für fremde Apps sperrt.
Die Nachtschicht selbst brach um ~00:40 ab, weil die Claude-Sitzung endete. Der Rest wurde
morgens gemeinsam fertiggestellt.
