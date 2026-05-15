package com.ce316.iae;

/**
 * Application entry point that does NOT extend {@link javafx.application.Application}.
 *
 * <p>When a class that extends {@code Application} is used as the JAR manifest's
 * {@code Main-Class}, the JVM performs a JavaFX module-availability check
 * <em>before</em> {@code main()} is invoked.  On plain classpaths (fat JARs,
 * IDE run configs without {@code --module-path}) this check fails with:
 * <pre>Error: JavaFX runtime components are missing, and are required to run this application</pre>
 *
 * <p>Declaring a <strong>separate</strong> launcher class that does not extend
 * {@code Application} bypasses that early check — the JavaFX toolkit is only
 * initialised later, inside {@link IaeApp#launch(String[])}, at which point
 * the classpath already contains all JavaFX JARs bundled by the shade plugin.
 *
 * <p>Both the {@code maven-shade-plugin} manifest and the IDE run configuration
 * should point to <strong>this</strong> class, not to {@code IaeApp}.
 */
public class Launcher {

    public static void main(String[] args) {
        IaeApp.launch(IaeApp.class, args);
    }
}
