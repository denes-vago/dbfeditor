package com.vd.dbfeditor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

final class Localization {
    private static final String RESOURCE_DIR = "com/vd/dbfeditor/i18n";
    private static final String FILE_PREFIX = "messages_";
    private static final String FILE_SUFFIX = ".properties";

    private final Map<String, BundleData> bundles;
    private final List<LanguageOption> availableLanguages;
    private BundleData currentBundle;

    Localization(String preferredLanguageCode) {
        bundles = loadBundles();
        availableLanguages = bundles.entrySet().stream()
            .map(entry -> new LanguageOption(entry.getKey(), entry.getValue().displayName))
            .sorted(Comparator.comparing(LanguageOption::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        String initialCode = bundles.containsKey(preferredLanguageCode) ? preferredLanguageCode : availableLanguages.get(0).code();
        currentBundle = bundles.get(initialCode);
    }

    List<LanguageOption> availableLanguages() {
        return availableLanguages;
    }

    String currentLanguageCode() {
        return currentBundle.code;
    }

    void setLanguage(String languageCode) {
        BundleData selected = bundles.get(languageCode);
        if (selected != null) {
            currentBundle = selected;
        }
    }

    String text(String key, Object... args) {
        String pattern = currentBundle.messages.getProperty(key, '!' + key + '!');
        return args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }

    private static Map<String, BundleData> loadBundles() {
        Map<String, BundleData> result = new LinkedHashMap<>();
        for (String languageCode : discoverLanguageCodes()) {
            BundleData bundle = loadBundle(languageCode);
            if (bundle != null) {
                result.put(languageCode, bundle);
            }
        }

        if (result.isEmpty()) {
            throw new IllegalStateException("Nincs elérhető nyelvi fájl.");
        }
        return result;
    }

    private static List<String> discoverLanguageCodes() {
        List<String> languageCodes = new ArrayList<>();
        try {
            URL resourceUrl = Localization.class.getClassLoader().getResource(RESOURCE_DIR);
            if (resourceUrl != null && "file".equals(resourceUrl.getProtocol())) {
                Path resourcePath = Path.of(resourceUrl.toURI());
                try (var stream = Files.list(resourcePath)) {
                    stream
                        .map(path -> path.getFileName().toString())
                        .filter(name -> name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX))
                        .map(name -> name.substring(FILE_PREFIX.length(), name.length() - FILE_SUFFIX.length()))
                        .sorted()
                        .forEach(languageCodes::add);
                }
            }
        } catch (Exception e) {
            // Fallback below.
        }

        if (languageCodes.isEmpty()) {
            tryAddLanguage(languageCodes, "hu");
            tryAddLanguage(languageCodes, "en");
        }
        return languageCodes;
    }

    private static void tryAddLanguage(List<String> languageCodes, String code) {
        String resourceName = RESOURCE_DIR + "/" + FILE_PREFIX + code + FILE_SUFFIX;
        if (Localization.class.getClassLoader().getResource(resourceName) != null) {
            languageCodes.add(code);
        }
    }

    private static BundleData loadBundle(String languageCode) {
        String resourceName = RESOURCE_DIR + "/" + FILE_PREFIX + languageCode + FILE_SUFFIX;
        try (InputStream stream = Localization.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (stream == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String displayName = properties.getProperty("language.display_name", languageCode.toUpperCase(Locale.ROOT));
            return new BundleData(languageCode, displayName, properties);
        } catch (IOException e) {
            throw new IllegalStateException("Nem sikerült betölteni a nyelvi fájlt: " + resourceName, e);
        }
    }

    record LanguageOption(String code, String displayName) {
    }

    private record BundleData(String code, String displayName, Properties messages) {
    }
}
