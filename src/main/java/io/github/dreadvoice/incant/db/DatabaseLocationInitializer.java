package io.github.dreadvoice.incant.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class DatabaseLocationInitializer implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(DatabaseLocationInitializer.class);
    private static final String URL_PROPERTY = "spring.datasource.url";
    private static final String WORKING_DIRECTORY_PROPERTY = "user.dir";
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";
    private static final String LEGACY_DATABASE = "data/incant.db";
    private static final List<String> SIDECAR_SUFFIXES = List.of("-wal", "-shm", "-journal");

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        Path database = configuredDatabase();
        if (database == null) {
            return;
        }

        createDirectory(database.getParent());
        migrateLegacyDatabase(database);
    }

    private Path configuredDatabase() {
        String url = environment.getProperty(URL_PROPERTY, "");
        if (!url.startsWith(SQLITE_PREFIX)) {
            return null;
        }

        Path database = Path.of(url.substring(SQLITE_PREFIX.length())).toAbsolutePath();
        return database.getParent() == null ? null : database;
    }

    private static void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new IllegalStateException("could not create database directory " + directory + ": " + e.getMessage(),
                    e);
        }
    }

    private void migrateLegacyDatabase(Path database) {
        Path legacy = Path.of(environment.getProperty(WORKING_DIRECTORY_PROPERTY, "")).resolve(LEGACY_DATABASE)
                .toAbsolutePath();
        if (legacy.equals(database) || !Files.isRegularFile(legacy) || Files.exists(database)) {
            return;
        }

        try {
            for (String suffix : SIDECAR_SUFFIXES) {
                Path sidecar = sidecar(legacy, suffix);
                if (Files.isRegularFile(sidecar)) {
                    Files.move(sidecar, sidecar(database, suffix));
                }
            }
            Files.move(legacy, database);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "could not migrate database from " + legacy + " to " + database + ": " + e.getMessage(), e);
        }

        log.info("migrated database from {} to {}", legacy, database);
    }

    private static Path sidecar(Path database, String suffix) {
        return database.resolveSibling(database.getFileName() + suffix);
    }
}
