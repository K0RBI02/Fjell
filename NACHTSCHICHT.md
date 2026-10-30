# Nachtschicht v2.0

Dauerauftrag vom 31.10.2026, 00:00 bis 08:00. v1.0 ist als Git-Tag `v1.0` gesichert
(zurück mit `git checkout v1.0`), gearbeitet wird auf dem Branch `v2.0`.

Regeln: Aufs Handy kommt nur, was baut, alle Tests besteht und nach dem Start nicht
abstürzt. Sonst wird sofort die letzte gute Version zurückgespielt.

## Backlog

- [x] Release-Build statt Debug: deutlich flüssiger (R8 + Compose ohne Debug-Overhead)
- [x] Lens-Option entfernen (Google sperrt Lens für fremde Apps, siehe v1.0-Abend)
- [x] Deinstallierte Apps machen ihren Gitterplatz frei
- [ ] Stapel: Suche bietet am Ende „Im Web suchen: …“ an
- [ ] Stapel: Reihe „Zuletzt benutzt“ oben (merkt sich der Launcher selbst, keine Berechtigung)
- [ ] Apps im Stapel ausblenden (und in den Einstellungen wieder einblenden)
- [ ] Nächster Wecker klein unter Datum/Wetter
- [ ] Popups: sanfte Ausblend-Animation statt hartem Verschwinden
- [ ] Gitter: leichter Vibrations-Tick, wenn beim Ziehen ein neuer Platz angesteuert wird
- [ ] Mehr Unit-Tests (Buchstaben-Gruppierung, Icon-Stil-Speicherformat)
- [ ] Einstellungen: Version anzeigen
- [ ] versionName 2.0, Tag `v2.0`

## Protokoll

**00:10** – Release-Build eingerichtet (3,2 MB statt 30 MB, deutlich flüssiger). Handy hängt
gerade im USB-Modus ohne Debugging, Installation folgt, sobald es wieder erreichbar ist.

**00:15** – Lens-Option entfernt: Google lehnt Lens-Aufrufe aus fremden Apps ab
(„Lens eligibility error“), ein Knopf ohne Funktion wäre nur verwirrend. Die Suchleiste hat
jetzt Lupe, „suchen“ und optional das Mikrofon.

**00:20** – Deinstallierte Apps geben ihren Gitterplatz frei. Es zählt nur eine echte
Deinstallation, nicht ein Update oder eine kurz nicht verfügbare App. Die gleiche App im
Arbeitsprofil bleibt unberührt. 3 neue Tests (13 insgesamt, alle grün).
