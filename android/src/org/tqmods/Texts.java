package org.tqmods;

import java.util.Locale;

/** User-facing text in plain language. Polish on Polish phones, English otherwise. */
final class Texts {
    private Texts() {}
    static final boolean PL = "pl".equals(Locale.getDefault().getLanguage());
    static String t(String en, String pl) { return PL ? pl : en; }

    static String title() { return t("Titan Quest Mods", "Titan Quest – mody"); }
    static String subtitle() { return t("Choose what to play, then tap Play.", "Wybierz, w co chcesz grać, i naciśnij Graj."); }
    static String vanilla() { return t("Original game (no mods)", "Oryginalna gra (bez modów)"); }
    static String vanillaHint() { return t("Your normal characters and saves.", "Twoje zwykłe postacie i zapisy."); }
    static String play(String name) { return t("Play: ", "Graj: ") + name; }
    static String returnToGame() { return t("Return to the game", "Wróć do gry"); }
    static String restartWith(String name) { return t("Restart the game with: ", "Uruchom grę ponownie z: ") + name; }
    static String chooseHeader() { return t("What do you want to play?", "W co chcesz grać?"); }
    static String addMod() { return t("+  Add a mod from a file", "+  Dodaj mod z pliku"); }
    static String addHint() { return t("Download a mod (a ZIP or 7z file) on this phone, then tap the button above and pick it. Downloaded files are usually in the Downloads folder.",
        "Pobierz mod (plik ZIP lub 7z) na ten telefon, potem naciśnij przycisk powyżej i wybierz go. Pobrane pliki są zwykle w folderze „Pobrane”."); }
    static String howItWorks() { return t("How it works", "Jak to działa"); }
    static String steps() { return t("1. Add a mod from a file.\n2. Tap it to choose it.\n3. Tap Play.\n\nEach mod has its own copy of your characters, so your normal saves stay safe.",
        "1. Dodaj mod z pliku.\n2. Stuknij go, żeby go wybrać.\n3. Naciśnij Graj.\n\nKażdy mod ma własną kopię Twoich postaci, więc zwykłe zapisy są bezpieczne."); }
    static String badgeExperimental() { return t("Experimental", "Eksperymentalny"); }
    static String badgeTested() { return t("Tested", "Sprawdzony"); }
    static String badgeMap() { return t("Custom map – not supported yet", "Własna mapa – jeszcze nieobsługiwana"); }
    static String hintContent() { return t("New items, skills or balance. Runs in the normal game world.", "Nowe przedmioty, umiejętności lub balans. Działa w zwykłym świecie gry."); }
    static String hintMap() { return t("This mod has its own world. Custom worlds cannot be played yet.", "Ten mod ma własny świat. Własnych światów nie da się jeszcze grać."); }
    static String hintMapSupported() { return t("Custom map with its own characters.", "Własna mapa z własnymi postaciami."); }
    static String selected() { return t("✓ Chosen", "✓ Wybrany"); }
    static String remove() { return t("Remove", "Usuń"); }
    static String removeTitle(String name) { return t("Remove \"", "Usunąć „") + name + t("\"?", "”?"); }
    static String removeBody() { return t("The mod file is deleted from the phone. Characters you played with this mod are kept and come back if you add the same mod again.",
        "Plik moda zostanie usunięty z telefonu. Postacie grane z tym modem zostają zachowane i wrócą, jeśli dodasz ten sam mod ponownie."); }
    static String cancel() { return t("Cancel", "Anuluj"); }
    static String ok() { return t("OK", "OK"); }
    static String more() { return t("Settings and backups", "Ustawienia i kopie zapasowe"); }
    static String perf() { return t("Faster graphics (Perf)", "Szybsza grafika (Perf)"); }
    static String perfHint() { return t("Engine optimizations and an in-game graphics menu. Takes effect the next time the game starts. Known issue: tiny labels in the pause menu.",
        "Optymalizacje silnika i menu grafiki w grze. Działa od następnego uruchomienia gry. Znany problem: bardzo małe napisy w menu pauzy."); }
    static String backup() { return t("Save a backup of my characters", "Zapisz kopię zapasową postaci"); }
    static String restore() { return t("Restore characters from a backup", "Przywróć postacie z kopii"); }
    static String shortcut() { return t("Put this app on the Home screen", "Dodaj tę aplikację na ekran główny"); }
    static String closeGame() { return t("Close the game", "Zamknij grę"); }
    static String saveFirstTitle() { return t("Have you saved your character?", "Czy zapisałeś postać?"); }
    static String saveFirstBody() { return t("The game will close. In the game open the menu and choose Save first, otherwise unsaved progress is lost.",
        "Gra zostanie zamknięta. Najpierw w grze otwórz menu i wybierz Zapisz – inaczej niezapisany postęp przepadnie."); }
    static String restartBody(String name) { return t("To switch to \"" + name + "\" the game has to restart. In the game open the menu and choose Save first, otherwise unsaved progress is lost.",
        "Żeby przełączyć na „" + name + "”, gra musi uruchomić się ponownie. Najpierw w grze otwórz menu i wybierz Zapisz – inaczej niezapisany postęp przepadnie."); }
    static String savedRestart() { return t("Saved – restart", "Zapisane – uruchom ponownie"); }
    static String savedClose() { return t("Saved – close", "Zapisane – zamknij"); }
    static String working() { return t("Please wait…", "Proszę czekać…"); }
    static String importing() { return t("Adding the mod…", "Dodaję mod…"); }
    static String imported(String name) { return t("Added: ", "Dodano: ") + name + t(". Tap it to choose it.", ". Stuknij go, żeby go wybrać."); }
    static String importedChosen(String name) { return t("Added and chosen: ", "Dodano i wybrano: ") + name + t(". Tap Play.", ". Naciśnij Graj."); }
    static String alreadyAdded(String name) { return t("This mod is already in the list: ", "Ten mod już jest na liście: ") + name
        + t(". It is chosen; your characters for it are kept.", ". Został wybrany; Twoje postacie dla niego zostają."); }
    static String archiveRar() { return t("This is a RAR archive. The app reads ZIP and 7z files: on a computer, unpack it and pack the folder again as ZIP.",
        "To archiwum RAR. Aplikacja czyta pliki ZIP i 7z: na komputerze rozpakuj je i spakuj folder ponownie jako ZIP."); }
    static String unpacking7z() { return t("Unpacking the 7z file. Large mods take up to a minute…", "Rozpakowuję plik 7z. Duże mody trwają do minuty…"); }
    static String unsupported7z(String what) { return t("This 7z file cannot be read here (" + what + "). On a computer, unpack it and pack the folder again as ZIP.",
        "Tego pliku 7z nie da się tu odczytać (" + what + "). Na komputerze rozpakuj go i spakuj folder ponownie jako ZIP."); }
    static String importFailed(String why) { return t("Could not add this file: ", "Nie udało się dodać pliku: ") + why; }
    static String chooseVariant() { return t("This file has several versions. Which one?", "Ten plik zawiera kilka wersji. Którą dodać?"); }
    static String preparingSaves() { return t("Preparing a separate copy of your characters for this mod. This happens only once…",
        "Przygotowuję osobną kopię Twoich postaci dla tego moda. To dzieje się tylko raz…"); }
    static String starting() { return t("Starting the game…", "Uruchamiam grę…"); }
    static String startFailed(String why) { return t("The game could not start: ", "Nie udało się uruchomić gry: ") + why; }
    static String stateRunningVanilla() { return t("The game is running without mods.", "Gra działa bez modów."); }
    static String stateRunning(String name) { return t("The game is running with: ", "Gra działa z modem: ") + name; }
    static String stateWaiting(String name) { return t("\"" + name + "\" will load when the game shows the character list.",
        "„" + name + "” załaduje się, gdy gra pokaże listę postaci."); }
    static String stateFailed(String why) { return t("The mod did not load: ", "Mod się nie załadował: ") + why; }
    static String lastFailed(String why) { return t("Last time the game was stopped: ", "Ostatnio gra została zatrzymana: ") + why
        + t(" Your saves were not changed.", " Twoje zapisy nie zostały zmienione."); }
    static String backupDone(int count) { return t("Backup saved (" + count + " files). Keep a copy on a computer too.", "Kopia zapisana (" + count + " plików). Trzymaj też kopię na komputerze."); }
    static String backupFailed(String why) { return t("Backup failed: ", "Nie udało się zapisać kopii: ") + why; }
    static String restoreTitle() { return t("Replace all characters with the backup?", "Zastąpić wszystkie postacie kopią?"); }
    static String restoreBody() { return t("Choose a backup ZIP made by this app. Your current characters are kept in a separate folder on the phone.",
        "Wybierz plik ZIP z kopią zrobioną przez tę aplikację. Obecne postacie zostaną zachowane w osobnym folderze na telefonie."); }
    static String chooseBackup() { return t("Choose backup", "Wybierz kopię"); }
    static String restoreDone() { return t("Characters restored from the backup.", "Postacie przywrócone z kopii."); }
    static String restoreFailed(String why) { return t("Restore failed: ", "Nie udało się przywrócić: ") + why; }
    static String closeGameFirst() { return t("Close the game first (Settings → Close the game), then try again.", "Najpierw zamknij grę (Ustawienia → Zamknij grę), potem spróbuj ponownie."); }
    static String removed() { return t("Mod removed.", "Mod usunięty."); }
    static String unsupportedMap() { return t("Custom maps cannot be played yet. Choose another mod.", "Własnych map nie da się jeszcze grać. Wybierz inny mod."); }
    static String rootIntro() { return t("To start the game with mods, this app needs root access from Magisk. Tap the button below, then tap Grant in the Magisk window before its countdown runs out.",
        "Żeby uruchamiać grę z modami, aplikacja potrzebuje dostępu roota z Magiska. Naciśnij przycisk poniżej, a potem naciśnij Zezwól w oknie Magiska, zanim skończy się odliczanie."); }
    static String allowRoot() { return t("Allow root access", "Zezwól na dostęp roota"); }
    static String noRoot() { return t("Root access was refused. Open Magisk → Superuser, switch on Titan Quest Mods, then tap the button again.",
        "Dostęp roota został odrzucony. Otwórz Magisk → Superuser, włącz Titan Quest Mods i naciśnij przycisk ponownie."); }
    static String noModule() { return t("The Titan Quest Mods loader module is not installed. Install the tq-mods-magisk ZIP in Magisk (Modules → Install from storage) and reboot the phone.",
        "Nie zainstalowano modułu loadera. Zainstaluj plik ZIP tq-mods-magisk w Magisku (zakładka Moduły) i uruchom telefon ponownie."); }
    static String noGame() { return t("Titan Quest is not installed on this phone.", "Titan Quest nie jest zainstalowany na tym telefonie."); }
    static String blocked(String why) { return t("Mods are paused: ", "Mody są wstrzymane: ") + why + "."; }
    static String perfMissing() { return t("Needs the TQ Android Perf Magisk module (2.4 or newer).", "Wymaga modułu Magisk TQ Android Perf (2.4 lub nowszy)."); }
    static String details() { return t("Details: ", "Szczegóły: "); }

