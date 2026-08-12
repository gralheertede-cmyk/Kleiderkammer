# VereinsKleiderverwaltung

Eine einfache Android-App für die Ausgabe, Rückgabe und Bestandsverwaltung von Vereinskleidung.

## Was enthalten ist

- T-Shirt
- Trikot mit optionaler Nummer
- Trikothose
- Trainingsjacke
- Trainingshose
- Mitgliederverwaltung
- Ausgabe und Rückgabe
- „Wer hat was?“
- Bestandsübersicht
- Inventur / Bestand ergänzen
- zentrale Daten über Google Sheets
- mehrere Smartphones
- gemeinsames Vereins-Passwort
- Transaktionshistorie im Tabellenblatt `Transactions`

## Google-Teil einrichten

1. Im Google-Konto des Vereins eine neue Google-Tabelle erstellen.
2. `Erweiterungen -> Apps Script` öffnen.
3. Inhalt von `google-apps-script/Code.gs` einfügen und speichern.
4. In Apps Script unter `Projekteinstellungen -> Skripteigenschaften` eine Eigenschaft anlegen:
   - Name: `APP_PASSWORD`
   - Wert: ein langes eigenes Vereins-Passwort
5. `Bereitstellen -> Neue Bereitstellung -> Web-App`
6. „Ausführen als“: **Ich**
7. Zugriff: **Jeder mit dem Link**
8. Bereitstellen und die Web-App-URL kopieren.

Wichtig: Das Google-Konto-Passwort wird nicht in der App gespeichert. Die App kennt nur das separate Vereins-Passwort für das Apps-Script.

## Android-App

Die Android-App ist ein normales Gradle-Projekt.

Für eine APK ohne Android Studio ist im Projekt bereits ein GitHub-Actions-Workflow vorgesehen. Lade das Projekt auf GitHub hoch und starte den Workflow `Build APK`. Danach liegt die fertige Debug-APK als Workflow-Artefakt zum Download bereit.

## Hinweis zur ersten Version

Die App ist absichtlich einfach gehalten. Bei nummerierten Artikeln wird jedes einzelne Stück als eigener Datensatz geführt. Bei nicht nummerierten Artikeln wird ebenfalls jedes einzelne Stück geführt. Dadurch kann die App zuverlässig feststellen, was verfügbar ist und was ausgeliehen wurde.

Für eine spätere Version wären z. B. Fotos, QR-Codes/Barcodes, Rollen für Betreuer und Excel-/CSV-Export möglich.