    /** Turns loader errors into one plain sentence. */
    static String explain(String error) {
        String e = error == null ? "" : error;
        if (e.contains("not ready")) return t("the character was opened before the mod finished loading. Start again and wait a moment on the character list.",
            "postać została otwarta, zanim mod skończył się ładować. Uruchom ponownie i poczekaj chwilę na liście postaci.");
        if (e.contains("API") || e.contains("Engine instance")) return t("this game version is not supported by the mod loader.", "ta wersja gry nie jest obsługiwana przez loader modów.");
        if (e.contains("Custom maps")) return t("custom maps are not supported yet.", "własne mapy nie są jeszcze obsługiwane.");
        if (e.contains("records not found") || e.contains("ARZ") || e.contains("resource") || e.contains("Mod text"))
            return t("this mod could not be loaded on Android.", "tego moda nie udało się załadować na Androidzie.");
        if (e.contains("save copy") || e.contains("profile")) return t("the separate character copy for this mod is missing.", "brakuje osobnej kopii postaci dla tego moda.");
        if (e.contains("before the loader was ready")) return t("the character list was opened before the mod loader was ready. Start again and wait a few seconds in the main menu.",
            "lista postaci została otwarta, zanim loader był gotowy. Uruchom ponownie i poczekaj kilka sekund w menu głównym.");
        if (e.contains("game is running")) return t("the game is still running. Close it first.", "gra nadal działa. Najpierw ją zamknij.");
        if (e.contains("timed out")) return t("the game did not reach the character list in time.", "gra nie doszła do listy postaci na czas.");
        return t("an unexpected error.", "nieoczekiwany błąd.");
    }
}
